package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client Provider ---

object RetrofitClient {
    private const val TAG = "RetrofitClient"
    const val TIMING_TAG = "AI_TIMING"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private val requestIdCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun nextRequestId(): Int = requestIdCounter.incrementAndGet()

    private val CANDIDATE_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite-preview"
    )

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

    private fun estimateInputChars(request: GenerateContentRequest): Int {
        val contentsChars = request.contents.sumOf { content ->
            content.parts.sumOf { it.text.length }
        }
        val systemChars = request.systemInstruction?.parts?.sumOf { it.text.length } ?: 0
        return contentsChars + systemChars
    }

    private fun estimateResponseChars(response: GenerateContentResponse): Int {
        return response.candidates?.sumOf { candidate ->
            candidate.content?.parts?.sumOf { it.text.length } ?: 0
        } ?: 0
    }

    /**
     * Calls Gemini `generateContent` with automatic exponential-backoff retry and model fallback
     * when the upstream model returns transient `HTTP 503` (Service Unavailable / Overloaded),
     * `HTTP 429` (Rate Limit), or `HTTP 500`/`502`/`504`.
     */
    suspend fun generateContentWithResilience(
        apiKey: String,
        request: GenerateContentRequest,
        operationName: String = "UNKNOWN",
        manuscriptInputChars: Int? = null,
        requestId: Int = nextRequestId()
    ): GenerateContentResponse {
        var lastException: Exception? = null
        val payloadInputChars = estimateInputChars(request)
        val loggedInputChars = manuscriptInputChars ?: payloadInputChars

        for ((index, modelName) in CANDIDATE_MODELS.withIndex()) {
            val attemptNumber = index + 1
            val isRetry = index > 0
            val attemptType = if (isRetry) "RETRY" else "INITIAL"
            val startTimestampMs = System.currentTimeMillis()

            Log.i(
                TIMING_TAG,
                "AI_TIMING operation=$operationName request=$requestId event=START " +
                    "startTimestampMs=$startTimestampMs model=$modelName attempt=$attemptNumber " +
                    "attemptType=$attemptType isRetry=$isRetry inputChars=$loggedInputChars payloadChars=$payloadInputChars"
            )

            try {
                val response = service.generateContent(model = modelName, apiKey = apiKey, request = request)
                val endTimestampMs = System.currentTimeMillis()
                val elapsedMs = endTimestampMs - startTimestampMs
                val responseChars = estimateResponseChars(response)

                Log.i(
                    TIMING_TAG,
                    "AI_TIMING operation=$operationName request=$requestId event=END " +
                        "startTimestampMs=$startTimestampMs endTimestampMs=$endTimestampMs elapsedMs=$elapsedMs " +
                        "success=true httpStatus=200 model=$modelName attempt=$attemptNumber " +
                        "attemptType=$attemptType isRetry=$isRetry inputChars=$loggedInputChars " +
                        "payloadChars=$payloadInputChars responseChars=$responseChars"
                )
                return response
            } catch (e: HttpException) {
                lastException = e
                val endTimestampMs = System.currentTimeMillis()
                val elapsedMs = endTimestampMs - startTimestampMs
                val code = e.code()
                val isTransient = code == 503 || code == 429 || code == 500 || code == 502 || code == 504

                Log.w(
                    TIMING_TAG,
                    "AI_TIMING operation=$operationName request=$requestId event=END " +
                        "startTimestampMs=$startTimestampMs endTimestampMs=$endTimestampMs elapsedMs=$elapsedMs " +
                        "success=false httpStatus=$code model=$modelName attempt=$attemptNumber " +
                        "attemptType=$attemptType isRetry=$isRetry inputChars=$loggedInputChars " +
                        "payloadChars=$payloadInputChars responseChars=0 errorType=HttpException"
                )

                if (isTransient && index < CANDIDATE_MODELS.lastIndex) {
                    val delayMs = (600L * (index + 1))
                    Log.w(
                        TAG,
                        "Model '$modelName' returned HTTP $code (transient overload/unavailable); retrying in ${delayMs}ms with fallback model '${CANDIDATE_MODELS[index + 1]}'."
                    )
                    delay(delayMs)
                    continue
                }
                throw e
            } catch (e: IOException) {
                lastException = e
                val endTimestampMs = System.currentTimeMillis()
                val elapsedMs = endTimestampMs - startTimestampMs

                Log.w(
                    TIMING_TAG,
                    "AI_TIMING operation=$operationName request=$requestId event=END " +
                        "startTimestampMs=$startTimestampMs endTimestampMs=$endTimestampMs elapsedMs=$elapsedMs " +
                        "success=false httpStatus=IO_ERROR model=$modelName attempt=$attemptNumber " +
                        "attemptType=$attemptType isRetry=$isRetry inputChars=$loggedInputChars " +
                        "payloadChars=$payloadInputChars responseChars=0 errorType=${e.javaClass.simpleName}"
                )

                if (index < CANDIDATE_MODELS.lastIndex) {
                    val delayMs = (600L * (index + 1))
                    Log.w(
                        TAG,
                        "Network I/O issue calling '$modelName' (${e.message}); retrying in ${delayMs}ms with '${CANDIDATE_MODELS[index + 1]}'."
                    )
                    delay(delayMs)
                    continue
                }
                throw e
            } catch (e: Exception) {
                val endTimestampMs = System.currentTimeMillis()
                val elapsedMs = endTimestampMs - startTimestampMs
                Log.w(
                    TIMING_TAG,
                    "AI_TIMING operation=$operationName request=$requestId event=END " +
                        "startTimestampMs=$startTimestampMs endTimestampMs=$endTimestampMs elapsedMs=$elapsedMs " +
                        "success=false httpStatus=UNKNOWN model=$modelName attempt=$attemptNumber " +
                        "attemptType=$attemptType isRetry=$isRetry inputChars=$loggedInputChars " +
                        "payloadChars=$payloadInputChars responseChars=0 errorType=${e.javaClass.simpleName}"
                )
                throw e
            }
        }
        throw lastException ?: IllegalStateException("Gemini API request failed across all candidate models.")
    }
}

