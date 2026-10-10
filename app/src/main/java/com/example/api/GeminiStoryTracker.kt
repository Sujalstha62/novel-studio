package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// --- Data Models for extracted story details ---

data class ExtractedCharacter(
    val name: String,
    val role: String = "Supporting", // Protagonist, Antagonist, Supporting
    val age: String = "",
    val appearance: String = "",
    val backstory: String = "",
    val plotArc: String = "",
    val notes: String = "",
    val avatarColorHex: String = "#6366F1"
)

data class ExtractedStoryEvent(
    val title: String,
    val description: String,
    val arcPhase: String = "Rising Action", // Exposition, Inciting Incident, Rising Action, Climax, Falling Action, Resolution
    val orderIndex: Int = 1
)

data class ExtractedRelationship(
    val sourceCharacter: String,
    val targetName: String,
    val isToEvent: Boolean = false,
    val relationType: String = "Ally", // Ally, Rival, Family, Enemy, Mentor, Love Interest, Present At
    val description: String = ""
)

data class ExtractedStoryData(
    val characters: List<ExtractedCharacter> = emptyList(),
    val plotEvents: List<ExtractedStoryEvent> = emptyList(),
    val relationships: List<ExtractedRelationship> = emptyList()
)

data class StoryTrackOutcome(
    val data: ExtractedStoryData,
    val requestId: Int,
    val geminiSuccess: Boolean,
    val usedLocalFallback: Boolean
)

class GeminiStoryTracker {
    private val TAG = "GeminiStoryTracker"

    suspend fun trackStory(
        text: String,
        chapterTitle: String,
        requestId: Int = RetrofitClient.nextRequestId()
    ): ExtractedStoryData = trackStoryWithDiagnostics(text, chapterTitle, requestId).data

