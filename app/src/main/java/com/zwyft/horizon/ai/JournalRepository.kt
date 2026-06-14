package com.zwyft.horizon.ai

import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.dao.JournalEntryDao
import com.zwyft.horizon.data.dao.MessageDao
import com.zwyft.horizon.data.entity.JournalEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Repository that generates AI journal entries from monitored messages.
 *
 * Responsibilities:
 * - Fetch unprocessed monitored messages (batch)
 * - Build a prompt that "reads between the lines"
 * - Call NousResearch API (via NousApiClient)
 * - Parse response and insert JournalEntryEntity
 * - Mark messages as journalProcessed
 */
class JournalRepository(
    private val db: HorizonDatabase,
    private val apiKey: String,   // from settings
    private val provider: AiProvider = AiProvider.NOUS,
    private val model: String = MODEL
) {
    companion object {
        const val BATCH_SIZE = 200  // messages per journal entry
        const val MODEL = "hermes-3"
    }

    private val msgDao: MessageDao = db.messageDao()
    private val journalDao: JournalEntryDao = db.journalEntryDao()
    private val api = NousApiClient.create(apiKey)

    /**
     * Generate a journal entry from messages in a date range.
     *
     * @param startDate Start of window
     * @param endDate End of window
     * @return The generated JournalEntryEntity (or null if API fails)
     */
    suspend fun generateJournalEntry(
        startDate: Date,
        endDate: Date
    ): JournalEntryEntity? = withContext(Dispatchers.IO) {

        // 1. Fetch monitored messages in range
        val messages = msgDao.getMonitoredInRange(startDate, endDate)
        if (messages.isEmpty()) return@withContext null

        // 2. Build "reading between the lines" prompt
        val prompt = buildPrompt(messages, startDate, endDate)

        // 3. Call NousResearch API
        val request = NousRequest(
            model = MODEL,
            messages = listOf(
                NousMessage(
                    role = "system",
                    content = "You are a thoughtful co-parenting journal assistant. " +
                        "Analyze messages between co-parents and write a neutral, " +
                        "observant journal entry. Read between the lines — note " +
                        "agreements, conflicts, tone, and implicit commitments."
                ),
                NousMessage(role = "user", content = prompt)
            ),
            temperature = 0.7f,
            max_tokens = 2048
        )

        val response = try {
            api.chatCompletion(request)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }

        val aiText = response.choices?.firstOrNull()?.message?.content ?: return@withContext null

        // 4. Parse AI response (extract title, body, summary, tags)
        val (title, body, summary, tags) = parseAiResponse(aiText)

        // 5. Insert journal entry
        val entry = JournalEntryEntity(
            title = title,
            body = body,
            summary = summary,
            dateStart = startDate,
            dateEnd = endDate,
            modelUsed = MODEL,
            promptUsed = prompt.take(500),
            relatedMessageIds = messages.joinToString(",") { it.id.toString() },
            tags = tags,
            sentimentOverall = extractSentiment(aiText)
        )

        val id = journalDao.insert(entry)

        // 6. Mark messages as processed
        msgDao.markJournalProcessed(messages.map { it.id })

        entry.copy(id = id)
    }

    /**
     * Generate a journal entry from a pre-fetched list of messages.
     * Used by [AutoJournalWorker] for daily auto-generation.
     */
    suspend fun generateJournalEntryFromMessages(
        messages: List<com.zwyft.horizon.data.entity.MessageEntity>,
        startDate: Date,
        endDate: Date
    ): JournalEntryEntity? = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext null

        val prompt = buildPrompt(messages, startDate, endDate)

        val request = NousRequest(
            model = model,
            messages = listOf(
                NousMessage(
                    role = "system",
                    content = "You are a thoughtful co-parenting journal assistant. " +
                        "Analyze messages between co-parents and write a neutral, " +
                        "observant journal entry. Read between the lines — note " +
                        "agreements, conflicts, tone, and implicit commitments."
                ),
                NousMessage(role = "user", content = prompt)
            ),
            temperature = 0.7f,
            max_tokens = 2048
        )

        val response = try {
            api.chatCompletion(request)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }

        val aiText = response.choices?.firstOrNull()?.message?.content ?: return@withContext null
        val (title, body, summary, tags) = parseAiResponse(aiText)

        val entry = JournalEntryEntity(
            title = title,
            body = body,
            summary = summary,
            dateStart = startDate,
            dateEnd = endDate,
            modelUsed = model,
            promptUsed = prompt.take(500),
            relatedMessageIds = messages.joinToString(",") { it.id.toString() },
            tags = tags,
            sentimentOverall = extractSentiment(aiText)
        )
        val id = journalDao.insert(entry)
        return@withContext entry.copy(id = id)
    }

    /**
     * Build prompt from messages.
     */
    private fun buildPrompt(
        messages: List<com.zwyft.horizon.data.entity.MessageEntity>,
        start: Date,
        end: Date
    ): String {
        val sb = StringBuilder()
        sb.append("Here are messages between co-parents from ${start} to ${end}:\n\n")

        messages.forEach { msg ->
            val direction = if (msg.type == 1) "Received" else "Sent"
            sb.append("[$direction] ${msg.contactName ?: msg.address}: ${msg.body}\n")
        }

        sb.append("\nPlease write a journal entry that:\n")
        sb.append("1. Summarizes what was discussed.\n")
        sb.append("2. Notes any agreements or plans made.\n")
        sb.append("3. Reads between the lines — tone, unspoken tensions, implicit commitments.\n")
        sb.append("4. Is neutral and fact-based (suitable for legal context if needed).\n")

        return sb.toString()
    }

    /**
     * Parse AI response into (title, body, summary, tags).
     */
    private fun parseAiResponse(aiText: String): Quadruple<String, String, String, String> {
        // Simple parsing — assume AI returns structured text
        val lines = aiText.lines()
        val title = lines.firstOrNull()?.removePrefix("Title:")?.trim() ?: "Journal Entry"
        val body = aiText
        val summary = lines.find { it.startsWith("Summary:") }?.removePrefix("Summary:")?.trim() ?: ""
        val tags = lines.find { it.startsWith("Tags:") }?.removePrefix("Tags:")?.trim() ?: ""
        return Quadruple(title, body, summary, tags)
    }

    /**
     * Rough sentiment extraction (-1.0 to 1.0).
     */
    private fun extractSentiment(text: String): Float {
        val lower = text.lowercase()
        val positive = listOf("happy", "agree", "thank", "great", "good", "helpful").count { lower.contains(it) }
        val negative = listOf("conflict", "angry", "refuse", "late", "missed", "problem").count { lower.contains(it) }
        return (positive - negative).coerceIn(-10, 10) / 10f
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
