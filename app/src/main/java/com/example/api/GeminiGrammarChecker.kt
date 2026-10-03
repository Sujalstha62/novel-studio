package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Data Models for Grammar Checker ---

data class GrammarSuggestion(
    val originalText: String,
    val suggestedText: String,
    val explanation: String,
    val severity: String, // "Critical" or "Style" or "Typos" or "Punctuation"
    val startOffset: Int = -1,
    val endOffset: Int = -1
)

// --- Request and Response Models for direct Gemini REST ---

data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: Content?
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client Provider ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val moshiInstance: Moshi get() = moshi
}

// --- High level grammar checker class ---

class GeminiGrammarChecker {
    private val TAG = "GeminiGrammarChecker"

    suspend fun checkGrammar(text: String): List<GrammarSuggestion> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or default placeholder!")
            return@withContext getLocalBackupSuggestions(text)
        }

        val systemPrompt = """
            You are an elite novel editor, proofreader, and creative writing style coach.
            Analyze the user's text for grammar, spelling, punctuation, pacing, word choice, and stylistic errors.
            You must output a JSON array of objects. Each object represents a single suggestion and must have exactly the following keys:
            - "originalText": The exact word, phrase, or sentence in the input text that needs correction.
            - "suggestedText": Your proposed edit or improvement.
            - "startOffset": The 0-based character start index of the exact originalText within the input text.
            - "endOffset": The 0-based character end index (exclusive) of the exact originalText within the input text.
            - "explanation": A friendly, helpful, literary explanation of why this change is suggested (e.g., 'Eliminates passive voice', 'Corrects typo', 'Enriches vocabulary').
            - "severity": Must be exactly one of: "Critical", "Style", "Punctuation", "Typos".

            Keep the tone warm, collaborative, and professional, as if mentoring an aspiring novelist.
            Only return the JSON array, no markdown wrappers like ```json or trailing explanations.
        """.trimIndent()

        val userPrompt = "Check this text from my manuscript:\n\n\"$text\""

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (rawJson != null) {
                val cleanedJson = rawJson.trim().removeSurrounding("```json", "```").trim()
                Log.d(TAG, "Response JSON: $cleanedJson")
                val type = Types.newParameterizedType(List::class.java, GrammarSuggestion::class.java)
                val adapter = RetrofitClient.moshiInstance.adapter<List<GrammarSuggestion>>(type)
                val parsed = adapter.fromJson(cleanedJson) ?: emptyList()

                return@withContext validateSuggestions(text, parsed)
            } else {
                Log.w(TAG, "No response candidates or empty content.")
                return@withContext getLocalBackupSuggestions(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
            return@withContext getLocalBackupSuggestions(text)
        }
    }

    /**
     * Strictly validates grammar suggestions against the manuscript text.
     * Any suggestion with missing, invalid, out-of-bounds, or mismatched startOffset/endOffset
     * is discarded rather than guessing an occurrence via indexOf() or replaceFirst().
     */
    fun validateSuggestions(text: String, candidates: List<GrammarSuggestion>): List<GrammarSuggestion> {
        return candidates.filter { suggestion ->
            val orig = suggestion.originalText
            val sOffset = suggestion.startOffset
            val eOffset = suggestion.endOffset

            val isValid = orig.isNotEmpty() &&
                sOffset >= 0 &&
                eOffset <= text.length &&
                sOffset < eOffset &&
                text.substring(sOffset, eOffset) == orig

            if (!isValid) {
                Log.w(
                    TAG,
                    "Discarding invalid grammar suggestion for '$orig': range [$sOffset, $eOffset] is invalid or does not match manuscript text."
                )
            }
            isValid
        }
    }

    /**
     * Fallback local regex-based and heuristic proofreader if offline or API key is not yet set.
     * Computes exact startOffset and endOffset for each detected issue.
     */
    private fun getLocalBackupSuggestions(text: String): List<GrammarSuggestion> {
        val suggestions = mutableListOf<GrammarSuggestion>()

        // 1. Passive voice detection (e.g., "was *ed")
        val passiveRegex = Regex(
            "\\b(am|is|are|was|were|be|been|being)\\s+([a-z]+ed|written|taken|seen|known|held|perceived|drawn)\\b",
            RegexOption.IGNORE_CASE
        )
        passiveRegex.findAll(text).forEach { match ->
            suggestions.add(
                GrammarSuggestion(
                    originalText = match.value,
                    suggestedText = "[Activate verb]",
                    explanation = "Passive voice detected. Try switching to an active verb to make the prose punchier and enhance the pacing.",
                    severity = "Style",
                    startOffset = match.range.first,
                    endOffset = match.range.last + 1
                )
            )
        }

        // 2. Cliché / Filter words (e.g. "began to", "started to", "felt", "saw")
        val filters = listOf(
            "began to" to "just do the action directly (e.g., instead of 'began to run', use 'ran')",
            "started to" to "use the direct past tense",
            "felt" to "show the physical sensation instead of telling (e.g., instead of 'he felt cold', use 'his fingers numbed')",
            "wondered if" to "reframe as an internal thought",
            "suddenly" to "usually dilutes tension; let the action happen abruptly on its own"
        )
        for ((filterWord, tip) in filters) {
            val filterRegex = Regex("\\b${Regex.escape(filterWord)}\\b", RegexOption.IGNORE_CASE)
            filterRegex.findAll(text).forEach { match ->
                suggestions.add(
                    GrammarSuggestion(
                        originalText = match.value,
                        suggestedText = "[Filter word]",
                        explanation = "Filter word/cliché detected: '$filterWord'. $tip to pull the reader closer to the action.",
                        severity = "Style",
                        startOffset = match.range.first,
                        endOffset = match.range.last + 1
                    )
                )
            }
        }

        // 3. Simple typos check (common writing slip-ups)
        val typoMap = mapOf(
            "recieve" to "receive",
            "seperate" to "separate",
            "comming" to "coming",
            "untill" to "until",
            "its a" to "it's a",
            "dont" to "don't",
            "cant" to "can't",
            "there own" to "their own"
        )
        for ((wrong, right) in typoMap) {
            val typoRegex = Regex("\\b${Regex.escape(wrong)}\\b", RegexOption.IGNORE_CASE)
            typoRegex.findAll(text).forEach { match ->
                suggestions.add(
                    GrammarSuggestion(
                        originalText = match.value,
                        suggestedText = right,
                        explanation = "Common spelling/typo correction.",
                        severity = "Typos",
                        startOffset = match.range.first,
                        endOffset = match.range.last + 1
                    )
                )
            }
        }

        return validateSuggestions(text, suggestions)
    }
}
