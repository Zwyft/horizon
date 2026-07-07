package com.horizon.coparentinglog

import com.horizon.coparentinglog.core.ArchiveSnapshot
import com.horizon.coparentinglog.core.DaySummary
import com.horizon.coparentinglog.core.JournalGenerationRequest
import com.horizon.coparentinglog.core.MessageDirection
import com.horizon.coparentinglog.core.MessageRecord
import com.horizon.coparentinglog.core.MessageTransport
import com.horizon.coparentinglog.data.JournalComposer
import com.horizon.coparentinglog.data.OpenRouterClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class JournalComposerTest {
    @Test
    fun createsFallbackEntryWhenNoMessagesExist() {
        val composer = JournalComposer(object : OpenRouterClient() {
            override fun summarizeDay(apiKey: String, model: String, prompt: String): DaySummary {
                return DaySummary(
                    date = "2026-07-07",
                    headline = "Test",
                    highlights = listOf("Hello"),
                )
            }

            override fun generateMarkdown(apiKey: String, model: String, prompt: String): String {
                return "# Test"
            }
        })
        val entries = composer.buildJournalEntries(
            apiKey = "key",
            analysisModel = "analysis",
            journalModel = "journal",
            archiveSnapshot = ArchiveSnapshot(),
            request = JournalGenerationRequest(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 7)),
            defaultRegionIso = "US",
        )
        assertEquals(1, entries.size)
        assertEquals("Test", entries.first().title)
    }

    @Test
    fun filtersMessagesByLocalDay() {
        val composer = JournalComposer(object : OpenRouterClient() {
            override fun summarizeDay(apiKey: String, model: String, prompt: String): DaySummary {
                return DaySummary(date = "2026-07-07", headline = "Filtered", highlights = listOf("one"))
            }

            override fun generateMarkdown(apiKey: String, model: String, prompt: String): String {
                return "# Filtered"
            }
        })
        val dayStart = LocalDate.of(2026, 7, 7).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val snapshot = ArchiveSnapshot(
            messages = listOf(
                MessageRecord("1", "t", direction = MessageDirection.INBOUND, transport = MessageTransport.SMS, sentAtEpochMs = dayStart + 1, body = "A"),
                MessageRecord("2", "t", direction = MessageDirection.OUTBOUND, transport = MessageTransport.SMS, sentAtEpochMs = dayStart - 1, body = "B"),
            ),
        )
        val entries = composer.buildJournalEntries(
            apiKey = "key",
            analysisModel = "analysis",
            journalModel = "journal",
            archiveSnapshot = snapshot,
            request = JournalGenerationRequest(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 7)),
            defaultRegionIso = "US",
        )
        assertEquals(1, entries.size)
        assertEquals(listOf("1"), entries.first().messageSourceIds)
    }
}