    suspend fun trackStoryWithDiagnostics(
        text: String,
        chapterTitle: String,
        requestId: Int = RetrofitClient.nextRequestId()
    ): StoryTrackOutcome = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is missing or default placeholder. Using local smart fallback.")
            return@withContext StoryTrackOutcome(
                data = getLocalBackupStoryData(text, chapterTitle),
                requestId = requestId,
                geminiSuccess = false,
                usedLocalFallback = true
            )
        }

        val systemPrompt = """
            You are an advanced narrative intelligence agent. Your job is to analyze chapter text from a novel, automatically track all characters, map their dramatic relationships, and extract key plot milestones.
            
            Based on the provided text, extract:
            1. All characters mentioned (name, role as Protagonist/Antagonist/Supporting, age, appearance description, backstory, plot arc notes, and a Hex color code representing their aura/avatar, e.g., #EF4444 for Antagonist, #6366F1 for Protagonist).
            2. Significant plot milestones or storyline events (title, description, dramatic arc phase, and chronological sequence index).
            3. Dynamic relationship lines linking characters to other characters, or characters to storyline events.
            
            You must output a single JSON object with EXACTLY the following structure:
            {
              "characters": [
                {
                  "name": "Character Name",
                  "role": "Protagonist" or "Antagonist" or "Supporting",
                  "age": "Age or estimate",
                  "appearance": "Visual description",
                  "backstory": "Brief background",
                  "plotArc": "How their arc changes",
                  "notes": "General observations",
                  "avatarColorHex": "#HEXCODE"
                }
              ],
              "plotEvents": [
                {
                  "title": "Milestone Title",
                  "description": "What happens in this milestone",
                  "arcPhase": "Exposition" or "Inciting Incident" or "Rising Action" or "Climax" or "Falling Action" or "Resolution",
                  "orderIndex": 1
                }
              ],
              "relationships": [
                {
                  "sourceCharacter": "Character Name",
                  "targetName": "Target Character Name OR Target Event Title",
                  "isToEvent": false (true if connecting to a plotEvent, false if connecting to another character),
                  "relationType": "Ally" or "Rival" or "Family" or "Enemy" or "Mentor" or "Love Interest" or "Present At",
                  "description": "Brief description of the link"
                }
              ]
            }

            Only return the JSON object, no markdown wrappers like ```json and no trailing explanations.
        """.trimIndent()

        val userPrompt = """
            Chapter Title: $chapterTitle
            Chapter Content:
            "$text"
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.3f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.generateContentWithResilience(
                apiKey = apiKey,
                request = request,
                operationName = "STORY_ANALYSIS",
                manuscriptInputChars = text.length,
                requestId = requestId
            )
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (rawJson != null) {
                val cleanedJson = rawJson.trim().removeSurrounding("```json", "```").trim()
                Log.d(TAG, "Extracted Response JSON: $cleanedJson")
                val adapter = RetrofitClient.moshiInstance.adapter(ExtractedStoryData::class.java)
                val parsed = adapter.fromJson(cleanedJson)
                if (parsed != null) {
                    return@withContext StoryTrackOutcome(
                        data = parsed,
                        requestId = requestId,
                        geminiSuccess = true,
                        usedLocalFallback = false
                    )
                } else {
                    Log.w(TAG, "Parsed null story data from Gemini JSON; falling back to local story tracker.")
                    return@withContext StoryTrackOutcome(
                        data = getLocalBackupStoryData(text, chapterTitle),
                        requestId = requestId,
                        geminiSuccess = false,
                        usedLocalFallback = true
                    )
                }
            } else {
                Log.w(TAG, "Empty content returned from Gemini; falling back to local story tracker.")
                return@withContext StoryTrackOutcome(
                    data = getLocalBackupStoryData(text, chapterTitle),
                    requestId = requestId,
                    geminiSuccess = false,
                    usedLocalFallback = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini story extraction temporarily unavailable (${e.message}); falling back to local story tracker.")
            return@withContext StoryTrackOutcome(
                data = getLocalBackupStoryData(text, chapterTitle),
                requestId = requestId,
                geminiSuccess = false,
                usedLocalFallback = true
            )
        }
    }

    /**
     * Highly advanced local backup story tracking. Extracts potential characters using regex name capitalizations,
     * structures events based on the chapter, and generates connections.
     */
    private fun getLocalBackupStoryData(text: String, chapterTitle: String): ExtractedStoryData {
        if (text.isBlank()) return ExtractedStoryData()

        val characters = mutableListOf<ExtractedCharacter>()
        val plotEvents = mutableListOf<ExtractedStoryEvent>()
        val relationships = mutableListOf<ExtractedRelationship>()

        // 1. Detect Names Heuristically (Capitalized words that aren't common stop words)
        val stopWords = setOf(
            "The", "A", "An", "In", "On", "At", "By", "For", "With", "About", "Against", "Between", "Into", "Through", "During", 
            "Before", "After", "Above", "Below", "To", "From", "Up", "Down", "In", "Out", "Off", "Over", "Under", "Again", "Further", 
            "Then", "Once", "Here", "There", "When", "Where", "Why", "How", "All", "Any", "Both", "Each", "Few", "More", "Most", 
            "Other", "Some", "Such", "No", "Nor", "Not", "Only", "Own", "Same", "So", "Than", "Too", "Very", "S", "T", "Can", "Will", 
            "Just", "Don", "Should", "Now", "He", "She", "It", "They", "But", "And", "Or", "If", "Because", "As", "Until", "While", 
            "Of", "His", "Her", "Their", "My", "Your", "Its", "This", "That", "These", "Those", "Am", "Is", "Are", "Was", "Were", "Be", 
            "Been", "Being", "Have", "Has", "Had", "Having", "Do", "Does", "Did", "Doing", "I", "You", "We", "They", "Chapter", "Prologue"
        )

        val words = text.split(Regex("[^a-zA-Z]+")).filter { it.length > 2 }
        val candidateNames = mutableSetOf<String>()

        for (i in 0 until words.size) {
            val word = words[i]
            if (word.isNotEmpty() && word[0].isUpperCase() && !stopWords.contains(word)) {
                // Check if it's a double-name like "Soren Vance"
                if (i + 1 < words.size) {
                    val nextWord = words[i + 1]
                    if (nextWord.isNotEmpty() && nextWord[0].isUpperCase() && !stopWords.contains(nextWord)) {
                        candidateNames.add("$word $nextWord")
                    } else {
                        candidateNames.add(word)
                    }
                } else {
                    candidateNames.add(word)
                }
            }
        }

        // Clean double-names from being counted twice (e.g., "Soren" and "Soren Vance")
        val finalNames = candidateNames.filter { name ->
            if (name.contains(" ")) true
            else candidateNames.none { other -> other != name && other.startsWith(name) }
        }.take(3) // Limit to 3 most relevant names

        // Create character profiles for detected names
        finalNames.forEachIndexed { index, name ->
            val role = when (index) {
                0 -> "Protagonist"
                1 -> "Antagonist"
                else -> "Supporting"
            }
            val colorHex = when (role) {
                "Protagonist" -> "#6366F1"
                "Antagonist" -> "#EF4444"
                else -> "#10B981"
            }
            characters.add(
                ExtractedCharacter(
                    name = name,
                    role = role,
                    age = if (index == 0) "20s" else "Unknown",
                    appearance = "A key figure mentioned in $chapterTitle.",
                    backstory = "First introduced in the draft of $chapterTitle.",
                    plotArc = "Shaped by the events of $chapterTitle.",
                    notes = "Automatically tracked by AI Story Agent.",
                    avatarColorHex = colorHex
                )
            )
        }

        // 2. Generate Plot Milestone Event from chapter details
        val eventTitle = if (chapterTitle.isNotBlank()) "Milestone: $chapterTitle" else "Plot Event"
        val eventDescription = if (text.length > 150) text.take(150).trim() + "..." else text
        plotEvents.add(
            ExtractedStoryEvent(
                title = eventTitle,
                description = "Events taking place in the chapter: $eventDescription",
                arcPhase = "Rising Action",
                orderIndex = 1
            )
        )

        // 3. Connect Characters and Events
        if (characters.isNotEmpty()) {
            val primaryChar = characters.first().name
            // Link character to event
            relationships.add(
                ExtractedRelationship(
                    sourceCharacter = primaryChar,
                    targetName = eventTitle,
                    isToEvent = true,
                    relationType = "Present At",
                    description = "Participates in the events of this chapter."
                )
            )

            // Link secondary characters
            if (characters.size > 1) {
                for (i in 1 until characters.size) {
                    relationships.add(
                        ExtractedRelationship(
                            sourceCharacter = primaryChar,
                            targetName = characters[i].name,
                            isToEvent = false,
                            relationType = if (characters[i].role == "Antagonist") "Rival" else "Ally",
                            description = "Interacts in $chapterTitle."
                        )
                    )
                }
            }
        }

        return ExtractedStoryData(characters, plotEvents, relationships)
    }
}
