package com.horizon.coparentinglog.data

import com.horizon.coparentinglog.core.ArchiveSnapshot
import com.horizon.coparentinglog.core.ContactIdentity
import com.horizon.coparentinglog.core.DaySummary
import com.horizon.coparentinglog.core.JournalEntryRecord
import com.horizon.coparentinglog.core.JournalGenerationRequest
import com.horizon.coparentinglog.core.MessageRecord
import com.horizon.coparentinglog.core.MessageNormalization
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

class JournalComposer(
    private val openRouterClient: OpenRouterClient,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }

    fun buildJournalEntries(
        apiKey: String,
        analysisModel: String,
        journalModel: String,
        archiveSnapshot: ArchiveSnapshot,
        request: JournalGenerationRequest,
        defaultRegionIso: String,
    ): List<JournalEntryRecord> {
        val dates = generateSequence(request.startDate) { current ->
            if (current.isBefore(request.endDate)) current.plusDays(1) else null
        }.toList()

        return dates.map { date ->
            val messages = archiveSnapshot.messages.forDate(date, request.focusContact, defaultRegionIso)
            val summary = summarizeDay(apiKey, analysisModel, date, messages)
            val markdown = writeJournal(apiKey, journalModel, date, summary, messages)
            JournalEntryRecord(
                journalId = UUID.randomUUID().toString(),
                date = date.toString(),
                title = summary.headline,
                markdown = markdown,
                summaryJson = json.encodeToString(summary),
                messageSourceIds = messages.map { it.sourceId },
            )
        }
    }

    private fun summarizeDay(
        apiKey: String,
        analysisModel: String,
        date: LocalDate,
        messages: List<MessageRecord>,
    ): DaySummary {
        val prompt = buildString {
            appendLine("Summarize the following co-parenting message day as JSON.")
            appendLine("Date: $date")
            appendLine("Return a JSON object with fields: date, headline, highlights, interactions, concerns, nextSteps.")
            appendLine("Use a neutral, factual tone and do not fabricate details.")
            appendLine()
            messages.forEach { message ->
                appendLine("${message.sentAtEpochMs}: ${message.direction} ${message.transport} ${message.rawAddress ?: message.normalizedAddress ?: "unknown"}")
                appendLine(message.subject?.let { "Subject: $it" } ?: "")
                appendLine(message.body ?: "(no body)")
                appendLine("---")
            }
        }
        return if (messages.isEmpty()) {
            DaySummary(
                date = date.toString(),
                headline = "Quiet day",
                highlights = listOf("No synced messages were recorded for this date."),
                interactions = emptyList(),
                concerns = emptyList(),
                nextSteps = listOf("Confirm the archive is synchronized if you expected messages here."),
            )
        } else {
            runCatching { openRouterClient.summarizeDay(apiKey, analysisModel, prompt) }.getOrElse {
                DaySummary(
                    date = date.toString(),
                    headline = "Day summary",
                    highlights = listOf("Messages were synced, but the model summary failed: ${it.message.orEmpty()}"),
                    interactions = listOf("Raw message count: ${messages.size}"),
                    concerns = emptyList(),
                    nextSteps = emptyList(),
                )
            }
        }
    }

    private fun writeJournal(
        apiKey: String,
        journalModel: String,
        date: LocalDate,
        summary: DaySummary,
        messages: List<MessageRecord>,
    ): String {
        val prompt = buildString {
            appendLine("Write a modern journal entry for $date using the summary below.")
            appendLine("Style: polished, reflective, grounded, and readable. Format as markdown.")
            appendLine("Do not add facts not supported by the summary or messages.")
            appendLine()
            appendLine("Summary JSON:")
            appendLine(json.encodeToString(summary))
            appendLine()
            appendLine("Supporting messages:")
            messages.forEach { message ->
                appendLine("- ${message.direction} ${message.transport}: ${message.body ?: message.subject ?: "(no body)"}")
            }
        }
        return runCatching {
            openRouterClient.generateMarkdown(apiKey, journalModel, prompt)
        }.getOrElse {
            buildString {
                appendLine("# ${summary.headline}")
                appendLine()
                appendLine("> ${summary.highlights.firstOrNull() ?: "No further details available."}")
                appendLine()
                appendLine("## Notes")
                summary.highlights.forEach { appendLine("- $it") }
                if (summary.concerns.isNotEmpty()) {
                    appendLine()
                    appendLine("## Watch items")
                    summary.concerns.forEach { appendLine("- $it") }
                }
                appendLine()
                appendLine("_Model fallback used: ${it.message.orEmpty()}_")
            }
        }
    }

    private fun List<MessageRecord>.forDate(
        date: LocalDate,
        focusContact: ContactIdentity?,
        defaultRegionIso: String,
    ): List<MessageRecord> {
        val zone = java.time.ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dateFiltered = filter { it.sentAtEpochMs in start until end }
        if (focusContact == null) return dateFiltered
        return dateFiltered.filter { message ->
            val address = message.normalizedAddress ?: message.rawAddress
            address != null && MessageNormalization.areSameNumber(address, focusContact.normalizedNumber, defaultRegionIso)
        }
    }
}
