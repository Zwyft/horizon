package com.horizon.coparentinglog.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Instant

@Serializable
enum class MessageTransport {
    SMS,
    MMS,
    RCS,
    UNKNOWN,
}

@Serializable
enum class MessageDirection {
    INBOUND,
    OUTBOUND,
    SYSTEM,
}

@Serializable
data class AttachmentRecord(
    val mimeType: String,
    val displayName: String? = null,
    val localUri: String? = null,
    val byteCount: Long? = null,
)

@Serializable
data class ContactIdentity(
    val lookupKey: String,
    val displayName: String,
    val normalizedNumber: String,
    val rawNumber: String,
)

@Serializable
data class MessageRecord(
    val sourceId: String,
    val threadKey: String,
    val contactLookupKey: String? = null,
    val normalizedAddress: String? = null,
    val rawAddress: String? = null,
    val direction: MessageDirection,
    val transport: MessageTransport,
    val sentAtEpochMs: Long,
    val body: String? = null,
    val subject: String? = null,
    val attachments: List<AttachmentRecord> = emptyList(),
)

@Serializable
data class SyncCursor(
    val lastSyncedAtEpochMs: Long = 0L,
    val lastProviderRowId: Long = 0L,
)

@Serializable
data class JournalEntryRecord(
    val journalId: String,
    val date: String,
    val title: String,
    val markdown: String,
    val summaryJson: String,
    val messageSourceIds: List<String> = emptyList(),
    val createdAtEpochMs: Long = Instant.now().toEpochMilli(),
)

@Serializable
data class ArchiveSnapshot(
    val syncCursor: SyncCursor = SyncCursor(),
    val messages: List<MessageRecord> = emptyList(),
    val journalEntries: List<JournalEntryRecord> = emptyList(),
)

@Serializable
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val defaultSmsGranted: Boolean = false,
    val messagesPermissionGranted: Boolean = false,
    val contactsPermissionGranted: Boolean = false,
    val openRouterApiKey: String = "",
    val analysisModel: String = "openai/gpt-5.5",
    val journalModel: String = "arcee-ai/virtuoso-large",
    val defaultRegionIso: String = "US",
    val managedRcsEnabled: Boolean = false,
)

@Serializable
data class DaySummary(
    val date: String,
    val headline: String,
    val highlights: List<String> = emptyList(),
    val interactions: List<String> = emptyList(),
    val concerns: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
)

data class SyncReport(
    val insertedCount: Int,
    val updatedCount: Int,
    val journalCount: Int,
    val statusMessage: String,
)

data class JournalGenerationRequest(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val focusContact: ContactIdentity? = null,
)