// --- High level grammar checker class ---

data class GrammarCheckOutcome(
    val suggestions: List<GrammarSuggestion>,
    val requestId: Int,
    val geminiSuccess: Boolean,
    val usedLocalFallback: Boolean
)

class GeminiGrammarChecker {
    private val TAG = "GeminiGrammarChecker"

    suspend fun checkGrammar(
        text: String,
        requestId: Int = RetrofitClient.nextRequestId()
    ): List<GrammarSuggestion> = checkGrammarWithDiagnostics(text, requestId).suggestions

    suspend fun checkGrammarWithDiagnostics(
        text: String,
        requestId: Int = RetrofitClient.nextRequestId()
    ): GrammarCheckOutcome = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is missing or default placeholder; using local proofreader.")
            return@withContext GrammarCheckOutcome(
                suggestions = getLocalBackupSuggestions(text),
                requestId = requestId,
                geminiSuccess = false,
                usedLocalFallback = true
            )
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
            val response = RetrofitClient.generateContentWithResilience(
                apiKey = apiKey,
                request = request,
                operationName = "GRAMMAR",
                manuscriptInputChars = text.length,
                requestId = requestId
            )
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (rawJson != null) {
                val cleanedJson = rawJson.trim().removeSurrounding("```json", "```").trim()
                Log.d(TAG, "Response JSON: $cleanedJson")
                val type = Types.newParameterizedType(List::class.java, GrammarSuggestion::class.java)
                val adapter = RetrofitClient.moshiInstance.adapter<List<GrammarSuggestion>>(type)
                val parsed = adapter.fromJson(cleanedJson)
                if (parsed != null) {
                    val validated = validateSuggestions(text, parsed)
                    Log.i(
                        TAG,
                        "Grammar pipeline counts: API suggestions count=${parsed.size} -> parsed suggestions count=${parsed.size} -> validated suggestions count=${validated.size}"
                    )
                    return@withContext GrammarCheckOutcome(
                        suggestions = validated,
                        requestId = requestId,
                        geminiSuccess = true,
                        usedLocalFallback = false
                    )
                } else {
                    Log.w(TAG, "Parsed null suggestion list from Gemini JSON; falling back to local proofreader.")
                    return@withContext GrammarCheckOutcome(
                        suggestions = getLocalBackupSuggestions(text),
                        requestId = requestId,
                        geminiSuccess = false,
                        usedLocalFallback = true
                    )
                }
            } else {
                Log.w(TAG, "No response candidates or empty content; falling back to local proofreader.")
                return@withContext GrammarCheckOutcome(
                    suggestions = getLocalBackupSuggestions(text),
                    requestId = requestId,
                    geminiSuccess = false,
                    usedLocalFallback = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API temporarily unavailable (${e.message}); falling back to local proofreader.")
            return@withContext GrammarCheckOutcome(
                suggestions = getLocalBackupSuggestions(text),
                requestId = requestId,
                geminiSuccess = false,
                usedLocalFallback = true
            )
        }
    }

