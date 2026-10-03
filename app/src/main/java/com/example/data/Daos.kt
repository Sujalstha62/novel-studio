package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ManuscriptDao {
    @Query("SELECT * FROM manuscript_nodes ORDER BY isFolder DESC, name ASC")
    fun getAllNodesFlow(): Flow<List<ManuscriptNode>>

    @Query("SELECT * FROM manuscript_nodes WHERE id = :id")
    fun getNodeByIdFlow(id: Int): Flow<ManuscriptNode?>

    @Query("SELECT * FROM manuscript_nodes WHERE id = :id")
    suspend fun getNodeById(id: Int): ManuscriptNode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: ManuscriptNode): Long

    @Update
    suspend fun updateNode(node: ManuscriptNode)

    @Delete
    suspend fun deleteNode(node: ManuscriptNode)

    @Query("DELETE FROM manuscript_nodes WHERE id = :id")
    suspend fun deleteNodeById(id: Int)
}

@Dao
interface CharacterDao {
    @Query("SELECT * FROM character_profiles ORDER BY name ASC")
    fun getAllCharactersFlow(): Flow<List<CharacterProfile>>

    @Query("SELECT * FROM character_profiles WHERE id = :id")
    suspend fun getCharacterById(id: Int): CharacterProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterProfile): Long

    @Delete
    suspend fun deleteCharacter(character: CharacterProfile)
}

@Dao
interface StoryEventDao {
    @Query("SELECT * FROM story_events ORDER BY orderIndex ASC")
    fun getAllEventsFlow(): Flow<List<StoryEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: StoryEvent): Long

    @Update
    suspend fun updateEvent(event: StoryEvent)

    @Delete
    suspend fun deleteEvent(event: StoryEvent)
}

@Dao
interface CharacterRelationshipDao {
    @Query("SELECT * FROM character_relationships")
    fun getAllRelationshipsFlow(): Flow<List<CharacterRelationship>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationship(relationship: CharacterRelationship)

    @Delete
    suspend fun deleteRelationship(relationship: CharacterRelationship)

    @Query("DELETE FROM character_relationships WHERE sourceCharacterId = :charId OR (targetId = :charId AND isToEvent = 0)")
    suspend fun deleteRelationshipsForCharacter(charId: Int)

    @Query("DELETE FROM character_relationships WHERE targetId = :eventId AND isToEvent = 1")
    suspend fun deleteRelationshipsForEvent(eventId: Int)
}

@Dao
interface ManuscriptCommitDao {
    @Query("SELECT * FROM manuscript_commits ORDER BY timestamp DESC")
    fun getAllCommitsFlow(): Flow<List<ManuscriptCommit>>

    @Query("SELECT * FROM manuscript_commits WHERE nodeId = :nodeId ORDER BY timestamp DESC")
    fun getCommitsForNodeFlow(nodeId: Int): Flow<List<ManuscriptCommit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommit(commit: ManuscriptCommit)

    @Query("DELETE FROM manuscript_commits WHERE id = :commitId")
    suspend fun deleteCommitById(commitId: Int)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM writing_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<WritingSettings?>

    @Query("SELECT * FROM writing_settings WHERE id = 1")
    suspend fun getSettings(): WritingSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: WritingSettings)
}
