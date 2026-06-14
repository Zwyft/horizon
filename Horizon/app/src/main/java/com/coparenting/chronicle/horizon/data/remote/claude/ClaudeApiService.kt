package com.coparenting.chronicle.horizon.data.remote.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json".toMediaType()

    /**
     * Generate a diary-style journal entry from a list of SMS messages for a given day.
     */
    suspend fun generateDiaryEntry(
        dateLabel: String,
        messagesText: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
You are helping a parent document their co-parenting experience for personal records.

Based on the following text messages exchanged on $dateLabel, write a detailed,
diary-style journal entry in first-person. The entry should:
- Read naturally, like a personal diary
- Capture the key events, agreements, and tone of the day's communications
- Note any specific requests, arrangements, or issues that came up
- Be factual and professional (this may serve as documentation)
- Include approximate times of important exchanges
- Be 2–4 paragraphs long

Text messages:
$messagesText

Write only the diary entry, no preamble or explanation.
        """.trimIndent()

        call(prompt, apiKey)
    }

    /**
     * Answer a natural-language question about past co-parenting events
     * using the provided context (SMS messages + journal entries).
     */
    suspend fun answerQuestion(
        question: String,
        context: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
You are an AI assistant helping a parent search their co-parenting records.

The user has asked: "$question"

Below are relevant journal entries and text messages from their records.
Answer the question as specifically as possible based only on what is in the records.
If the exact information isn't there, say so and share what you did find that's related.
Keep your answer conversational and concise (2–5 sentences unless a longer answer is needed).

Records:
$context
        """.trimIndent()

        call(prompt, apiKey)
    }

    /**
     * Summarize a week or month of entries for an overview.
     */
    suspend fun summarizePeriod(
        periodLabel: String,
        entriesText: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
You are helping a parent review their co-parenting journal.

Summarize the following journal entries and messages from $periodLabel into a
brief overview (3–5 bullet points). Focus on:
- Key events or handoffs
- Any recurring issues or agreements
- Overall communication tone
- Anything worth remembering

Entries:
$entriesText
        """.trimIndent()

        call(prompt, apiKey)
    }

    private fun call(prompt: String, apiKey: String): Result<String> {
        return try {
            val body = JSONObject().apply {
                put("model", "claude-sonnet-4-6")
                put("max_tokens", 2048)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("API error ${response.code}: ${response.body?.string()}"))
                }
                val json = JSONObject(response.body?.string() ?: "")
                val text = json.getJSONArray("content").getJSONObject(0).getString("text")
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
