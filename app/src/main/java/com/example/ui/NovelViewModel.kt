package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

enum class SyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

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

    // --- Cloud Sync state ---
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncedTimeText = MutableStateFlow("Synced locally")
    val lastSyncedTimeText: StateFlow<String> = _lastSyncedTimeText.asStateFlow()

    // --- Active Character profile details ---
    private val _editingCharacter = MutableStateFlow<CharacterProfile?>(null)
    val editingCharacter: StateFlow<CharacterProfile?> = _editingCharacter.asStateFlow()

    private var autoSaveJob: Job? = null
    private var aiTrackerJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NovelRepository(
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
            if (novelId == null) chars
            else chars.filter { it.novelId == novelId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredEvents = combine(allEvents, _selectedNovelId) { events, novelId ->
            if (novelId == null) events
            else events.filter { it.novelId == novelId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredRelationships = combine(allRelationships, _selectedNovelId) { relationships, novelId ->
            if (novelId == null) relationships
            else relationships.filter { it.novelId == novelId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Seed data and start initial setup
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            updateSyncTimeText()
            startPeriodicAutoSync()
        }

        // Auto select first novel if selectedNovelId is null
        viewModelScope.launch {
            allNodes.collect { nodes ->
                if (_selectedNovelId.value == null) {
                    val firstNovel = nodes.firstOrNull { it.isFolder && it.parentId == null }
                    if (firstNovel != null) {
                        _selectedNovelId.value = firstNovel.id
                    }
                }
            }
        }

        // Auto-update selectedNovelId if activeNode belongs to a specific novel folder
        viewModelScope.launch {
            activeNode.collect { node ->
                if (node != null) {
                    val novelId = findNovelFolderIdForNode(node.id)
                    if (novelId != null) {
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

    // --- Node operations ---
    fun selectNode(nodeId: Int?) {
        _selectedNodeId.value = nodeId
        _grammarSuggestions.value = emptyList() // clear previous suggestions
        if (nodeId == null) {
            _activeNode.value = null
            _editorText.value = ""
            _wordCount.value = 0
            autoSaveJob?.cancel()
            aiTrackerJob?.cancel()
        } else {
            autoSaveJob?.cancel()
            aiTrackerJob?.cancel()
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

    private fun setupAutoSave(node: ManuscriptNode) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            _editorText
                .debounce(1000) // save after 1 second of inactivity
                .collect { content ->
                    val currentActive = _activeNode.value
                    if (currentActive != null && currentActive.id == node.id && currentActive.content != content) {
                        val updatedNode = currentActive.copy(
                            content = content,
                            wordCount = countWords(content),
                            lastUpdated = System.currentTimeMillis()
                        )
                        repository.updateNode(updatedNode)
                        _activeNode.value = updatedNode
                        triggerAutoSync()

                        // Automatically trigger AI story analysis in the background
                        // after 5 seconds of continued inactivity following the save.
                        aiTrackerJob?.cancel()
                        aiTrackerJob = viewModelScope.launch {
                            delay(5000)
                            if (content.trim().length > 15) {
                                runAIStoryAnalysis(node.id)
                            }
                        }
                    }
                }
        }
    }

    fun createFolder(name: String, parentId: Int?) {
        viewModelScope.launch {
            repository.insertNode(
                ManuscriptNode(
                    name = name,
                    parentId = parentId,
                    isFolder = true
                )
            )
            triggerAutoSync()
        }
    }

    fun createFile(name: String, parentId: Int?) {
        viewModelScope.launch {
            val newId = repository.insertNode(
                ManuscriptNode(
                    name = name,
                    parentId = parentId,
                    isFolder = false,
                    content = ""
                )
            ).toInt()
            selectNode(newId)
            triggerAutoSync()
        }
    }

    fun deleteNode(node: ManuscriptNode) {
        viewModelScope.launch {
            if (selectedNodeId.value == node.id) {
                selectNode(null)
            }
            repository.deleteNode(node)
            // also recursively delete children if folder (simplified, could do a deeper clean but this is clean)
            if (node.isFolder) {
                val children = allNodes.value.filter { it.parentId == node.id }
                children.forEach { child ->
                    repository.deleteNode(child)
                }
            }
            triggerAutoSync()
        }
    }

    fun renameNode(nodeId: Int, newName: String) {
        viewModelScope.launch {
            val node = repository.getNodeById(nodeId)
            if (node != null) {
                repository.updateNode(node.copy(name = newName, lastUpdated = System.currentTimeMillis()))
                triggerAutoSync()
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

        // Avoid empty target matching
        if (original.isBlank()) return

        // Simple string replacement for direct edits
        val updatedText = currentText.replaceFirst(original, replacement)
        updateEditorText(updatedText)

        // Remove applied suggestion from the active list
        _grammarSuggestions.value = _grammarSuggestions.value.filter { it != suggestion }

        // Trigger an immediate save of text
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
                novelId = novelId
            )
            repository.insertCharacter(char)
            _editingCharacter.value = null
            triggerAutoSync()
        }
    }

    fun deleteCharacter(character: CharacterProfile) {
        viewModelScope.launch {
            repository.deleteCharacter(character)
            if (_editingCharacter.value?.id == character.id) {
                _editingCharacter.value = null
            }
            triggerAutoSync()
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

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value
            repository.insertOrUpdateSettings(currentSettings.copy(isAutoSyncEnabled = enabled))
        }
    }

    // --- Automatic / Simulated Cloud Sync ---
    private fun triggerAutoSync() {
        if (settings.value.isAutoSyncEnabled) {
            viewModelScope.launch {
                triggerSync()
            }
        }
    }

    suspend fun triggerSync() {
        if (_syncStatus.value == SyncStatus.SYNCING) return
        _syncStatus.value = SyncStatus.SYNCING
        delay(1500) // simulate network delay for saving to the cloud database
        _syncStatus.value = SyncStatus.SUCCESS
        val now = System.currentTimeMillis()
        val currentSettings = settings.value
        repository.insertOrUpdateSettings(currentSettings.copy(lastSyncedTime = now))
        updateSyncTimeText()
        delay(1500)
        _syncStatus.value = SyncStatus.IDLE
    }

    private fun startPeriodicAutoSync() {
        viewModelScope.launch {
            while (true) {
                delay(60000) // check and sync every minute if enabled and state is dirty
                updateSyncTimeText()
            }
        }
    }

    private fun updateSyncTimeText() {
        val lastSynced = settings.value.lastSyncedTime
        val diff = System.currentTimeMillis() - lastSynced
        _lastSyncedTimeText.value = when {
            diff < 10000 -> "Synced just now"
            diff < 60000 -> "Synced less than a minute ago"
            else -> {
                val mins = diff / 60000
                "Synced ${mins}m ago"
            }
        }
    }

    // --- Integrated Export Manager ---
    fun exportManuscript(context: Context, format: String, nodeId: Int?): Uri? {
        val nodesList = allNodes.value
        val builder = java.lang.StringBuilder()

        if (nodeId != null) {
            // Export single file
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
                    builder.append("body { font-family: 'Garamond', 'Georgia', serif; line-height: 1.8; margin: 3in 2in; font-size: 12pt; }\n")
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
            // Export entire Book
            builder.append("THE SHATTERED MANUSCRIPT\n\n")
            val rootNodes = nodesList.filter { it.parentId == null }
            rootNodes.forEach { rootNode ->
                appendNodeToExport(rootNode, nodesList, format, builder, 0)
            }
        }

        // Write to temporary sharing file
        val extension = when (format) {
            "Standard Manuscript (TXT)" -> "txt"
            "Creative Markdown (MD)" -> "md"
            "Professional HTML" -> "html"
            else -> "txt"
        }
        val fileName = "Novel_Export_${System.currentTimeMillis()}.$extension"
        val file = File(context.cacheDir, fileName)
        try {
            val writer = FileWriter(file)
            writer.write(builder.toString())
            writer.flush()
            writer.close()

            // Get Share Uri via FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            // Trigger Share Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (extension) {
                    "txt" -> "text/plain"
                    "md" -> "text/markdown"
                    "html" -> "text/html"
                    else -> "text/plain"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Manuscript Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Manuscript via"))
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write export file: ${e.message}", e)
        }
        return null
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
                    builder.append("<h${depth + 2}>${node.name}</h${depth + 2}>\n")
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

    // --- Version Backups (GitHub style) & Commits ---
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
            triggerAutoSync()
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
                triggerAutoSync()
            }
        }
    }

    fun deleteCommit(commitId: Int) {
        viewModelScope.launch {
            repository.deleteCommitById(commitId)
            triggerAutoSync()
        }
    }

    // --- Storyline Events Tracker ---
    fun startEditingEvent(event: StoryEvent?) {
        _editingEvent.value = event
    }

    fun saveStoryEvent(id: Int, title: String, description: String, arcPhase: String, orderIndex: Int, novelId: Int? = _selectedNovelId.value) {
        viewModelScope.launch {
            val event = StoryEvent(
                id = id,
                title = title,
                description = description,
                arcPhase = arcPhase,
                orderIndex = orderIndex,
                novelId = novelId
            )
            if (id == 0) {
                repository.insertEvent(event)
            } else {
                repository.updateEvent(event)
            }
            _editingEvent.value = null
            triggerAutoSync()
        }
    }

    fun deleteStoryEvent(event: StoryEvent) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            if (_editingEvent.value?.id == event.id) {
                _editingEvent.value = null
            }
            triggerAutoSync()
        }
    }

    // --- ER Relationship Diagram connections ---
    fun addRelationship(sourceCharId: Int, targetId: Int, isToEvent: Boolean, relationType: String, description: String, novelId: Int? = _selectedNovelId.value) {
        viewModelScope.launch {
            val rel = CharacterRelationship(
                sourceCharacterId = sourceCharId,
                targetId = targetId,
                isToEvent = isToEvent,
                relationType = relationType,
                description = description,
                novelId = novelId
            )
            repository.insertRelationship(rel)
            triggerAutoSync()
        }
    }

    fun deleteRelationship(relationship: CharacterRelationship) {
        viewModelScope.launch {
            repository.deleteRelationship(relationship)
            triggerAutoSync()
        }
    }

    // --- Novel Selector & Tree Helper ---
    fun selectNovel(novelId: Int?) {
        _selectedNovelId.value = novelId
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

    // --- AI Story Auto-Tracker Agent ---
    private val _isAnalyzingStory = MutableStateFlow(false)
    val isAnalyzingStory: StateFlow<Boolean> = _isAnalyzingStory.asStateFlow()

    fun runAIStoryAnalysis(nodeId: Int) {
        val node = allNodes.value.find { it.id == nodeId } ?: return
        if (node.content.isBlank()) return

        _isAnalyzingStory.value = true

        viewModelScope.launch {
            try {
                // Find corresponding novel folder for this node
                val novelId = findNovelFolderIdForNode(nodeId) ?: _selectedNovelId.value
                val extracted = storyTracker.trackStory(node.content, node.name)

                // 1. Insert/Update Character Profiles
                val characterIds = mutableMapOf<String, Int>()
                extracted.characters.forEach { extChar ->
                    // Check if character already exists in this novel (case-insensitive)
                    val existing = allCharacters.value.find {
                        it.name.equals(extChar.name, ignoreCase = true) && it.novelId == novelId
                    }

                    val colorHex = extChar.avatarColorHex.removePrefix("#")
                    val colorInt = try {
                        android.graphics.Color.parseColor("#$colorHex")
                    } catch (e: Exception) {
                        0xFF6366F1.toInt()
                    }

                    val charId = if (existing != null) {
                        val updated = existing.copy(
                            role = extChar.role,
                            age = if (existing.age.isBlank()) extChar.age else existing.age,
                            appearance = if (existing.appearance.isBlank()) extChar.appearance else existing.appearance,
                            backstory = if (existing.backstory.isBlank()) extChar.backstory else existing.backstory,
                            plotArc = if (existing.plotArc.isBlank()) extChar.plotArc else existing.plotArc,
                            notes = if (existing.notes.isBlank()) extChar.notes else existing.notes,
                            avatarColor = colorInt
                        )
                        repository.insertCharacter(updated)
                        existing.id
                    } else {
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
                        repository.insertCharacter(newChar).toInt()
                    }
                    characterIds[extChar.name.lowercase().trim()] = charId
                }

                // 2. Insert/Update Story Timeline Events (Plot milestones)
                extracted.plotEvents.forEach { extEvent ->
                    val existing = allEvents.value.find {
                        it.title.equals(extEvent.title, ignoreCase = true) && it.novelId == novelId
                    }

                    if (existing != null) {
                        val updated = existing.copy(
                            description = extEvent.description,
                            arcPhase = extEvent.arcPhase,
                            orderIndex = extEvent.orderIndex
                        )
                        repository.updateEvent(updated)
                    } else {
                        val newEvent = StoryEvent(
                            title = extEvent.title,
                            description = extEvent.description,
                            arcPhase = extEvent.arcPhase,
                            orderIndex = extEvent.orderIndex,
                            novelId = novelId
                        )
                        repository.insertEvent(newEvent)
                    }
                }

                // Wait a moment for DB updates to settle
                delay(500)

                // 3. Insert/Update Relationships (Connections)
                val freshChars = repository.allCharacters.first()
                val freshEvents = repository.allEvents.first()
                
                extracted.relationships.forEach { extRel ->
                    val sourceChar = freshChars.find { 
                        it.name.equals(extRel.sourceCharacter, ignoreCase = true) && it.novelId == novelId 
                    } ?: return@forEach

                    val targetId = if (extRel.isToEvent) {
                        freshEvents.find { 
                            it.title.equals(extRel.targetName, ignoreCase = true) && it.novelId == novelId 
                        }?.id
                    } else {
                        freshChars.find { 
                            it.name.equals(extRel.targetName, ignoreCase = true) && it.novelId == novelId 
                        }?.id
                    }

                    if (targetId != null) {
                        val existingRel = allRelationships.value.find {
                            it.sourceCharacterId == sourceChar.id &&
                            it.targetId == targetId &&
                            it.isToEvent == extRel.isToEvent &&
                            it.novelId == novelId
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
                            repository.insertRelationship(rel)
                        }
                    }
                }
                triggerAutoSync()
            } catch (e: Exception) {
                Log.e(TAG, "AI story tracker parsing failed: ${e.message}", e)
            } finally {
                _isAnalyzingStory.value = false
            }
        }
    }
}
