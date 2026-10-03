package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manuscript_nodes")
data class ManuscriptNode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val parentId: Int? = null,
    val isFolder: Boolean,
    val content: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val wordCount: Int = 0
)

@Entity(tableName = "character_profiles")
data class CharacterProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: String = "Protagonist",
    val age: String = "",
    val appearance: String = "",
    val backstory: String = "",
    val plotArc: String = "",
    val notes: String = "",
    val avatarColor: Int = 0xFF6200EE.toInt(),
    val novelId: Int? = null
)

@Entity(tableName = "writing_settings")
data class WritingSettings(
    @PrimaryKey val id: Int = 1,
    val selectedTexture: String = "Parchment",
    val fontSize: Int = 18,
    val isDistractionFree: Boolean = false,
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "story_events")
data class StoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val arcPhase: String = "Rising Action", // "Setup", "Inciting Incident", "Rising Action", "Climax", "Resolution"
    val orderIndex: Int = 0,
    val novelId: Int? = null
)

@Entity(tableName = "character_relationships")
data class CharacterRelationship(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceCharacterId: Int,
    val targetId: Int, // Represents another character ID or an event ID
    val isToEvent: Boolean = false, // If true, targetId matches a StoryEvent, else CharacterProfile
    val relationType: String, // e.g. "Rival", "Sovereign", "Sibling", "Present at Event"
    val description: String = "",
    val novelId: Int? = null
)

@Entity(tableName = "manuscript_commits")
data class ManuscriptCommit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nodeId: Int,
    val commitHash: String, // simulated hash like "e49f2c3"
    val commitMessage: String,
    val contentSnapshot: String,
    val timestamp: Long = System.currentTimeMillis()
)
