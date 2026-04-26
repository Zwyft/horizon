package com.coparenting.chronicle.horizon.data.remote.openrouter

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
class OpenRouterApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json".toMediaType()

    suspend fun generateDiaryEntry(
        dateLabel: String,
        messagesText: String,
        apiKey: String,
        modelId: String
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

        call(prompt, apiKey, modelId)
    }

    suspend fun answerQuestion(
        question: String,
        context: String,
        apiKey: String,
        modelId: String
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

        call(prompt, apiKey, modelId)
    }

    suspend fun summarizePeriod(
        periodLabel: String,
        entriesText: String,
        apiKey: String,
        modelId: String
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

        call(prompt, apiKey, modelId)
    }

    private fun call(prompt: String, apiKey: String, modelId: String): Result<String> {
        return try {
            val body = JSONObject().apply {
                put("model", modelId)
                put("max_tokens", 2048)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("X-Title", "Horizon Co-Parenting Journal")
                .post(body.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("OpenRouter error ${response.code}: ${response.body?.string()}"))
                }
                val json = JSONObject(response.body?.string() ?: "")
                val text = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(text.trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