    /**
     * Strictly validates grammar suggestions against the manuscript text.
     * Supports both 0-based offsets relative to [text] and offsets shifted by +1 (when Gemini
     * counts the opening quote in `"$text"`), or resolves a unique exact match in [text] when
     * the model's character count is slightly off.
     */
    fun validateSuggestions(text: String, candidates: List<GrammarSuggestion>): List<GrammarSuggestion> {
        return candidates.mapNotNull { suggestion ->
            val orig = suggestion.originalText
            if (orig.isEmpty()) {
                Log.w(TAG, "Discarding grammar suggestion with empty originalText.")
                return@mapNotNull null
            }

            val sOffset = suggestion.startOffset
            val eOffset = suggestion.endOffset
            val len = orig.length

            // 1. Exact match at [sOffset, eOffset] or [sOffset, sOffset + orig.length]
            if (sOffset >= 0 && sOffset + len <= text.length && text.substring(sOffset, sOffset + len) == orig) {
                return@mapNotNull if (eOffset == sOffset + len) {
                    suggestion
                } else {
                    suggestion.copy(endOffset = sOffset + len)
                }
            }

            // 2. Shifted by +1 because userPrompt wraps text in quotes ("\"$text\"") or 1-based indexing
            if (sOffset >= 1 && (sOffset - 1) + len <= text.length && text.substring(sOffset - 1, sOffset - 1 + len) == orig) {
                return@mapNotNull suggestion.copy(
                    startOffset = sOffset - 1,
                    endOffset = sOffset - 1 + len
                )
            }

            // 3. Find all exact occurrences of originalText in text; if matches exist, pick the one closest to sOffset
            val occurrences = mutableListOf<Int>()
            var searchIndex = text.indexOf(orig)
            while (searchIndex != -1) {
                occurrences.add(searchIndex)
                searchIndex = text.indexOf(orig, searchIndex + 1)
            }

            if (occurrences.isNotEmpty()) {
                val bestStart = if (sOffset >= 0) {
                    occurrences.minByOrNull { kotlin.math.abs(it - sOffset) }!!
                } else {
                    occurrences.first()
                }
                Log.i(
                    TAG,
                    "Normalized offset for '$orig' from [$sOffset, $eOffset] to [$bestStart, ${bestStart + len}]."
                )
                return@mapNotNull suggestion.copy(
                    startOffset = bestStart,
                    endOffset = bestStart + len
                )
            }

            val actualAtRange = if (sOffset >= 0 && eOffset <= text.length && sOffset < eOffset) {
                text.substring(sOffset, eOffset)
            } else {
                "<out of bounds for text length ${text.length}>"
            }
            Log.w(
                TAG,
                "Discarding invalid grammar suggestion for '$orig': range [$sOffset, $eOffset] (actual='$actualAtRange') does not match manuscript text."
            )
            null
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
