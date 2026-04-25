package com.coparenting.chronicle.horizon.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val contactId: String,
    val contactName: String,
    val phoneNumber: String,
    val messageText: String,
    val timestamp: LocalDateTime,
    val messageType: MessageType,
    val isIncoming: Boolean,
    val threadId: String,
    val attachmentCount: Int = 0,
    val isRead: Boolean = true,
    val folder: String = "inbox"
)

enum class MessageType {
    TEXT, IMAGE, AUDIO, VIDEO, FILE, MMS,
    INCOMING, OUTGOING, DRAFT, UNKNOWN
}

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val lastContactDate: LocalDateTime? = null,
    val messageCount: Int = 0,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: LocalDateTime? = null,
    val isStarred: Boolean = false,
    val note: String? = null
)

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDateTime,
    val title: String,
    val content: String,
    val emotionalTone: EmotionalTone,
    val keyEvents: List<String>,
    val messageCount: Int,
    val contactInteractions: Map<String, Int>,
    val interactionDuration: Long, // in minutes
    val insights: List<String>,
    val perspectiveComparison: String?,
    val emotionalScore: Double = 0.0, // -1.0 to 1.0
    val isGenerated: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class EmotionalTone {
    POSITIVE, NEUTRAL, NEGATIVE, MIXED, UNCERTAIN
}

@Entity(tableName = "timeline_events")
data class TimelineEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val diaryEntryId: String,
    val timestamp: LocalDateTime,
    val eventType: EventType,
    val title: String,
    val description: String,
    val contactId: String?,
    val durationMinutes: Int = 0,
    val isHighlighted: Boolean = false
)

enum class EventType {
    MESSAGE_EXCHANGE, PHONE_CALL, IN_PERSON_MEETING, ACTIVITY, DECISION_MADE, CONFLICT
}

data class AnalyticsData(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDateTime,
    val contactId: String,
    val messageFrequency: Double,
    val interactionTime: Long,
    val conversationClusters: Int,
    val avgResponseTime: Long,
    val emotionalScore: Double,
    val keyTopics: List<String>,
    val lastUpdated: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "parenting_schedule")
data class ParentingSchedule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDateTime,
    val parent1Name: String,
    val parent2Name: String,
    val parent1StartTime: LocalDateTime,
    val parent1EndTime: LocalDateTime,
    val parent2StartTime: LocalDateTime,
    val parent2EndTime: LocalDateTime,
    val scheduleType: ScheduleType,
    val notes: String? = null,
    val isConflicted: Boolean = false,
    val version: Int = 1,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class ScheduleType {
    NORMAL, CHANGED, HOLIDAY, MAKEUP_DAY, COURT_ORDERED
}

@Entity(tableName = "schedule_conflicts")
data class ScheduleConflict(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDateTime,
    val parent1Name: String,
    val parent2Name: String,
    val conflictType: ConflictType,
    val description: String,
    val suggestedResolution: String?,
    val isResolved: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val resolvedAt: LocalDateTime? = null
)

enum class ConflictType {
    TIME_OVERLAP, MISSED_HANDOFF, COMMUNICATION_BREAKDOWN, COURT_VIOLATION
}

sealed class DiaryGenerationResult {
    data class Success(val diaryEntry: DiaryEntry) : DiaryGenerationResult()
    data class Error(val message: String) : DiaryGenerationResult()
}

data class TimelineResult(
    val events: List<TimelineEvent>,
    val contactInteractions: Map<String, Int>,
    val totalDuration: Long
)

data class ParentingScheduleUpdate(
    val date: LocalDateTime,
    val parent1Changes: ScheduleChange?,
    val parent2Changes: ScheduleChange?
)

data class ScheduleChange(
    val oldStartTime: LocalDateTime?,
    val oldEndTime: LocalDateTime?,
    val newStartTime: LocalDateTime,
    val newEndTime: LocalDateTime,
    val reason: String
)
