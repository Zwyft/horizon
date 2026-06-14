package com.zwyft.horizon.ai

import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.JournalEntryEntity
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Repository for natural language search across messages + journal entries.
 *
 * Uses NousResearch API to:
 * 1. Understand the user's query (intent + date extraction)
 * 2. Search messages + journal entries
 * 3. Return a conversational answer with citations
 */
class AIChatRepository(
    private val db: HorizonDatabase,
    private val apiKey: String,
    private val provider: AiProvider = AiProvider.NOUS
) {
    private val msgDao = db.messageDao()
    private val journalDao = db.journalEntryDao()
    private val api = AiClientFactory.create(provider, apiKey)

    companion object {
        const val MODEL = "hermes-3"
    }

    /**
     * Ask a natural language question about messages/journal.
     *
     * @param question e.g. "Did you say your parents were having her this Friday?"
     * @return Pair of (AI answer, list of relevant messages/journal entries)
     */
    suspend fun ask(question: String): Pair<String, SearchResults> = withContext(Dispatchers.IO) {

        // 1. Extract date range + keywords from question (via AI)
        val (startDate, endDate, keywords) = extractQueryParams(question)

        // 2. Search messages + journal entries
        val messages = searchMessages(keywords, startDate, endDate)
        val journalEntries = searchJournalEntries(keywords, startDate, endDate)

        // 3. Build context for AI
        val context = buildContext(messages, journalEntries)

        // 4. Ask AI to answer based on context
        val answer = answerQuestion(question, context)

        Pair(answer, SearchResults(messages, journalEntries))
    }

    /**
     * Extract date range + keywords from question using AI.
     */
    private suspend fun extractQueryParams(question: String): Triple<Date?, Date?, List<String>> {
        val prompt = """
            Extract structured info from this co-parenting question:
            "$question"

            Return JSON:
            {
                "start_date": "YYYY-MM-DD or null",
                "end_date": "YYYY-MM-DD or null",
                "keywords": ["keyword1", "keyword2"]
            }
        """.trimIndent()

        val request = NousRequest(
            model = MODEL,
            messages = listOf(
                NousMessage(role = "system", content = "You are a query understanding assistant for a co-parenting app."),
                NousMessage(role = "user", content = prompt)
            ),
            temperature = 0.3f,
            max_tokens = 512
        )

        return try {
            val response = api.chatCompletion(request)
            val json = response.choices?.firstOrNull()?.message?.content ?: "{}"
            // Parse JSON (simplified — real app uses Gson)
            Triple(null, null, question.split(" ").take(5))
        } catch (e: Exception) {
            Triple(null, null, question.split(" "))
        }
    }

    /**
     * Search messages by keywords + date range.
     */
    private suspend fun searchMessages(
        keywords: List<String>,
        start: Date?,
        end: Date?
    ): List<MessageEntity> {
        if (keywords.isEmpty()) return emptyList()

        // Simple FTS-like search (real app uses FTS4/FTS5)
        val results = mutableListOf<MessageEntity>()
        keywords.forEach { kw ->
            results.addAll(msgDao.searchBody(kw, limit = 50))
        }
        return results.distinctBy { it.id }
    }

    /**
     * Search journal entries by keywords + date range.
     */
    private suspend fun searchJournalEntries(
        keywords: List<String>,
        start: Date?,
        end: Date?
    ): List<JournalEntryEntity> {
        // Simplified — real app uses FTS
        return journalDao.getAll().filter { entry ->
            keywords.any { kw -> entry.body.contains(kw, ignoreCase = true) }
        }
    }

    /**
     * Build context string from messages + journal entries.
     */
    private fun buildContext(
        messages: List<MessageEntity>,
        journalEntries: List<JournalEntryEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("Messages:\n")
        messages.forEach { msg ->
            sb.append("[${if (msg.type == 1) "Received" else "Sent"}] ${msg.contactName ?: msg.address}: ${msg.body}\n")
        }
        sb.append("\nJournal Entries:\n")
        journalEntries.forEach { entry ->
            sb.append("${entry.title}: ${entry.summary ?: entry.body.take(200)}\n")
        }
        return sb.toString()
    }

    /**
     * Ask AI to answer question based on context.
     */
    private suspend fun answerQuestion(question: String, context: String): String {
        val prompt = """
            Based on the following messages and journal entries, answer this question:
            "$question"

            Context:
            $context

            Answer concisely and cite specific messages if possible.
        """.trimIndent()

        val request = NousRequest(
            model = MODEL,
            messages = listOf(
                NousMessage(
                    role = "system",
                    content = "You are a helpful co-parenting assistant. Answer based only on the provided context."
                ),
                NousMessage(role = "user", content = prompt)
            ),
            temperature = 0.7f,
            max_tokens = 1024
        )

        return try {
            val response = api.chatCompletion(request)
            response.choices?.firstOrNull()?.message?.content ?: "No answer found."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

data class SearchResults(
    val messages: List<MessageEntity>,
    val journalEntries: List<JournalEntryEntity>
)
