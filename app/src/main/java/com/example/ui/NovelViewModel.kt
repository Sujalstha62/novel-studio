package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiGrammarChecker
import com.example.api.GrammarSuggestion
import com.example.api.GeminiStoryTracker
import com.example.api.ExtractedStoryData
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

sealed class ActiveTab {
    object Manuscript : ActiveTab()
    object Characters : ActiveTab()
    object StorylineTracker : ActiveTab()
    object RelationshipMap : ActiveTab()
    object Exporter : ActiveTab()
    object Settings : ActiveTab()
}

class NovelViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "NovelViewModel"
    private val repository: NovelRepository
    private val grammarChecker = GeminiGrammarChecker()
    private val storyTracker = GeminiStoryTracker()

    // --- State flows ---
    val allNodes: StateFlow<List<ManuscriptNode>>
    val rootNovels: StateFlow<List<ManuscriptNode>>
    val allCharacters: StateFlow<List<CharacterProfile>>
    val settings: StateFlow<WritingSettings>
    val allEvents: StateFlow<List<StoryEvent>>
    val allRelationships: StateFlow<List<CharacterRelationship>>
    val allCommits: StateFlow<List<ManuscriptCommit>>

    private val _selectedNovelId = MutableStateFlow<Int?>(null)
    val selectedNovelId: StateFlow<Int?> = _selectedNovelId.asStateFlow()

    val filteredCharacters: StateFlow<List<CharacterProfile>>
    val filteredEvents: StateFlow<List<StoryEvent>>
    val filteredRelationships: StateFlow<List<CharacterRelationship>>

    private val _editingEvent = MutableStateFlow<StoryEvent?>(null)
    val editingEvent: StateFlow<StoryEvent?> = _editingEvent.asStateFlow()

    private val _selectedNodeId = MutableStateFlow<Int?>(null)
    val selectedNodeId: StateFlow<Int?> = _selectedNodeId.asStateFlow()

    private val _activeNode = MutableStateFlow<ManuscriptNode?>(null)
    val activeNode: StateFlow<ManuscriptNode?> = _activeNode.asStateFlow()

    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val _activeTab = MutableStateFlow<ActiveTab>(ActiveTab.Manuscript)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    // --- Grammar checking state ---
    private val _isCheckingGrammar = MutableStateFlow(false)
    val isCheckingGrammar: StateFlow<Boolean> = _isCheckingGrammar.asStateFlow()

    private val _grammarSuggestions = MutableStateFlow<List<GrammarSuggestion>>(emptyList())
    val grammarSuggestions: StateFlow<List<GrammarSuggestion>> = _grammarSuggestions.asStateFlow()

    // --- Honest Local Storage & Save State (No fake cloud sync) ---
    private val _lastSavedTimeText = MutableStateFlow("Saved locally")
    val lastSavedTimeText: StateFlow<String> = _lastSavedTimeText.asStateFlow()

    private val _isSavingLocally = MutableStateFlow(false)
    val isSavingLocally: StateFlow<Boolean> = _isSavingLocally.asStateFlow()

    // --- Active Character profile details ---
    private val _editingCharacter = MutableStateFlow<CharacterProfile?>(null)
    val editingCharacter: StateFlow<CharacterProfile?> = _editingCharacter.asStateFlow()

    // --- Reviewable Proposed Changes for AI Story Analysis ---
    private val _isAnalyzingStory = MutableStateFlow(false)
    val isAnalyzingStory: StateFlow<Boolean> = _isAnalyzingStory.asStateFlow()

    private val _proposedChanges = MutableStateFlow<List<ProposedChange>>(emptyList())
    val proposedChanges: StateFlow<List<ProposedChange>> = _proposedChanges.asStateFlow()

    private val _showReviewDialog = MutableStateFlow(false)
    val showReviewDialog: StateFlow<Boolean> = _showReviewDialog.asStateFlow()

    private val _analysisStatusMessage = MutableStateFlow<String?>(null)
    val analysisStatusMessage: StateFlow<String?> = _analysisStatusMessage.asStateFlow()

    private var currentlyAnalyzingNodeId: Int? = null
    private var currentlyAnalyzingHash: String? = null

    private var autoSaveJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NovelRepository(
            database,
            database.manuscriptDao(),
            database.characterDao(),
            database.settingsDao(),
            database.storyEventDao(),
            database.characterRelationshipDao(),
            database.manuscriptCommitDao()
        )

        allNodes = repository.allNodes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Only root-level folders (parentId == null && isFolder == true) represent novels
        rootNovels = allNodes.map { nodes ->
            nodes.filter { it.parentId == null && it.isFolder }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allCharacters = repository.allCharacters.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        settings = repository.settings
            .map { it ?: WritingSettings() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = WritingSettings()
            )

        allEvents = repository.allEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allRelationships = repository.allRelationships.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allCommits = repository.allCommits.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredCharacters = combine(allCharacters, _selectedNovelId) { chars, novelId ->
            if (novelId == null) emptyList()
            else chars.filter { it.novelId == novelId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredEvents = combine(allEvents, _selectedNovelId) { events, novelId ->
            if (novelId == null) emptyList()
            else events.filter { it.novelId == novelId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredRelationships = combine(allRelationships, _selectedNovelId) { relationships, novelId ->
            if (novelId == null) emptyList()
            else relationships.filter { it.novelId == novelId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Seed data and guarantee novel associations
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        // Auto select first novel if selectedNovelId is null or missing
        viewModelScope.launch {
            rootNovels.collect { novels ->
                if (novels.isNotEmpty()) {
                    if (_selectedNovelId.value == null || novels.none { it.id == _selectedNovelId.value }) {
                        _selectedNovelId.value = novels.first().id
                    }
                } else {
                    _selectedNovelId.value = null
                }
            }
        }

        // Auto-update selectedNovelId if activeNode belongs to a specific root novel folder
        viewModelScope.launch {
            activeNode.collect { node ->
                if (node != null) {
                    val novelId = findNovelFolderIdForNode(node.id)
                    if (novelId != null && novelId != _selectedNovelId.value) {
                        _selectedNovelId.value = novelId
                    }
                }
            }
        }
    }

    // --- Tab Selection ---
    fun selectTab(tab: ActiveTab) {
        _activeTab.value = tab
    }

    // --- Novel Selector & Novel Creation ---
    fun selectNovel(novelId: Int?) {
        _selectedNovelId.value = novelId
        // If current active node doesn't belong to new novel, deselect editor node
        val active = _activeNode.value
        if (active != null && novelId != null) {
            val nodeNovelId = findNovelFolderIdForNode(active.id)
            if (nodeNovelId != null && nodeNovelId != novelId) {
                selectNode(null)
            }
        }
    }

    fun createNovel(name: String) {
        viewModelScope.launch {
            val newNovelId = repository.insertNode(
                ManuscriptNode(
                    name = name.ifBlank { "Untitled Novel" },
                    parentId = null,
                    isFolder = true
                )
            ).toInt()
            _selectedNovelId.value = newNovelId
            _lastSavedTimeText.value = "New novel created locally"
        }
    }

    fun findNovelFolderIdForNode(nodeId: Int): Int? {
        val nodes = allNodes.value
        var current = nodes.find { it.id == nodeId }
        while (current != null) {
            if (current.parentId == null) {
                return if (current.isFolder) current.id else null
            }
            current = nodes.find { it.id == current.parentId }
        }
        return null
    }

    // --- Node operations ---
    fun selectNode(nodeId: Int?) {
        _selectedNodeId.value = nodeId
        _grammarSuggestions.value = emptyList()
        if (nodeId == null) {
            _activeNode.value = null
            _editorText.value = ""
            _wordCount.value = 0
            autoSaveJob?.cancel()
        } else {
            autoSaveJob?.cancel()
            viewModelScope.launch {
                val node = repository.getNodeById(nodeId)
                if (node != null && !node.isFolder) {
                    _activeNode.value = node
                    _editorText.value = node.content
                    _wordCount.value = countWords(node.content)
                    setupAutoSave(node)
                }
            }
        }
    }

    fun updateEditorText(newText: String) {
        _editorText.value = newText
        _wordCount.value = countWords(newText)
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }

    /**
     * Local Auto-Save: Debounces user typing and writes to local database.
     * Note: This does NOT trigger Gemini story analysis automatically.
     */
    private fun setupAutoSave(node: ManuscriptNode) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            _editorText
                .debounce(1000)
                .collect { content ->
                    val currentActive = _activeNode.value
                    if (currentActive != null && currentActive.id == node.id && currentActive.content != content) {
                        _isSavingLocally.value = true
                        val updatedNode = currentActive.copy(
                            content = content,
                            wordCount = countWords(content),
                            lastUpdated = System.currentTimeMillis()
                        )
                        repository.updateNode(updatedNode)
                        _activeNode.value = updatedNode
                        _isSavingLocally.value = false
                        _lastSavedTimeText.value = "Saved locally just now"

                        // If user explicitly turned ON automatic analysis in settings, run throttled
                        if (settings.value.isAutoAnalysisEnabled) {
                            val currentHash = content.hashCode().toString()
                            if (content.trim().length > 30 && currentHash != node.lastAnalyzedHash) {
                                delay(30000) // Heavy 30s throttle
                                if (!_isAnalyzingStory.value && _activeNode.value?.id == node.id) {
                                    runAIStoryAnalysis(node.id, isManual = false)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun createFolder(name: String, parentId: Int?) {
        viewModelScope.launch {
            // Default to selectedNovelId if parentId is null so folders belong to the active novel
            val targetParent = parentId ?: _selectedNovelId.value
            repository.insertNode(
                ManuscriptNode(
                    name = name,
                    parentId = targetParent,
                    isFolder = true
                )
            )
            _lastSavedTimeText.value = "Folder created locally"
        }
    }

    fun createFile(name: String, parentId: Int?) {
        viewModelScope.launch {
            val targetParent = parentId ?: _selectedNovelId.value
            val newId = repository.insertNode(
                ManuscriptNode(
                    name = name,
                    parentId = targetParent,
                    isFolder = false,
                    content = ""
                )
            ).toInt()
            selectNode(newId)
            _lastSavedTimeText.value = "Draft created locally"
        }
    }

    /**
     * Deletes a node recursively. If a folder, all its children and deeper descendants
     * are deleted in a single Room transaction to eliminate orphan records.
     * If deleting an entire novel, all novel-specific records are safely cleaned up.
     */
    fun deleteNode(node: ManuscriptNode) {
        viewModelScope.launch {
            if (selectedNodeId.value == node.id) {
                selectNode(null)
            }
            repository.deleteNodeRecursively(node)

            // If deleted novel was active, pick another root novel
            if (node.parentId == null && node.isFolder && _selectedNovelId.value == node.id) {
                val remainingNovels = rootNovels.value.filter { it.id != node.id }
                _selectedNovelId.value = remainingNovels.firstOrNull()?.id
            }
            _lastSavedTimeText.value = "Deleted locally"
        }
    }

    fun renameNode(nodeId: Int, newName: String) {
        viewModelScope.launch {
            val node = repository.getNodeById(nodeId)
            if (node != null) {
                repository.updateNode(node.copy(name = newName, lastUpdated = System.currentTimeMillis()))
                _lastSavedTimeText.value = "Renamed locally"
            }
        }
    }

    // --- Grammar Checker ---
    fun runGrammarCheck() {
        val currentText = _editorText.value
        if (currentText.isBlank()) return

        _isCheckingGrammar.value = true
        _grammarSuggestions.value = emptyList()

        viewModelScope.launch {
            try {
                val suggestions = grammarChecker.checkGrammar(currentText)
                _grammarSuggestions.value = suggestions
            } catch (e: Exception) {
                Log.e(TAG, "Grammar check failed: ${e.message}")
            } finally {
                _isCheckingGrammar.value = false
            }
        }
    }

    fun applyGrammarSuggestion(suggestion: GrammarSuggestion) {
        val currentText = _editorText.value
        val original = suggestion.originalText
        val replacement = suggestion.suggestedText

        if (original.isBlank()) return

        val updatedText = currentText.replaceFirst(original, replacement)
        updateEditorText(updatedText)
        _grammarSuggestions.value = _grammarSuggestions.value.filter { it != suggestion }

        viewModelScope.launch {
            val currentActive = _activeNode.value
            if (currentActive != null) {
                val updatedNode = currentActive.copy(
                    content = updatedText,
                    wordCount = countWords(updatedText),
                    lastUpdated = System.currentTimeMillis()
                )
                repository.updateNode(updatedNode)
                _activeNode.value = updatedNode
                _lastSavedTimeText.value = "Suggestion applied & saved"
            }
        }
    }

    // --- Character operations ---
    fun startEditingCharacter(character: CharacterProfile?) {
        _editingCharacter.value = character
    }

    fun saveCharacter(
        id: Int,
        name: String,
        role: String,
        age: String,
        appearance: String,
        backstory: String,
        plotArc: String,
        notes: String,
        avatarColor: Int,
        novelId: Int? = _selectedNovelId.value
    ) {
        viewModelScope.launch {
            val assignedNovelId = novelId ?: _selectedNovelId.value ?: 1
            val char = CharacterProfile(
                id = if (id == 0) 0 else id,
                name = name,
                role = role,
                age = age,
                appearance = appearance,
                backstory = backstory,
                plotArc = plotArc,
                notes = notes,
                avatarColor = avatarColor,
                novelId = assignedNovelId
            )
            repository.insertCharacter(char)
            _editingCharacter.value = null
            _lastSavedTimeText.value = "Character profile saved"
        }
    }

    fun deleteCharacter(character: CharacterProfile) {
        viewModelScope.launch {
            repository.deleteCharacter(character)
            if (_editingCharacter.value?.id == character.id) {
                _editingCharacter.value = null
            }
            _lastSavedTimeText.value = "Character deleted"
        }
    }

    // --- Settings operations ---
    private val sharedPrefs = getApplication<Application>().getSharedPreferences("novel_ui_settings", Context.MODE_PRIVATE)
    private val _selectedFont = MutableStateFlow(sharedPrefs.getString("selected_font", "Classic Serif") ?: "Classic Serif")
    val selectedFont: StateFlow<String> = _selectedFont.asStateFlow()

    fun updateSelectedFont(font: String) {
        _selectedFont.value = font
        sharedPrefs.edit().putString("selected_font", font).apply()
    }

    fun updateTexture(texture: String) {
        viewModelScope.launch {
            val currentSettings = settings.value
            repository.insertOrUpdateSettings(currentSettings.copy(selectedTexture = texture))
        }
    }

    fun updateFontSize(size: Int) {
        viewModelScope.launch {
            val currentSettings = settings.value
            repository.insertOrUpdateSettings(currentSettings.copy(fontSize = size))
        }
    }

    fun setDistractionFree(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value
            repository.insertOrUpdateSettings(currentSettings.copy(isDistractionFree = enabled))
        }
    }

    fun setAutoAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value
            repository.insertOrUpdateSettings(currentSettings.copy(isAutoAnalysisEnabled = enabled))
        }
    }

    // --- Safe File Export & Android Sharing System ---
    fun exportManuscript(context: Context, format: String, nodeId: Int?, novelId: Int? = _selectedNovelId.value): Uri? {
        val nodesList = allNodes.value
        val builder = java.lang.StringBuilder()
        val targetNovel = rootNovels.value.find { it.id == novelId }
        val novelTitle = targetNovel?.name ?: "Novel Manuscript"

        if (nodeId != null) {
            // Export single file / chapter
            val node = nodesList.find { it.id == nodeId } ?: return null
            when (format) {
                "Standard Manuscript (TXT)" -> {
                    builder.append("${node.name.uppercase()}\n\n")
                    builder.append("--------------------------------------------------\n\n")
                    builder.append(node.content.replace("\n", "\n\n"))
                }
                "Creative Markdown (MD)" -> {
                    builder.append("# ${node.name}\n\n")
                    builder.append(node.content)
                }
                "Professional HTML" -> {
                    builder.append("<!DOCTYPE html>\n<html>\n<head>\n")
                    builder.append("<meta charset=\"utf-8\">\n")
                    builder.append("<title>${node.name}</title>\n")
                    builder.append("<style>\n")
                    builder.append("body { font-family: 'Garamond', 'Georgia', serif; line-height: 1.8; margin: 2in 1.5in; font-size: 12pt; color: #111; }\n")
                    builder.append("h1 { text-align: center; text-transform: uppercase; margin-bottom: 2em; }\n")
                    builder.append("p { text-indent: 0.5in; margin-bottom: 0; margin-top: 0; text-align: justify; }\n")
                    builder.append("</style>\n</head>\n<body>\n")
                    builder.append("<h1>${node.name}</h1>\n")
                    node.content.split("\n\n").forEach { paragraph ->
                        if (paragraph.isNotBlank()) {
                            builder.append("<p>${paragraph.trim()}</p>\n")
                        }
                    }
                    builder.append("</body>\n</html>")
                }
            }
        } else {
            // Export entire Book belonging to target novel
            when (format) {
                "Creative Markdown (MD)" -> {
                    builder.append("# ${novelTitle.uppercase()}\n\n")
                }
                "Professional HTML" -> {
                    builder.append("<!DOCTYPE html>\n<html>\n<head>\n")
                    builder.append("<meta charset=\"utf-8\">\n")
                    builder.append("<title>$novelTitle</title>\n")
                    builder.append("<style>\n")
                    builder.append("body { font-family: 'Garamond', 'Georgia', serif; line-height: 1.8; margin: 2in 1.5in; font-size: 12pt; color: #111; }\n")
                    builder.append("h1.title { text-align: center; text-transform: uppercase; margin-top: 3in; margin-bottom: 3in; font-size: 28pt; }\n")
                    builder.append("h2 { text-align: center; margin-top: 2em; margin-bottom: 1em; page-break-before: always; }\n")
                    builder.append("p { text-indent: 0.5in; margin-bottom: 0; margin-top: 0; text-align: justify; }\n")
                    builder.append("</style>\n</head>\n<body>\n")
                    builder.append("<h1 class=\"title\">$novelTitle</h1>\n")
                }
                else -> {
                    builder.append("==================================================\n")
                    builder.append("               ${novelTitle.uppercase()}\n")
                    builder.append("==================================================\n\n\n")
                }
            }

            // Export nodes belonging to this novel
            val topNodesForNovel = if (targetNovel != null) {
                nodesList.filter { it.parentId == targetNovel.id }
            } else {
                nodesList.filter { it.parentId == null }
            }

            topNodesForNovel.forEach { node ->
                appendNodeToExport(node, nodesList, format, builder, 0)
            }

            if (format == "Professional HTML") {
                builder.append("</body>\n</html>")
            }
        }

        val extension = when (format) {
            "Standard Manuscript (TXT)" -> "txt"
            "Creative Markdown (MD)" -> "md"
            "Professional HTML" -> "html"
            else -> "txt"
        }

        val sanitizedTitle = novelTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "${sanitizedTitle}_${System.currentTimeMillis()}.$extension"

        return try {
            // Dedicated export directory in cacheDir configured in provider paths
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(builder.toString())
                writer.flush()
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val mimeType = when (extension) {
                "txt" -> "text/plain"
                "md" -> "text/markdown"
                "html" -> "text/html"
                else -> "text/plain"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$novelTitle - Manuscript Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Manuscript via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Export and share failed: ${e.message}", e)
            Toast.makeText(context, "Export failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun appendNodeToExport(
        node: ManuscriptNode,
        all: List<ManuscriptNode>,
        format: String,
        builder: java.lang.StringBuilder,
        depth: Int
    ) {
        if (node.isFolder) {
            if (format == "Creative Markdown (MD)") {
                builder.append("\n" + "#".repeat(depth + 1) + " ${node.name}\n\n")
            } else if (format == "Professional HTML") {
                builder.append("<h${depth + 1} style=\"text-align: center;\">${node.name}</h${depth + 1}>\n")
            } else {
                builder.append("\n=== ${node.name.uppercase()} ===\n\n")
            }

            val children = all.filter { it.parentId == node.id }
            children.forEach { child ->
                appendNodeToExport(child, all, format, builder, depth + 1)
            }
        } else {
            when (format) {
                "Standard Manuscript (TXT)" -> {
                    builder.append("${node.name.uppercase()}\n\n")
                    builder.append(node.content.replace("\n", "\n\n"))
                    builder.append("\n\n--------------------------------------------------\n\n")
                }
                "Creative Markdown (MD)" -> {
                    builder.append("## ${node.name}\n\n")
                    builder.append(node.content)
                    builder.append("\n\n")
                }
                "Professional HTML" -> {
                    builder.append("<h2>${node.name}</h2>\n")
                    node.content.split("\n\n").forEach { paragraph ->
                        if (paragraph.isNotBlank()) {
                            builder.append("<p>${paragraph.trim()}</p>\n")
                        }
                    }
                    builder.append("\n")
                }
            }
        }
    }

    // --- Version Backups & Commits ---
    fun commitDraft(nodeId: Int, commitMessage: String) {
        val node = allNodes.value.find { it.id == nodeId } ?: return
        viewModelScope.launch {
            val randomHash = java.util.UUID.randomUUID().toString().take(7)
            val commit = ManuscriptCommit(
                nodeId = nodeId,
                commitHash = randomHash,
                commitMessage = commitMessage,
                contentSnapshot = node.content,
                timestamp = System.currentTimeMillis()
            )
            repository.insertCommit(commit)
            _lastSavedTimeText.value = "Draft snapshot committed locally"
        }
    }

    fun restoreToCommit(commit: ManuscriptCommit) {
        viewModelScope.launch {
            val node = repository.getNodeById(commit.nodeId)
            if (node != null) {
                val updatedNode = node.copy(
                    content = commit.contentSnapshot,
                    wordCount = countWords(commit.contentSnapshot),
                    lastUpdated = System.currentTimeMillis()
                )
                repository.updateNode(updatedNode)
                if (selectedNodeId.value == commit.nodeId) {
                    _activeNode.value = updatedNode
                    _editorText.value = commit.contentSnapshot
                    _wordCount.value = countWords(commit.contentSnapshot)
                }
                _lastSavedTimeText.value = "Restored draft snapshot"
            }
        }
    }

    fun deleteCommit(commitId: Int) {
        viewModelScope.launch {
            repository.deleteCommitById(commitId)
            _lastSavedTimeText.value = "Snapshot deleted"
        }
    }

    // --- Storyline Events Tracker ---
    fun startEditingEvent(event: StoryEvent?) {
        _editingEvent.value = event
    }

    fun saveStoryEvent(id: Int, title: String, description: String, arcPhase: String, orderIndex: Int, novelId: Int? = _selectedNovelId.value) {
        viewModelScope.launch {
            val assignedNovelId = novelId ?: _selectedNovelId.value ?: 1
            val event = StoryEvent(
                id = id,
                title = title,
                description = description,
                arcPhase = arcPhase,
                orderIndex = orderIndex,
                novelId = assignedNovelId
            )
            if (id == 0) {
                repository.insertEvent(event)
            } else {
                repository.updateEvent(event)
            }
            _editingEvent.value = null
            _lastSavedTimeText.value = "Storyline event saved"
        }
    }

    fun deleteStoryEvent(event: StoryEvent) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            if (_editingEvent.value?.id == event.id) {
                _editingEvent.value = null
            }
            _lastSavedTimeText.value = "Storyline event deleted"
        }
    }

    // --- ER Relationship Diagram connections ---
    fun addRelationship(sourceCharId: Int, targetId: Int, isToEvent: Boolean, relationType: String, description: String, novelId: Int? = _selectedNovelId.value) {
        viewModelScope.launch {
            val assignedNovelId = novelId ?: _selectedNovelId.value ?: 1
            val rel = CharacterRelationship(
                sourceCharacterId = sourceCharId,
                targetId = targetId,
                isToEvent = isToEvent,
                relationType = relationType,
                description = description,
                novelId = assignedNovelId
            )
            repository.insertRelationship(rel)
            _lastSavedTimeText.value = "Relationship saved"
        }
    }

    fun deleteRelationship(relationship: CharacterRelationship) {
        viewModelScope.launch {
            repository.deleteRelationship(relationship)
            _lastSavedTimeText.value = "Relationship deleted"
        }
    }

    // --- Intentional AI Story Analysis with Reviewable Proposed Changes ---
    fun requestAIStoryAnalysis(nodeId: Int, forceReanalyze: Boolean = false) {
        runAIStoryAnalysis(nodeId, isManual = true, forceReanalyze = forceReanalyze)
    }

    private fun runAIStoryAnalysis(nodeId: Int, isManual: Boolean, forceReanalyze: Boolean = false) {
        val node = allNodes.value.find { it.id == nodeId } ?: return
        if (node.content.isBlank()) {
            if (isManual) {
                _analysisStatusMessage.value = "Cannot analyze empty chapter text."
            }
            return
        }

        // Prevent duplicate simultaneous analysis
        if (_isAnalyzingStory.value) return

        val contentHash = node.content.hashCode().toString()
        if (!forceReanalyze && node.lastAnalyzedHash == contentHash) {
            if (isManual) {
                _analysisStatusMessage.value = "No new manuscript changes to analyze in this chapter."
            }
            return
        }

        _isAnalyzingStory.value = true
        _analysisStatusMessage.value = "Analyzing narrative with AI..."
        currentlyAnalyzingNodeId = nodeId
        currentlyAnalyzingHash = contentHash

        viewModelScope.launch {
            try {
                val novelId = findNovelFolderIdForNode(nodeId) ?: _selectedNovelId.value ?: 1
                val extracted = storyTracker.trackStory(node.content, node.name)

                // Build proposed changes by comparing against existing database data for this novel
                val changes = mutableListOf<ProposedChange>()

                val currentNovelChars = allCharacters.value.filter { it.novelId == novelId }
                val currentNovelEvents = allEvents.value.filter { it.novelId == novelId }
                val currentNovelRels = allRelationships.value.filter { it.novelId == novelId }

                // 1. Proposed Characters
                extracted.characters.forEach { extChar ->
                    val existing = currentNovelChars.find { it.name.equals(extChar.name, ignoreCase = true) }
                    val colorHex = extChar.avatarColorHex.removePrefix("#")
                    val colorInt = try {
                        android.graphics.Color.parseColor("#$colorHex")
                    } catch (e: Exception) {
                        0xFF6366F1.toInt()
                    }

                    if (existing == null) {
                        val newChar = CharacterProfile(
                            name = extChar.name,
                            role = extChar.role,
                            age = extChar.age,
                            appearance = extChar.appearance,
                            backstory = extChar.backstory,
                            plotArc = extChar.plotArc,
                            notes = extChar.notes,
                            avatarColor = colorInt,
                            novelId = novelId
                        )
                        changes.add(ProposedChange.NewCharacter(character = newChar))
                    } else {
                        // Check if any significant fields differ
                        val diffs = mutableListOf<String>()
                        if (existing.role != extChar.role) diffs.add("Role: ${existing.role} -> ${extChar.role}")
                        if (existing.appearance.isBlank() && extChar.appearance.isNotBlank()) diffs.add("New appearance details")
                        if (existing.backstory.isBlank() && extChar.backstory.isNotBlank()) diffs.add("New backstory details")
                        if (existing.plotArc.isBlank() && extChar.plotArc.isNotBlank()) diffs.add("Updated plot arc")

                        if (diffs.isNotEmpty()) {
                            val updatedChar = existing.copy(
                                role = extChar.role,
                                appearance = if (existing.appearance.isBlank()) extChar.appearance else existing.appearance,
                                backstory = if (existing.backstory.isBlank()) extChar.backstory else existing.backstory,
                                plotArc = if (existing.plotArc.isBlank()) extChar.plotArc else existing.plotArc,
                                notes = if (existing.notes.isBlank()) extChar.notes else existing.notes
                            )
                            changes.add(
                                ProposedChange.UpdatedCharacter(
                                    existingCharacter = existing,
                                    updatedCharacter = updatedChar,
                                    diffSummary = diffs.joinToString(", ")
                                )
                            )
                        }
                    }
                }

                // 2. Proposed Story Events
                extracted.plotEvents.forEach { extEvent ->
                    val existing = currentNovelEvents.find { it.title.equals(extEvent.title, ignoreCase = true) }
                    if (existing == null) {
                        val newEvent = StoryEvent(
                            title = extEvent.title,
                            description = extEvent.description,
                            arcPhase = extEvent.arcPhase,
                            orderIndex = (currentNovelEvents.maxOfOrNull { it.orderIndex } ?: 0) + 1,
                            novelId = novelId
                        )
                        changes.add(ProposedChange.NewStoryEvent(event = newEvent))
                    } else if (existing.description != extEvent.description || existing.arcPhase != extEvent.arcPhase) {
                        val updatedEvent = existing.copy(
                            description = extEvent.description,
                            arcPhase = extEvent.arcPhase
                        )
                        changes.add(
                            ProposedChange.UpdatedStoryEvent(
                                existingEvent = existing,
                                updatedEvent = updatedEvent,
                                diffSummary = "Phase: ${existing.arcPhase} -> ${extEvent.arcPhase}"
                            )
                        )
                    }
                }

                // 3. Proposed Relationships
                extracted.relationships.forEach { extRel ->
                    val sourceChar = currentNovelChars.find { it.name.equals(extRel.sourceCharacter, ignoreCase = true) }
                    if (sourceChar != null) {
                        val targetId = if (extRel.isToEvent) {
                            currentNovelEvents.find { it.title.equals(extRel.targetName, ignoreCase = true) }?.id
                        } else {
                            currentNovelChars.find { it.name.equals(extRel.targetName, ignoreCase = true) }?.id
                        }

                        if (targetId != null) {
                            val existingRel = currentNovelRels.find {
                                it.sourceCharacterId == sourceChar.id &&
                                it.targetId == targetId &&
                                it.isToEvent == extRel.isToEvent
                            }
                            if (existingRel == null) {
                                val rel = CharacterRelationship(
                                    sourceCharacterId = sourceChar.id,
                                    targetId = targetId,
                                    isToEvent = extRel.isToEvent,
                                    relationType = extRel.relationType,
                                    description = extRel.description,
                                    novelId = novelId
                                )
                                changes.add(
                                    ProposedChange.NewRelationship(
                                        relationship = rel,
                                        sourceName = sourceChar.name,
                                        targetName = extRel.targetName
                                    )
                                )
                            }
                        }
                    }
                }

                if (changes.isNotEmpty()) {
                    _proposedChanges.value = changes
                    _showReviewDialog.value = true
                    _analysisStatusMessage.value = "Found ${changes.size} proposed story changes to review."
                } else {
                    _analysisStatusMessage.value = "Story analysis complete. No new characters or plot changes detected."
                    // Save hash since no changes needed
                    repository.updateLastAnalyzedHash(nodeId, contentHash)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Story analysis failed: ${e.message}", e)
                _analysisStatusMessage.value = "Analysis error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isAnalyzingStory.value = false
            }
        }
    }

    fun clearAnalysisStatusMessage() {
        _analysisStatusMessage.value = null
    }

    // --- Review Workflow Actions ---
    fun acceptProposedChange(change: ProposedChange) {
        viewModelScope.launch {
            when (change) {
                is ProposedChange.NewCharacter -> {
                    repository.insertCharacter(change.character)
                }
                is ProposedChange.UpdatedCharacter -> {
                    repository.insertCharacter(change.updatedCharacter)
                }
                is ProposedChange.NewStoryEvent -> {
                    repository.insertEvent(change.event)
                }
                is ProposedChange.UpdatedStoryEvent -> {
                    repository.updateEvent(change.updatedEvent)
                }
                is ProposedChange.NewRelationship -> {
                    repository.insertRelationship(change.relationship)
                }
            }
            val remaining = _proposedChanges.value.filter { it.changeId != change.changeId }
            _proposedChanges.value = remaining
            if (remaining.isEmpty()) {
                finishReviewProcess()
            }
        }
    }

    fun rejectProposedChange(change: ProposedChange) {
        val remaining = _proposedChanges.value.filter { it.changeId != change.changeId }
        _proposedChanges.value = remaining
        if (remaining.isEmpty()) {
            finishReviewProcess()
        }
    }

    fun acceptAllProposedChanges() {
        viewModelScope.launch {
            _proposedChanges.value.forEach { change ->
                when (change) {
                    is ProposedChange.NewCharacter -> repository.insertCharacter(change.character)
                    is ProposedChange.UpdatedCharacter -> repository.insertCharacter(change.updatedCharacter)
                    is ProposedChange.NewStoryEvent -> repository.insertEvent(change.event)
                    is ProposedChange.UpdatedStoryEvent -> repository.updateEvent(change.updatedEvent)
                    is ProposedChange.NewRelationship -> repository.insertRelationship(change.relationship)
                }
            }
            _proposedChanges.value = emptyList()
            finishReviewProcess()
        }
    }

    fun rejectAllProposedChanges() {
        _proposedChanges.value = emptyList()
        finishReviewProcess()
    }

    fun dismissReviewDialog() {
        _showReviewDialog.value = false
    }

    private fun finishReviewProcess() {
        _showReviewDialog.value = false
        val nodeId = currentlyAnalyzingNodeId
        val hash = currentlyAnalyzingHash
        if (nodeId != null && hash != null) {
            viewModelScope.launch {
                repository.updateLastAnalyzedHash(nodeId, hash)
            }
        }
        currentlyAnalyzingNodeId = null
        currentlyAnalyzingHash = null
        _lastSavedTimeText.value = "Story database updated"
    }
}
