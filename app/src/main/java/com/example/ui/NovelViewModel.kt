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
import com.example.drive.DriveAuthorizationOutcome
import com.example.drive.DriveBackupError
import com.example.drive.DriveConnectionUiState
import com.example.drive.GoogleDriveBackupRepository
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
    val driveBackupRepository = GoogleDriveBackupRepository(application)

    private val _driveUiState = MutableStateFlow(DriveConnectionUiState())
    val driveUiState: StateFlow<DriveConnectionUiState> = _driveUiState.asStateFlow()

    private val _pendingDriveResolution = MutableStateFlow<android.app.PendingIntent?>(null)
    val pendingDriveResolution: StateFlow<android.app.PendingIntent?> = _pendingDriveResolution.asStateFlow()

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

        // Observe persisted Google Drive backup metadata
        viewModelScope.launch {
            driveBackupRepository.metadataFlow.collect { meta ->
                _driveUiState.update { current ->
                    current.copy(
                        isConnected = meta.isConnected,
                        connectedAccountLabel = meta.connectedAccountEmail,
                        lastBackupFileId = meta.lastBackupDriveFileId,
                        lastBackupTimestampMillis = meta.lastBackupTimestampMillis,
                        knownBackupCount = meta.knownBackupIds.size
                    )
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
        var count = 0
        var inWord = false
        for (i in 0 until text.length) {
            if (text[i].isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
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
    private val _grammarErrorMessage = MutableStateFlow<String?>(null)
    val grammarErrorMessage: StateFlow<String?> = _grammarErrorMessage.asStateFlow()

    fun clearGrammarErrorMessage() {
        _grammarErrorMessage.value = null
    }

    fun runGrammarCheck() {
        val currentText = _editorText.value
        if (currentText.isBlank()) return

        _isCheckingGrammar.value = true
        _grammarSuggestions.value = emptyList()
        _grammarErrorMessage.value = null

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

    /**
     * Applies a grammar suggestion targeting its exact character offsets in the manuscript.
     * Validates bounds and text at range to ensure no accidental modification of wrong occurrences.
     * Never uses replaceFirst().
     */
    fun applyGrammarSuggestion(suggestion: GrammarSuggestion): Boolean {
        val currentText = _editorText.value
        val start = suggestion.startOffset
        val end = suggestion.endOffset
        val original = suggestion.originalText
        val replacement = suggestion.suggestedText

        if (original.isBlank()) return false

        // 1. Validate that the stored offset range is within bounds
        if (start < 0 || end > currentText.length || start > end) {
            _grammarErrorMessage.value = "Manuscript changed. Suggestion range is out of date. Please re-run grammar check."
            return false
        }

        // 2. Verify that the text at that exact range matches the expected original text
        val textAtRange = currentText.substring(start, end)
        if (textAtRange != original) {
            _grammarErrorMessage.value = "Manuscript text at this position was edited. Please re-run grammar check."
            return false
        }

        // 3. Replace exactly that range (Never replaceFirst!)
        val updatedText = currentText.substring(0, start) + replacement + currentText.substring(end)
        updateEditorText(updatedText)

        // 4. Adjust offsets of remaining suggestions based on text length delta
        val delta = replacement.length - (end - start)
        val remainingSuggestions = _grammarSuggestions.value
            .filter { it != suggestion }
            .mapNotNull { other ->
                when {
                    other.endOffset <= start -> other
                    other.startOffset >= end -> other.copy(
                        startOffset = other.startOffset + delta,
                        endOffset = other.endOffset + delta
                    )
                    else -> null // Overlapping with the replaced range: discard
                }
            }
        _grammarSuggestions.value = remainingSuggestions

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
        return true
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
    fun escapeHtml(text: String): String {
        return buildString(text.length) {
            for (ch in text) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }
    }

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
                    val safeName = escapeHtml(node.name)
                    builder.append("<!DOCTYPE html>\n<html>\n<head>\n")
                    builder.append("<meta charset=\"utf-8\">\n")
                    builder.append("<title>$safeName</title>\n")
                    builder.append("<style>\n")
                    builder.append("body { font-family: 'Garamond', 'Georgia', serif; line-height: 1.8; margin: 2in 1.5in; font-size: 12pt; color: #111; }\n")
                    builder.append("h1 { text-align: center; text-transform: uppercase; margin-bottom: 2em; }\n")
                    builder.append("p { text-indent: 0.5in; margin-bottom: 0; margin-top: 0; text-align: justify; }\n")
                    builder.append("</style>\n</head>\n<body>\n")
                    builder.append("<h1>$safeName</h1>\n")
                    node.content.split("\n\n").forEach { paragraph ->
                        if (paragraph.isNotBlank()) {
                            builder.append("<p>${escapeHtml(paragraph.trim())}</p>\n")
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
                    val safeNovelTitle = escapeHtml(novelTitle)
                    builder.append("<!DOCTYPE html>\n<html>\n<head>\n")
                    builder.append("<meta charset=\"utf-8\">\n")
                    builder.append("<title>$safeNovelTitle</title>\n")
                    builder.append("<style>\n")
                    builder.append("body { font-family: 'Garamond', 'Georgia', serif; line-height: 1.8; margin: 2in 1.5in; font-size: 12pt; color: #111; }\n")
                    builder.append("h1.title { text-align: center; text-transform: uppercase; margin-top: 3in; margin-bottom: 3in; font-size: 28pt; }\n")
                    builder.append("h2 { text-align: center; margin-top: 2em; margin-bottom: 1em; page-break-before: always; }\n")
                    builder.append("p { text-indent: 0.5in; margin-bottom: 0; margin-top: 0; text-align: justify; }\n")
                    builder.append("</style>\n</head>\n<body>\n")
                    builder.append("<h1 class=\"title\">$safeNovelTitle</h1>\n")
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
                builder.append("<h${depth + 1} style=\"text-align: center;\">${escapeHtml(node.name)}</h${depth + 1}>\n")
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
                    builder.append("<h2>${escapeHtml(node.name)}</h2>\n")
                    node.content.split("\n\n").forEach { paragraph ->
                        if (paragraph.isNotBlank()) {
                            builder.append("<p>${escapeHtml(paragraph.trim())}</p>\n")
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

                // 3. Proposed Relationships (Evaluating merged context of existing + newly proposed characters)
                val proposedNewChars = changes.filterIsInstance<ProposedChange.NewCharacter>()
                val proposedNewEvents = changes.filterIsInstance<ProposedChange.NewStoryEvent>()

                extracted.relationships.forEach { extRel ->
                    // Resolve Source character (existing in DB or newly proposed in this pass)
                    val existingSource = currentNovelChars.find { it.name.equals(extRel.sourceCharacter, ignoreCase = true) }
                    val pendingSource = if (existingSource == null) proposedNewChars.find { it.character.name.equals(extRel.sourceCharacter, ignoreCase = true) } else null

                    val sourceId = existingSource?.id ?: 0
                    val sourceName = existingSource?.name ?: pendingSource?.character?.name
                    val sourcePendingId = pendingSource?.changeId

                    if (sourceName == null) return@forEach // Source character unknown, skip

                    // Resolve Target (existing in DB or newly proposed in this pass)
                    val targetId: Int
                    val targetName: String?
                    val targetPendingId: String?

                    if (extRel.isToEvent) {
                        val existingTargetEvent = currentNovelEvents.find { it.title.equals(extRel.targetName, ignoreCase = true) }
                        val pendingTargetEvent = if (existingTargetEvent == null) proposedNewEvents.find { it.event.title.equals(extRel.targetName, ignoreCase = true) } else null
                        targetId = existingTargetEvent?.id ?: 0
                        targetName = existingTargetEvent?.title ?: pendingTargetEvent?.event?.title
                        targetPendingId = pendingTargetEvent?.changeId
                    } else {
                        val existingTargetChar = currentNovelChars.find { it.name.equals(extRel.targetName, ignoreCase = true) }
                        val pendingTargetChar = if (existingTargetChar == null) proposedNewChars.find { it.character.name.equals(extRel.targetName, ignoreCase = true) } else null
                        targetId = existingTargetChar?.id ?: 0
                        targetName = existingTargetChar?.name ?: pendingTargetChar?.character?.name
                        targetPendingId = pendingTargetChar?.changeId
                    }

                    if (targetName == null) return@forEach // Target unknown, skip

                    // Check whether relationship already exists in the database
                    if (sourcePendingId == null && targetPendingId == null) {
                        val existingRel = currentNovelRels.find {
                            it.sourceCharacterId == sourceId &&
                            it.targetId == targetId &&
                            it.isToEvent == extRel.isToEvent
                        }

                        if (existingRel == null) {
                            // Case A: Relationship does not exist
                            val rel = CharacterRelationship(
                                sourceCharacterId = sourceId,
                                targetId = targetId,
                                isToEvent = extRel.isToEvent,
                                relationType = extRel.relationType,
                                description = extRel.description,
                                novelId = novelId
                            )
                            changes.add(
                                ProposedChange.NewRelationship(
                                    relationship = rel,
                                    sourceName = sourceName,
                                    targetName = targetName
                                )
                            )
                        } else {
                            // Case B: Relationship exists and is materially different
                            val isDifferentType = !existingRel.relationType.equals(extRel.relationType, ignoreCase = true)
                            val isDifferentDesc = extRel.description.isNotBlank() && existingRel.description != extRel.description

                            if (isDifferentType || isDifferentDesc) {
                                val updatedRel = existingRel.copy(
                                    relationType = extRel.relationType,
                                    description = if (extRel.description.isNotBlank()) extRel.description else existingRel.description
                                )
                                val diff = if (isDifferentType && isDifferentDesc) {
                                    "Type: ${existingRel.relationType} -> ${extRel.relationType}, Description updated"
                                } else if (isDifferentType) {
                                    "Type: ${existingRel.relationType} -> ${extRel.relationType}"
                                } else {
                                    "Description updated"
                                }
                                changes.add(
                                    ProposedChange.UpdatedRelationship(
                                        existingRelationship = existingRel,
                                        updatedRelationship = updatedRel,
                                        diffSummary = diff,
                                        sourceName = sourceName,
                                        targetName = targetName
                                    )
                                )
                            }
                            // Case C: Relationship is unchanged -> Do nothing
                        }
                    } else {
                        // Case A involving newly proposed characters:
                        // Prevent duplicate proposed relationships between the same pair in this pass
                        val alreadyProposed = changes.filterIsInstance<ProposedChange.NewRelationship>().any {
                            it.sourceName.equals(sourceName, ignoreCase = true) &&
                            it.targetName.equals(targetName, ignoreCase = true) &&
                            it.relationship.isToEvent == extRel.isToEvent
                        }

                        if (!alreadyProposed) {
                            val rel = CharacterRelationship(
                                sourceCharacterId = sourceId,
                                targetId = targetId,
                                isToEvent = extRel.isToEvent,
                                relationType = extRel.relationType,
                                description = extRel.description,
                                novelId = novelId
                            )
                            changes.add(
                                ProposedChange.NewRelationship(
                                    relationship = rel,
                                    sourceName = sourceName,
                                    targetName = targetName,
                                    sourcePendingChangeId = sourcePendingId,
                                    targetPendingChangeId = targetPendingId
                                )
                            )
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
                    val newId = repository.insertCharacter(change.character).toInt()
                    // Update any pending relationships that depend on this newly created character
                    val updatedChanges = _proposedChanges.value.map { other ->
                        if (other is ProposedChange.NewRelationship) {
                            var updatedRel = other.relationship
                            var updatedSourcePending = other.sourcePendingChangeId
                            var updatedTargetPending = other.targetPendingChangeId

                            if (other.sourcePendingChangeId == change.changeId) {
                                updatedRel = updatedRel.copy(sourceCharacterId = newId)
                                updatedSourcePending = null
                            }
                            if (!other.relationship.isToEvent && other.targetPendingChangeId == change.changeId) {
                                updatedRel = updatedRel.copy(targetId = newId)
                                updatedTargetPending = null
                            }
                            other.copy(
                                relationship = updatedRel,
                                sourcePendingChangeId = updatedSourcePending,
                                targetPendingChangeId = updatedTargetPending
                            )
                        } else {
                            other
                        }
                    }
                    _proposedChanges.value = updatedChanges.filter { it.changeId != change.changeId }
                }
                is ProposedChange.UpdatedCharacter -> {
                    repository.updateCharacter(change.updatedCharacter)
                    _proposedChanges.value = _proposedChanges.value.filter { it.changeId != change.changeId }
                }
                is ProposedChange.NewStoryEvent -> {
                    val newId = repository.insertEvent(change.event).toInt()
                    // Update any pending relationships pointing to this event
                    val updatedChanges = _proposedChanges.value.map { other ->
                        if (other is ProposedChange.NewRelationship && other.relationship.isToEvent && other.targetPendingChangeId == change.changeId) {
                            other.copy(
                                relationship = other.relationship.copy(targetId = newId),
                                targetPendingChangeId = null
                            )
                        } else {
                            other
                        }
                    }
                    _proposedChanges.value = updatedChanges.filter { it.changeId != change.changeId }
                }
                is ProposedChange.UpdatedStoryEvent -> {
                    repository.updateEvent(change.updatedEvent)
                    _proposedChanges.value = _proposedChanges.value.filter { it.changeId != change.changeId }
                }
                is ProposedChange.NewRelationship -> {
                    // Ensure source and target characters exist before inserting to avoid broken foreign keys
                    if (change.sourcePendingChangeId != null || change.targetPendingChangeId != null) {
                        _analysisStatusMessage.value = "Cannot add relationship '${change.sourceName} -> ${change.targetName}' until its pending character(s) are accepted."
                        return@launch
                    }
                    repository.insertRelationship(change.relationship)
                    _proposedChanges.value = _proposedChanges.value.filter { it.changeId != change.changeId }
                }
                is ProposedChange.UpdatedRelationship -> {
                    repository.updateRelationship(change.updatedRelationship)
                    _proposedChanges.value = _proposedChanges.value.filter { it.changeId != change.changeId }
                }
            }

            if (_proposedChanges.value.isEmpty()) {
                finishReviewProcess()
            }
        }
    }

    fun rejectProposedChange(change: ProposedChange) {
        // If rejecting a NewCharacter or NewStoryEvent, also discard any dependent relationships
        // so invalid foreign keys pointing to nonexistent characters are never created
        val discardedChangeIds = mutableSetOf(change.changeId)
        _proposedChanges.value.forEach { other ->
            if (other is ProposedChange.NewRelationship) {
                if (other.sourcePendingChangeId == change.changeId || other.targetPendingChangeId == change.changeId) {
                    discardedChangeIds.add(other.changeId)
                }
            }
        }

        val remaining = _proposedChanges.value.filter { !discardedChangeIds.contains(it.changeId) }
        _proposedChanges.value = remaining
        if (remaining.isEmpty()) {
            finishReviewProcess()
        }
    }

    fun acceptAllProposedChanges() {
        viewModelScope.launch {
            val currentChanges = _proposedChanges.value
            val result = repository.applyProposedChangesAtomically(currentChanges)
            if (result.isSuccess) {
               _proposedChanges.value = emptyList()
               finishReviewProcess()
            } else {
               Log.e(TAG, "Accept All transaction failed: ${result.exceptionOrNull()?.message}")
               _analysisStatusMessage.value = "Failed to apply changes: ${result.exceptionOrNull()?.localizedMessage ?: "Transaction rolled back"}"
               // Changes preserved for retry
            }
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

    // --- Google Drive Backup & Restore Foundation ---
    fun connectGoogleDrive() {
        viewModelScope.launch {
            _driveUiState.update {
                it.copy(isAuthorizing = true, error = null, statusMessage = "Requesting Google Drive authorization...")
            }
            val result = driveBackupRepository.connectAndAuthorize()
            result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is DriveAuthorizationOutcome.Authorized -> {
                            _driveUiState.update {
                                it.copy(
                                    isConnected = true,
                                    isAuthorizing = false,
                                    statusMessage = "Google Drive connected (drive.file scope).",
                                    error = null
                                )
                            }
                            refreshDriveBackups()
                        }
                        is DriveAuthorizationOutcome.ResolutionRequired -> {
                            _pendingDriveResolution.value = outcome.pendingIntent
                            _driveUiState.update {
                                it.copy(
                                    isAuthorizing = true,
                                    statusMessage = "Waiting for user consent..."
                                )
                            }
                        }
                    }
                },
                onFailure = { err ->
                    val driveError = driveBackupRepository.mapToDriveBackupError(err)
                    _driveUiState.update {
                        it.copy(
                            isAuthorizing = false,
                            statusMessage = null,
                            error = driveError
                        )
                    }
                }
            )
        }
    }

    fun onDriveResolutionResult(resultCode: Int, data: Intent?) {
        _pendingDriveResolution.value = null
        Log.i(
            TAG,
            "onDriveResolutionResult: resultCode=$resultCode, hasResultIntent=${data != null}"
        )
        viewModelScope.launch {
            val result = driveBackupRepository.handleAuthorizationIntentResult(resultCode, data)
            result.fold(
                onSuccess = {
                    _driveUiState.update {
                        it.copy(
                            isConnected = true,
                            isAuthorizing = false,
                            statusMessage = "Google Drive authorized and ready for manual backups.",
                            error = null
                        )
                    }
                    refreshDriveBackups()
                },
                onFailure = { err ->
                    val driveError = driveBackupRepository.mapToDriveBackupError(err)
                    _driveUiState.update {
                        it.copy(
                            isConnected = false,
                            isAuthorizing = false,
                            statusMessage = null,
                            error = driveError
                        )
                    }
                }
            )
        }
    }

    fun onDriveResolutionLaunchFailed(exception: Throwable) {
        _pendingDriveResolution.value = null
        val driveError = driveBackupRepository.handleAuthorizationLaunchFailure(exception)
        _driveUiState.update {
            it.copy(
                isConnected = false,
                isAuthorizing = false,
                statusMessage = null,
                error = driveError
            )
        }
    }

    fun clearPendingDriveResolution() {
        _pendingDriveResolution.value = null
    }

    fun disconnectGoogleDrive() {
        viewModelScope.launch {
            _driveUiState.update {
                it.copy(isBusy = true, error = null, statusMessage = "Disconnecting Google Drive...")
            }
            val result = driveBackupRepository.disconnectAndRevoke()
            result.fold(
                onSuccess = {
                    _driveUiState.update {
                        it.copy(
                            isConnected = false,
                            isBusy = false,
                            connectedAccountLabel = null,
                            remoteBackups = emptyList(),
                            statusMessage = "Disconnected from Google Drive.",
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    val driveError = driveBackupRepository.mapToDriveBackupError(err)
                    _driveUiState.update {
                        it.copy(
                            isConnected = false,
                            isBusy = false,
                            statusMessage = null,
                            error = driveError
                        )
                    }
                }
            )
        }
    }

    fun uploadManualDriveBackup() {
        if (!_driveUiState.value.isConnected) return
        viewModelScope.launch {
            _driveUiState.update {
                it.copy(isBusy = true, error = null, statusMessage = "Creating consistent SQLite snapshot and uploading to Google Drive...")
            }

            // Flush any pending in-memory manuscript edits to Room before snapshotting
            val currentActive = _activeNode.value
            val currentText = _editorText.value
            if (currentActive != null && !currentActive.isFolder && currentActive.content != currentText) {
                val updatedNode = currentActive.copy(
                    content = currentText,
                    wordCount = countWords(currentText),
                    lastUpdated = System.currentTimeMillis()
                )
                repository.updateNode(updatedNode)
                _activeNode.value = updatedNode
                _lastSavedTimeText.value = "Saved locally just now"
            }

            val backupFileName = "novel_studio_backup_${System.currentTimeMillis()}.sqlite"
            val result = driveBackupRepository.backupDatabaseSnapshotToDrive(
                backupFileName = backupFileName
            )
            result.fold(
                onSuccess = { uploaded ->
                    _driveUiState.update {
                        it.copy(
                            isBusy = false,
                            remoteBackups = listOf(uploaded) + it.remoteBackups.filterNot { b -> b.id == uploaded.id },
                            statusMessage = "Uploaded consistent snapshot '${uploaded.name}' to Google Drive.",
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    val driveError = driveBackupRepository.mapToDriveBackupError(err)
                    _driveUiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = null,
                            error = driveError
                        )
                    }
                }
            )
        }
    }

    fun refreshDriveBackups() {
        if (!_driveUiState.value.isConnected) return
        viewModelScope.launch {
            _driveUiState.update {
                it.copy(isBusy = true, error = null, statusMessage = "Listing Google Drive backups...")
            }
            val result = driveBackupRepository.listBackupFiles()
            result.fold(
                onSuccess = { files ->
                    _driveUiState.update {
                        it.copy(
                            isBusy = false,
                            remoteBackups = files,
                            statusMessage = "Found ${files.size} backup(s) in Google Drive.",
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    val driveError = driveBackupRepository.mapToDriveBackupError(err)
                    _driveUiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = null,
                            error = driveError
                        )
                    }
                }
            )
        }
    }

    /**
     * Restores a user-selected Google Drive backup safely into the local database:
     * - Cancels active editor auto-save coroutines
     * - Downloads to a unique local staging file
     * - Validates SQLite integrity and Novel Studio Room schema compatibility
     * - Creates a local pre-restore safety backup (aborting if it fails)
     * - Closes Room, replaces the database cleanly without stale WAL/SHM/journal files,
     *   reopens Room, and rebinds NovelRepository Flows (rolling back on any failure)
     */
    fun restoreSelectedDriveBackup(driveFileId: String, backupFileName: String) {
        if (!_driveUiState.value.isConnected || _driveUiState.value.isBusy || _driveUiState.value.isRestoring) return
        viewModelScope.launch {
            // Stop any active editor auto-save before closing/replacing the database
            autoSaveJob?.cancel()
            selectNode(null)

            _driveUiState.update {
                it.copy(
                    isBusy = true,
                    isRestoring = true,
                    restoringFileId = driveFileId,
                    restoreProgressStep = "Preparing to restore '$backupFileName'...",
                    statusMessage = "Preparing to restore '$backupFileName'...",
                    error = null
                )
            }

            val result = driveBackupRepository.restoreBackupFromDrive(
                driveFileId = driveFileId,
                backupFileName = backupFileName,
                onProgress = { stepMessage ->
                    _driveUiState.update { state ->
                        state.copy(
                            restoreProgressStep = stepMessage,
                            statusMessage = stepMessage
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { (reopenedDb, summary) ->
                    repository.rebindDatabase(reopenedDb)
                    _selectedNovelId.value = null
                    _lastSavedTimeText.value = "Restored from '${summary.backupFileName}'"
                    _driveUiState.update {
                        it.copy(
                            isBusy = false,
                            isRestoring = false,
                            restoringFileId = null,
                            restoreProgressStep = null,
                            lastRestoreSummary = summary,
                            statusMessage = "Restored '${summary.backupFileName}' (${summary.restoredNodesCount} manuscript items, ${summary.restoredCharactersCount} characters, ${summary.restoredEventsCount} events).",
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    // Ensure repository is rebound to the active (or rolled-back) Room instance
                    runCatching {
                        repository.rebindDatabase(AppDatabase.getDatabase(getApplication()))
                    }
                    val driveError = driveBackupRepository.mapToDriveBackupError(err)
                    _driveUiState.update {
                        it.copy(
                            isBusy = false,
                            isRestoring = false,
                            restoringFileId = null,
                            restoreProgressStep = null,
                            statusMessage = null,
                            error = driveError
                        )
                    }
                }
            )
        }
    }

    fun clearDriveError() {
        _driveUiState.update { it.copy(error = null) }
    }
}
