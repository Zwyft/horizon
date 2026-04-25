package com.coparenting.chronicle.horizon.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "analytics")
data class AnalyticsEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val userId: String,
    val contactId: String,
    val date: LocalDateTime,
    val messageCount: Int,
    val interactionTime: Long, // in minutes
    val emotionalTone: String, // POSITIVE, NEUTRAL, NEGATIVE, MIXED
    val keyTopics: List<String>,
    val avgResponseTime: Long, // in minutes
    val conversationClusters: Int,
    val lastUpdated: LocalDateTime = LocalDateTime.now(),
    val weekOfYear: Int,
    val monthOfYear: Int,
    val year: Int,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val sentimentScore: Double = 0.0, // -1.0 to 1.0
    val relationshipType: String = "general",
    val messageFrequency: Double = 0.0 // messages per hour

) {
    // Helper methods for analytics
    fun getWeekKey(): String {
        return "week_${year}_$weekOfYear"
    }

    fun getMonthKey(): String {
        return "month_${year}_$monthOfYear"
    }

    fun getDayKey(): String {
        return "day_${date.format(DateTimeFormatter.ISO_DATE)}"
    }

    fun getTimeOfDay(): String {
        return when {
            hourOfDay in 6..11 -> "morning"
            hourOfDay in 12..17 -> "afternoon"
            hourOfDay in 18..23 -> "evening"
            else -> "night"
        }
    }
}
