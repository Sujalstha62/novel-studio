package com.example.data

import java.util.UUID

sealed class ProposedChange {
    abstract val changeId: String

    data class NewCharacter(
        override val changeId: String = UUID.randomUUID().toString(),
        val character: CharacterProfile
    ) : ProposedChange()

    data class UpdatedCharacter(
        override val changeId: String = UUID.randomUUID().toString(),
        val existingCharacter: CharacterProfile,
        val updatedCharacter: CharacterProfile,
        val diffSummary: String
    ) : ProposedChange()

    data class NewStoryEvent(
        override val changeId: String = UUID.randomUUID().toString(),
        val event: StoryEvent
    ) : ProposedChange()

    data class UpdatedStoryEvent(
        override val changeId: String = UUID.randomUUID().toString(),
        val existingEvent: StoryEvent,
        val updatedEvent: StoryEvent,
        val diffSummary: String
    ) : ProposedChange()

    data class NewRelationship(
        override val changeId: String = UUID.randomUUID().toString(),
        val relationship: CharacterRelationship,
        val sourceName: String,
        val targetName: String
    ) : ProposedChange()
}
