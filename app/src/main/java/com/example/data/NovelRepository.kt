package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import java.util.ArrayDeque

@OptIn(ExperimentalCoroutinesApi::class)
class NovelRepository(
    initialDatabase: AppDatabase,
    initialManuscriptDao: ManuscriptDao = initialDatabase.manuscriptDao(),
    initialCharacterDao: CharacterDao = initialDatabase.characterDao(),
    initialSettingsDao: SettingsDao = initialDatabase.settingsDao(),
    initialStoryEventDao: StoryEventDao = initialDatabase.storyEventDao(),
    initialCharacterRelationshipDao: CharacterRelationshipDao = initialDatabase.characterRelationshipDao(),
    initialManuscriptCommitDao: ManuscriptCommitDao = initialDatabase.manuscriptCommitDao()
) {
    private val activeDatabaseFlow = MutableStateFlow(initialDatabase)

    private val database: AppDatabase
        get() = activeDatabaseFlow.value
    private val manuscriptDao: ManuscriptDao
        get() = activeDatabaseFlow.value.manuscriptDao()
    private val characterDao: CharacterDao
        get() = activeDatabaseFlow.value.characterDao()
    private val settingsDao: SettingsDao
        get() = activeDatabaseFlow.value.settingsDao()
    private val storyEventDao: StoryEventDao
        get() = activeDatabaseFlow.value.storyEventDao()
    private val characterRelationshipDao: CharacterRelationshipDao
        get() = activeDatabaseFlow.value.characterRelationshipDao()
    private val manuscriptCommitDao: ManuscriptCommitDao
        get() = activeDatabaseFlow.value.manuscriptCommitDao()

    fun rebindDatabase(newDatabase: AppDatabase) {
        activeDatabaseFlow.value = newDatabase
    }

    val allNodes: Flow<List<ManuscriptNode>> = activeDatabaseFlow.flatMapLatest { db ->
        db.manuscriptDao().getAllNodesFlow()
    }
    val allCharacters: Flow<List<CharacterProfile>> = activeDatabaseFlow.flatMapLatest { db ->
        db.characterDao().getAllCharactersFlow()
    }
    val settings: Flow<WritingSettings?> = activeDatabaseFlow.flatMapLatest { db ->
        db.settingsDao().getSettingsFlow()
    }
    val allEvents: Flow<List<StoryEvent>> = activeDatabaseFlow.flatMapLatest { db ->
        db.storyEventDao().getAllEventsFlow()
    }
    val allRelationships: Flow<List<CharacterRelationship>> = activeDatabaseFlow.flatMapLatest { db ->
        db.characterRelationshipDao().getAllRelationshipsFlow()
    }
    val allCommits: Flow<List<ManuscriptCommit>> = activeDatabaseFlow.flatMapLatest { db ->
        db.manuscriptCommitDao().getAllCommitsFlow()
    }

    fun getCharactersByNovelFlow(novelId: Int): Flow<List<CharacterProfile>> =
        activeDatabaseFlow.flatMapLatest { db -> db.characterDao().getCharactersByNovelFlow(novelId) }

    fun getEventsByNovelFlow(novelId: Int): Flow<List<StoryEvent>> =
        activeDatabaseFlow.flatMapLatest { db -> db.storyEventDao().getEventsByNovelFlow(novelId) }

    fun getRelationshipsByNovelFlow(novelId: Int): Flow<List<CharacterRelationship>> =
        activeDatabaseFlow.flatMapLatest { db -> db.characterRelationshipDao().getRelationshipsByNovelFlow(novelId) }

    fun getNodeByIdFlow(id: Int): Flow<ManuscriptNode?> =
        activeDatabaseFlow.flatMapLatest { db -> db.manuscriptDao().getNodeByIdFlow(id) }
    suspend fun getNodeById(id: Int): ManuscriptNode? = manuscriptDao.getNodeById(id)

    suspend fun insertNode(node: ManuscriptNode): Long = manuscriptDao.insertNode(node)
    suspend fun updateNode(node: ManuscriptNode) = manuscriptDao.updateNode(node)
    suspend fun updateLastAnalyzedHash(nodeId: Int, hash: String?) = manuscriptDao.updateLastAnalyzedHash(nodeId, hash)

    /**
     * Recursively and safely deletes a manuscript node (and all its nested descendants).
     * If the node is a root novel folder (parentId == null && isFolder == true),
     * this transaction atomically cleans up all associated characters, plot events,
     * and relationships belonging to the novel, leaving no orphaned data.
     */
    suspend fun deleteNodeRecursively(node: ManuscriptNode) {
        database.withTransaction {
            val allExistingNodes = manuscriptDao.getAllNodesList()
            val nodesToDelete = mutableListOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(node.id)

            while (queue.isNotEmpty()) {
                val currentId = queue.poll() ?: continue
                nodesToDelete.add(currentId)
                val children = allExistingNodes.filter { it.parentId == currentId }
                for (child in children) {
                    if (!nodesToDelete.contains(child.id)) {
                        queue.add(child.id)
                    }
                }
            }

            // 1. Delete all commits for all descendant nodes
            if (nodesToDelete.isNotEmpty()) {
                manuscriptCommitDao.deleteCommitsForNodes(nodesToDelete)
                manuscriptDao.deleteNodesByIds(nodesToDelete)
            }

            // 2. If deleting an entire novel (root-level folder), cascade clean its novel-specific data
            val isRootNovel = node.parentId == null && node.isFolder
            if (isRootNovel) {
                characterRelationshipDao.deleteRelationshipsByNovel(node.id)
                characterDao.deleteCharactersByNovel(node.id)
                storyEventDao.deleteEventsByNovel(node.id)
            }
        }
    }

    suspend fun getCharacterById(id: Int): CharacterProfile? = characterDao.getCharacterById(id)
    suspend fun insertCharacter(character: CharacterProfile): Long = characterDao.insertCharacter(character)
    suspend fun updateCharacter(character: CharacterProfile) = characterDao.updateCharacter(character)
    suspend fun deleteCharacter(character: CharacterProfile) {
        database.withTransaction {
            characterRelationshipDao.deleteRelationshipsForCharacter(character.id)
            characterDao.deleteCharacter(character)
        }
    }

    suspend fun getSettings(): WritingSettings? = settingsDao.getSettings()
    suspend fun insertOrUpdateSettings(settings: WritingSettings) = settingsDao.insertOrUpdateSettings(settings)

    // Commit history methods
    fun getCommitsForNodeFlow(nodeId: Int): Flow<List<ManuscriptCommit>> =
        activeDatabaseFlow.flatMapLatest { db -> db.manuscriptCommitDao().getCommitsForNodeFlow(nodeId) }
    suspend fun insertCommit(commit: ManuscriptCommit) = manuscriptCommitDao.insertCommit(commit)
    suspend fun deleteCommitById(commitId: Int) = manuscriptCommitDao.deleteCommitById(commitId)

    // Story Arc Events
    suspend fun insertEvent(event: StoryEvent): Long = storyEventDao.insertEvent(event)
    suspend fun updateEvent(event: StoryEvent) = storyEventDao.updateEvent(event)
    suspend fun deleteEvent(event: StoryEvent) {
        database.withTransaction {
            characterRelationshipDao.deleteRelationshipsForEvent(event.id)
            storyEventDao.deleteEvent(event)
        }
    }

    // Relationships
    suspend fun insertRelationship(relationship: CharacterRelationship) = characterRelationshipDao.insertRelationship(relationship)
    suspend fun updateRelationship(relationship: CharacterRelationship) = characterRelationshipDao.updateRelationship(relationship)
    suspend fun deleteRelationship(relationship: CharacterRelationship) = characterRelationshipDao.deleteRelationship(relationship)

    /**
     * Atomically commits all accepted proposed changes within a single Room database transaction.
     * Preserves entity dependencies, updates existing entities via @Update, and maps newly generated
     * character/event IDs for dependent relationships. If an error occurs, the entire transaction rolls back.
     */
    suspend fun applyProposedChangesAtomically(changes: List<ProposedChange>): Result<Unit> = runCatching {
        database.withTransaction {
            val insertedCharIds = mutableMapOf<String, Int>()
            val insertedEventIds = mutableMapOf<String, Int>()

            // 1. Insert New Characters
            for (change in changes.filterIsInstance<ProposedChange.NewCharacter>()) {
                val newId = characterDao.insertCharacter(change.character).toInt()
                insertedCharIds[change.changeId] = newId
            }

            // 2. Update Existing Characters via proper DAO @Update
            for (change in changes.filterIsInstance<ProposedChange.UpdatedCharacter>()) {
                characterDao.updateCharacter(change.updatedCharacter)
            }

            // 3. Insert New Story Events
            for (change in changes.filterIsInstance<ProposedChange.NewStoryEvent>()) {
                val newId = storyEventDao.insertEvent(change.event).toInt()
                insertedEventIds[change.changeId] = newId
            }

            // 4. Update Existing Story Events via proper DAO @Update
            for (change in changes.filterIsInstance<ProposedChange.UpdatedStoryEvent>()) {
                storyEventDao.updateEvent(change.updatedEvent)
            }

            // 5. Insert New Relationships with resolved IDs
            for (change in changes.filterIsInstance<ProposedChange.NewRelationship>()) {
                val sourceId = if (change.sourcePendingChangeId != null) {
                    insertedCharIds[change.sourcePendingChangeId] ?: change.relationship.sourceCharacterId
                } else {
                    change.relationship.sourceCharacterId
                }

                val targetId = if (change.targetPendingChangeId != null) {
                    if (change.relationship.isToEvent) {
                        insertedEventIds[change.targetPendingChangeId] ?: change.relationship.targetId
                    } else {
                        insertedCharIds[change.targetPendingChangeId] ?: change.relationship.targetId
                    }
                } else {
                    change.relationship.targetId
                }

                if (sourceId > 0 && targetId > 0) {
                    characterRelationshipDao.insertRelationship(
                        change.relationship.copy(
                            sourceCharacterId = sourceId,
                            targetId = targetId
                        )
                    )
                } else {
                    android.util.Log.w("NovelRepository", "Skipped proposed relationship '${change.sourceName} -> ${change.targetName}': required character was not created.")
                }
            }

            // 6. Update Existing Relationships via proper DAO @Update
            for (change in changes.filterIsInstance<ProposedChange.UpdatedRelationship>()) {
                characterRelationshipDao.updateRelationship(change.updatedRelationship)
            }
        }
    }

    /**
     * Conservatively and idempotently repairs legacy records where novelId was null.
     * If exactly one root novel exists, assigns orphaned records to that novel.
     * If multiple root novels exist, does NOT arbitrarily assign records, leaving ambiguous
     * records unassigned so user lore is never moved across novels.
     */
    suspend fun repairMissingNovelAssociations() {
        val allNodesList = manuscriptDao.getAllNodesList()
        val rootNovels = allNodesList.filter { it.parentId == null && it.isFolder }
        if (rootNovels.size == 1) {
            val singleNovel = rootNovels.first()
            database.withTransaction {
                characterDao.updateMissingNovelId(singleNovel.id)
                storyEventDao.updateMissingNovelId(singleNovel.id)
                characterRelationshipDao.updateMissingNovelId(singleNovel.id)
            }
        } else {
            // Multiple root novels exist: leave unassigned to avoid cross-novel contamination
            android.util.Log.i("NovelRepository", "Multiple root novels detected (${rootNovels.size}); leaving ambiguous unassigned records untouched.")
        }
    }

    suspend fun seedInitialDataIfEmpty() {
        // Seed default settings if not exists
        if (settingsDao.getSettings() == null) {
            settingsDao.insertOrUpdateSettings(WritingSettings())
        }

        // Seed some initial chapters and folders if empty
        val currentNodes = manuscriptDao.getAllNodesFlow().firstOrNull() ?: emptyList()
        var novelFolderId = currentNodes.firstOrNull { it.parentId == null && it.isFolder }?.id ?: 0

        if (currentNodes.isEmpty()) {
            // Create a main Novel Folder (Root Novel)
            novelFolderId = manuscriptDao.insertNode(
                ManuscriptNode(
                    name = "The Lost Cartographer",
                    parentId = null,
                    isFolder = true
                )
            ).toInt()

            // Inside Novel, create chapters
            manuscriptDao.insertNode(
                ManuscriptNode(
                    name = "Prologue: Coordinates of the Abyss",
                    parentId = novelFolderId,
                    isFolder = false,
                    content = """The salt-wind over the docks of Vael-Anor always carried the smell of drying kelp and charcoal, but tonight it held a sharper scent: ozone. 

Soren adjusted his oilskin coat, his thumb tracing the brass edge of the astrolabe in his pocket. It was cold. Colder than a copper dial had any right to be. On the parchment map pinned to his drafting table, the ink of the Southern Reach was still wet, but the lines he’d drawn didn't align with the old imperial charts. They shouldn't have aligned. The sea didn't bend that way, and islands didn't simply drift five leagues to the east in the span of a single lunar cycle.

"You’re staring again, boy," old Master Charles muttered, his wooden leg clacking against the pine floorboards of the watchtower. "The sea doesn't like being measured. Map it too close, and it’ll swallow the compass."

"It's not just a drift, Charles," Soren said, his voice quiet. He picked up his drafting pen, the tip silver under the whale-oil lamp. "The reefs are rising. I saw the spires of the Red Silt city poking through the foam at low tide. They aren't in the imperial histories."

Charles stopped scraping his pipe. He looked at Soren's drawing—the clean, geometric spirals mapping the erratic tides—and his weathered face went pale. "Hide that, Soren. Now. If the Archon's Guard sees you plotting the shifting tides, they won't just burn your maps. They’ll take your fingers."

But Soren couldn't stop. The sea was writing a story on the shores of Vael-Anor, and he was the only cartographer alive who knew how to read the alphabet.""",
                    wordCount = 283
                )
            )

            manuscriptDao.insertNode(
                ManuscriptNode(
                    name = "Chapter 1: Ink and Iron",
                    parentId = novelFolderId,
                    isFolder = false,
                    content = """The guard-boats went out at dawn, their bronze-plated hulls carving through the mist like knives. Soren watched them from the high window of his workshop, his eyes burning from a sleepless night of mathematical calculations.

In his hands, he held a fragment of deep-sea glass—an irregular chunk of dark violet obsidian that hummed when held near copper. He had found it embedded in the keel of a fishing vessel that had barely limped back from the Great Shelf. The fishermen spoke of a sudden wall of black water that rose without wind, carrying with it a forest of floating white pine and stone ruins.

He placed the obsidian fragment onto his brass scale. 

"Weight: three ounces. Displace-volume: negligible. Yet it pulls the needle of my compass forty degrees off-true." He whispered, jotting down the values.

A heavy knock rattled his oak door. The lock clicked, then groaned. 

"Open in the name of the Archon's High Registry!" a voice boomed.

Soren's heart leaped. He swept the charts into his hollow desk leg, slid the violet glass into his inner pocket, and grabbed a decoy map of standard fishing routes. "Coming!" he called, his voice shaking slightly. 

He had to protect the charts. The coordinates of the shifting sea were more than heresy—they were a route to whatever lay beneath.""",
                    wordCount = 227
                )
            )

            // Inside Novel, create a folder for Outline and Lore
            val loreFolderId = manuscriptDao.insertNode(
                ManuscriptNode(
                    name = "Lore & World Building",
                    parentId = novelFolderId,
                    isFolder = true
                )
            ).toInt()

            manuscriptDao.insertNode(
                ManuscriptNode(
                    name = "The Law of Shifting Oceans",
                    parentId = loreFolderId,
                    isFolder = false,
                    content = """### Oceanography of the Shattered Reach
Unlike standard terrestrial worlds, the world of Vael-Anor is enveloped by a dynamic fluid crust. Islands are not anchored to continental plates; rather, they float upon thick, fibrous roots of giant deep-sea kelp forests known as the Great Silt Bed.

### Shifting Anomalies
- **Lunar Drift**: During a blood moon, the islands migrate up to 5 leagues, driven by thermal deep-water vents.
- **The Archon's Hegemony**: The ruling class controls the 'Astrolabe Prime', a massive clockwork lighthouse that projects artificial stable coordinates, allowing safe navigation—at a steep tax.""",
                    wordCount = 101
                )
            )
        }

        // Seed some initial characters if empty (associated explicitly with the novel)
        val currentCharacters = characterDao.getAllCharactersFlow().firstOrNull() ?: emptyList()
        if (currentCharacters.isEmpty() && novelFolderId != 0) {
            characterDao.insertCharacter(
                CharacterProfile(
                    name = "Soren Vance",
                    role = "Protagonist",
                    age = "23",
                    appearance = "Tall and lean, with ink-stained fingers and gray eyes. Wears a worn cartographer's oilskin coat and keeps his drafting glass tucked into a leather chest strap.",
                    backstory = "Orphaned during the Great Shift of 2012 (imperial reckoning). Apprenticed to Master Charles, the harbor cartographer. Soren secretly possesses the forbidden Gift of Shifting Vision, allowing him to perceive the subterranean patterns of the fluid sea.",
                    plotArc = "Soren begins as a passive apprentice trying to survive under the Archon's strict laws. As he uncovers the corruption of the Astrolabe Prime, he must decide whether to publish his secret charts and plunge Vael-Anor into revolution, or keep quiet to save his mentor.",
                    notes = "Strengths: Brilliant mathematician, high spatial memory, exceptionally loyal.\nWeaknesses: Impulsive when curious, lacks physical combat training, prone to chronic insomnia.",
                    avatarColor = 0xFF6366F1.toInt(),
                    novelId = novelFolderId
                )
            )

            characterDao.insertCharacter(
                CharacterProfile(
                    name = "Commander Draven Cross",
                    role = "Antagonist",
                    age = "42",
                    appearance = "Broad-shouldered, clad in the silver-and-blue plate armor of the Archon's Registry Guard. His left cheek bears a jagged white scar from sea-serpent obsidian shrapnel.",
                    backstory = "A decorated military officer who rose through the ranks by ruthlessly enforcing the Archon's navigation monopoly. Cross believes that the Shifting Sea is a chaotic demonic force, and only the Archon's absolute order prevents humanity's total extinction.",
                    plotArc = "Cross is hunting Soren down to seize the shifting coordinates map. He represents structural stability at the cost of personal liberty, and will stop at nothing to capture the 'heretic mapmaker'.",
                    notes = "Weapon of Choice: Clockwork sabre which hums with light-energy.\nQuotes: 'Order is the only bridge over the abyss, cartographer. Break the compass, and you break Vael-Anor.'",
                    avatarColor = 0xFFEF4444.toInt(),
                    novelId = novelFolderId
                )
            )

            characterDao.insertCharacter(
                CharacterProfile(
                    name = "Lyra Vance",
                    role = "Supporting",
                    age = "21",
                    appearance = "Athletic build, weathered bronze skin, with dark braided hair woven with copper wire. Often covered in grease and soot from her steam-skiff engines.",
                    backstory = "Soren's adoptive sister, an expert mechanical engineer who builds and repairs illegal speed boats (steam-skiffs) for the harbor smugglers.",
                    plotArc = "Lyra acts as Soren's escape plan and tactical support. She helps Soren navigate the shifting reefs in her customized skiff, 'The Copper Needle'.",
                    notes = "Talents: Fast repairs under fire, lockpicking, excellent pilot.\nGoals: Wants to escape the Archon's territory to find the legendary Free Floating Islands.",
                    avatarColor = 0xFF10B981.toInt(),
                    novelId = novelFolderId
                )
            )
        }

        // Seed some initial plot events and relationships if empty
        val currentEvents = storyEventDao.getAllEventsFlow().firstOrNull() ?: emptyList()
        if (currentEvents.isEmpty() && novelFolderId != 0) {
            val event1Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "Discovery of Shifting Silt",
                    description = "Soren analyzes maps of the Southern Reach and realizes the ocean bed itself is migrating in a mathematically predictable spiral.",
                    arcPhase = "Setup",
                    orderIndex = 1,
                    novelId = novelFolderId
                )
            ).toInt()

            val event2Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "The Harbor Workshop Raid",
                    description = "Commander Draven Cross leads a squadron of the Archon's High Registry Guard to raid Soren's workshop. Soren barely escapes with his life and the forbidden maps.",
                    arcPhase = "Inciting Incident",
                    orderIndex = 2,
                    novelId = novelFolderId
                )
            ).toInt()

            val event3Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "Escape via The Copper Needle",
                    description = "Lyra pilots her souped-up steam skiff through unstable reefs to outrun Cross's ironclad ships, validating Soren's calculations of reef shifts.",
                    arcPhase = "Rising Action",
                    orderIndex = 3,
                    novelId = novelFolderId
                )
            ).toInt()

            val event4Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "Infiltrating Astrolabe Prime",
                    description = "Soren and Lyra sneak into the heart of the clockwork lighthouse to expose how the Archon manipulates stable coordinates.",
                    arcPhase = "Climax",
                    orderIndex = 4,
                    novelId = novelFolderId
                )
            ).toInt()

            // Seed Relationships (Associated explicitly with the novel)
            val characters = characterDao.getCharactersByNovel(novelFolderId)
            val sorenId = characters.find { it.name.contains("Soren") }?.id ?: 0
            val crossId = characters.find { it.name.contains("Cross") }?.id ?: 0
            val lyraId = characters.find { it.name.contains("Lyra") }?.id ?: 0

            if (sorenId != 0 && crossId != 0) {
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = sorenId,
                        targetId = crossId,
                        isToEvent = false,
                        relationType = "Rival / Hunter",
                        description = "Cross hunts Soren to seize his shifting-coordinates map; Soren opposes Cross's tyranny.",
                        novelId = novelFolderId
                    )
                )
            }

            if (sorenId != 0 && lyraId != 0) {
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = sorenId,
                        targetId = lyraId,
                        isToEvent = false,
                        relationType = "Adoptive Sibling / Ally",
                        description = "Grew up together in Charles's workshop. Lyra provides the engines, Soren provides the navigation.",
                        novelId = novelFolderId
                    )
                )
            }

            if (sorenId != 0 && event1Id != 0) {
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = sorenId,
                        targetId = event1Id,
                        isToEvent = true,
                        relationType = "Main Investigator",
                        description = "Soren discovers the anomalous shifts during late-night map drafting.",
                        novelId = novelFolderId
                    )
                )
            }

            if (crossId != 0 && event2Id != 0) {
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = crossId,
                        targetId = event2Id,
                        isToEvent = true,
                        relationType = "Raiding Commander",
                        description = "Cross leads the attack on Master Charles's harbor watchtower.",
                        novelId = novelFolderId
                    )
                )
            }

            if (lyraId != 0 && event3Id != 0) {
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = lyraId,
                        targetId = event3Id,
                        isToEvent = true,
                        relationType = "Pilot",
                        description = "Lyra steers the skiff through high-tide shoals under heavy cannon fire.",
                        novelId = novelFolderId
                    )
                )
            }
        }

        // Always run association repair to fix any pre-existing records with null novelId
        repairMissingNovelAssociations()
    }
}
