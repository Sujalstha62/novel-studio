package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class NovelRepository(
    private val manuscriptDao: ManuscriptDao,
    private val characterDao: CharacterDao,
    private val settingsDao: SettingsDao,
    private val storyEventDao: StoryEventDao,
    private val characterRelationshipDao: CharacterRelationshipDao,
    private val manuscriptCommitDao: ManuscriptCommitDao
) {
    val allNodes: Flow<List<ManuscriptNode>> = manuscriptDao.getAllNodesFlow()
    val allCharacters: Flow<List<CharacterProfile>> = characterDao.getAllCharactersFlow()
    val settings: Flow<WritingSettings?> = settingsDao.getSettingsFlow()
    val allEvents: Flow<List<StoryEvent>> = storyEventDao.getAllEventsFlow()
    val allRelationships: Flow<List<CharacterRelationship>> = characterRelationshipDao.getAllRelationshipsFlow()
    val allCommits: Flow<List<ManuscriptCommit>> = manuscriptCommitDao.getAllCommitsFlow()

    fun getNodeByIdFlow(id: Int): Flow<ManuscriptNode?> = manuscriptDao.getNodeByIdFlow(id)
    suspend fun getNodeById(id: Int): ManuscriptNode? = manuscriptDao.getNodeById(id)

    suspend fun insertNode(node: ManuscriptNode): Long = manuscriptDao.insertNode(node)
    suspend fun updateNode(node: ManuscriptNode) = manuscriptDao.updateNode(node)
    suspend fun deleteNode(node: ManuscriptNode) = manuscriptDao.deleteNode(node)
    suspend fun deleteNodeById(id: Int) = manuscriptDao.deleteNodeById(id)

    suspend fun getCharacterById(id: Int): CharacterProfile? = characterDao.getCharacterById(id)
    suspend fun insertCharacter(character: CharacterProfile): Long = characterDao.insertCharacter(character)
    suspend fun deleteCharacter(character: CharacterProfile) {
        // clean up relationships
        characterRelationshipDao.deleteRelationshipsForCharacter(character.id)
        characterDao.deleteCharacter(character)
    }

    suspend fun getSettings(): WritingSettings? = settingsDao.getSettings()
    suspend fun insertOrUpdateSettings(settings: WritingSettings) = settingsDao.insertOrUpdateSettings(settings)

    // Commit history methods
    fun getCommitsForNodeFlow(nodeId: Int): Flow<List<ManuscriptCommit>> = manuscriptCommitDao.getCommitsForNodeFlow(nodeId)
    suspend fun insertCommit(commit: ManuscriptCommit) = manuscriptCommitDao.insertCommit(commit)
    suspend fun deleteCommitById(commitId: Int) = manuscriptCommitDao.deleteCommitById(commitId)

    // Story Arc Events
    suspend fun insertEvent(event: StoryEvent): Long = storyEventDao.insertEvent(event)
    suspend fun updateEvent(event: StoryEvent) = storyEventDao.updateEvent(event)
    suspend fun deleteEvent(event: StoryEvent) {
        characterRelationshipDao.deleteRelationshipsForEvent(event.id)
        storyEventDao.deleteEvent(event)
    }

    // Relationships
    suspend fun insertRelationship(relationship: CharacterRelationship) = characterRelationshipDao.insertRelationship(relationship)
    suspend fun deleteRelationship(relationship: CharacterRelationship) = characterRelationshipDao.deleteRelationship(relationship)
    suspend fun deleteRelationshipsForCharacter(charId: Int) = characterRelationshipDao.deleteRelationshipsForCharacter(charId)

    suspend fun seedInitialDataIfEmpty() {
        // Seed default settings if not exists
        if (settingsDao.getSettings() == null) {
            settingsDao.insertOrUpdateSettings(WritingSettings())
        }

        // Seed some initial chapters and folders if empty
        val currentNodes = manuscriptDao.getAllNodesFlow().firstOrNull() ?: emptyList()
        if (currentNodes.isEmpty()) {
            // Create a main Novel Folder
            val novelFolderId = manuscriptDao.insertNode(
                ManuscriptNode(
                    name = "The Lost Cartographer",
                    isFolder = true
                )
            ).toInt()

            // Inside Novel, create chapters
            val prologueId = manuscriptDao.insertNode(
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

            val chapter1Id = manuscriptDao.insertNode(
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

            // Outside Novel, create a folder for Outline and Lore
            val loreFolderId = manuscriptDao.insertNode(
                ManuscriptNode(
                    name = "Lore & World Building",
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

        // Seed some initial characters if empty
        val currentCharacters = characterDao.getAllCharactersFlow().firstOrNull() ?: emptyList()
        if (currentCharacters.isEmpty()) {
            characterDao.insertCharacter(
                CharacterProfile(
                    name = "Soren Vance",
                    role = "Protagonist",
                    age = "23",
                    appearance = "Tall and lean, with ink-stained fingers and gray eyes. Wears a worn cartographer's oilskin coat and keeps his drafting glass tucked into a leather chest strap.",
                    backstory = "Orphaned during the Great Shift of 2012 (imperial reckoning). Apprenticed to Master Charles, the harbor cartographer. Soren secretly possesses the forbidden Gift of Shifting Vision, allowing him to perceive the subterranean patterns of the fluid sea.",
                    plotArc = "Soren begins as a passive apprentice trying to survive under the Archon's strict laws. As he uncovers the corruption of the Astrolabe Prime, he must decide whether to publish his secret charts and plunge Vael-Anor into revolution, or keep quiet to save his mentor.",
                    notes = "Strengths: Brilliant mathematician, high spatial memory, exceptionally loyal.\nWeaknesses: Impulsive when curious, lacks physical combat training, prone to chronic insomnia."
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
                    notes = "Weapon of Choice: Clockwork sabre which hums with light-energy.\nQuotes: 'Order is the only bridge over the abyss, cartographer. Break the compass, and you break Vael-Anor.'"
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
                    notes = "Talents: Fast repairs under fire, lockpicking, excellent pilot.\nGoals: Wants to escape the Archon's territory to find the legendary Free Floating Islands."
                )
            )
        }

        // Seed some initial plot events and relationships if empty
        val currentEvents = storyEventDao.getAllEventsFlow().firstOrNull() ?: emptyList()
        if (currentEvents.isEmpty()) {
            val event1Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "Discovery of Shifting Silt",
                    description = "Soren analyzes maps of the Southern Reach and realizes the ocean bed itself is migrating in a mathematically predictable spiral.",
                    arcPhase = "Setup",
                    orderIndex = 1
                )
            ).toInt()

            val event2Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "The Harbor Workshop Raid",
                    description = "Commander Draven Cross leads a squadron of the Archon's High Registry Guard to raid Soren's workshop. Soren barely escapes with his life and the forbidden maps.",
                    arcPhase = "Inciting Incident",
                    orderIndex = 2
                )
            ).toInt()

            val event3Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "Escape via The Copper Needle",
                    description = "Lyra pilots her souped-up steam skiff through unstable reefs to outrun Cross's ironclad ships, validating Soren's calculations of reef shifts.",
                    arcPhase = "Rising Action",
                    orderIndex = 3
                )
            ).toInt()

            val event4Id = storyEventDao.insertEvent(
                StoryEvent(
                    title = "Infiltrating Astrolabe Prime",
                    description = "Soren and Lyra sneak into the heart of the clockwork lighthouse to expose how the Archon manipulates stable coordinates.",
                    arcPhase = "Climax",
                    orderIndex = 4
                )
            ).toInt()

            // Seed Relationships (ER Connections)
            val characters = characterDao.getAllCharactersFlow().firstOrNull() ?: emptyList()
            val sorenId = characters.find { it.name.contains("Soren") }?.id ?: 0
            val crossId = characters.find { it.name.contains("Cross") }?.id ?: 0
            val lyraId = characters.find { it.name.contains("Lyra") }?.id ?: 0

            if (sorenId != 0 && crossId != 0) {
                // Soren vs Cross relationship
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = sorenId,
                        targetId = crossId,
                        isToEvent = false,
                        relationType = "Rival / Hunter",
                        description = "Cross hunts Soren to seize his shifting-coordinates map; Soren opposes Cross's tyranny."
                    )
                )
            }

            if (sorenId != 0 && lyraId != 0) {
                // Soren & Lyra sibling relationship
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = sorenId,
                        targetId = lyraId,
                        isToEvent = false,
                        relationType = "Adoptive Sibling / Ally",
                        description = "Grew up together in Charles's workshop. Lyra provides the engines, Soren provides the navigation."
                    )
                )
            }

            if (sorenId != 0 && event1Id != 0) {
                // Soren present at event 1
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = sorenId,
                        targetId = event1Id,
                        isToEvent = true,
                        relationType = "Main Investigator",
                        description = "Soren discovers the anomalous shifts during late-night map drafting."
                    )
                )
            }

            if (crossId != 0 && event2Id != 0) {
                // Cross triggers event 2
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = crossId,
                        targetId = event2Id,
                        isToEvent = true,
                        relationType = "Raiding Commander",
                        description = "Cross leads the attack on Master Charles's harbor watchtower."
                    )
                )
            }

            if (lyraId != 0 && event3Id != 0) {
                // Lyra drives event 3
                characterRelationshipDao.insertRelationship(
                    CharacterRelationship(
                        sourceCharacterId = lyraId,
                        targetId = event3Id,
                        isToEvent = true,
                        relationType = "Pilot",
                        description = "Lyra steers the skiff through high-tide shoals under heavy cannon fire."
                    )
                )
            }
        }
    }
}
