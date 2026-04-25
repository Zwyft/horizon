package com.coparenting.chronicle.horizon.data.local.database.dao

import androidx.room.*
import com.coparenting.chronicle.horizon.data.local.database.entity.AnalyticsEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface AnalyticsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalytics(analytics: AnalyticsEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalyticsList(analyticsList: List<AnalyticsEntity>): List<Long>
    
    @Query("SELECT * FROM analytics WHERE userId = :userId ORDER BY date DESC")
    fun getAnalyticsForUser(userId: String): Flow<List<AnalyticsEntity>>
    
    @Query("SELECT * FROM analytics WHERE userId = :userId AND contactId = :contactId ORDER BY date DESC")
    fun getAnalyticsForContact(userId: String, contactId: String): Flow<List<AnalyticsEntity>>
    
    @Query("SELECT * FROM analytics WHERE date BETWEEN :startDate AND :endDate AND userId = :userId ORDER BY date")
    fun getAnalyticsForDateRange(userId: String, startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<AnalyticsEntity>>
    
    @Query("SELECT * FROM analytics WHERE userId = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestAnalyticsForUser(userId: String): AnalyticsEntity?
    
    @Query("SELECT * FROM analytics WHERE contactId = :contactId AND userId = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestAnalyticsForContact(userId: String, contactId: String): AnalyticsEntity?
    
    // Aggregation queries for analytics reporting
    @Query("SELECT AVG(messageFrequency) as avgMessageFrequency, " +
           "AVG(interactionTime) as avgInteractionTime, " +
           "AVG(sentimentScore) as avgSentimentScore, " +
           "COUNT(*) as totalRecords " +
           "FROM analytics WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getAnalyticsSummary(userId: String, startDate: LocalDateTime, endDate: LocalDateTime): AnalyticsSummary
    
    @Query("SELECT strftime('%Y-%W', date/1000, 'unixepoch') as week, " +
           "AVG(messageFrequency) as avgMessageFrequency, " +
           "AVG(interactionTime) as avgInteractionTime, " +
           "AVG(sentimentScore) as avgSentimentScore " +
           "FROM analytics WHERE userId = :userId " +
           "GROUP BY week ORDER BY week")
    fun getWeeklyTrends(userId: String): Flow<List<WeeklyTrend>>
    
    @Query("SELECT strftime('%Y-%m', date/1000, 'unixepoch') as month, " +
           "AVG(messageFrequency) as avgMessageFrequency, " +
           "AVG(interactionTime) as avgInteractionTime, " +
           "AVG(sentimentScore) as avgSentimentScore " +
           "FROM analytics WHERE userId = :userId " +
           "GROUP BY month ORDER BY month")
    fun getMonthlyTrends(userId: String): Flow<List<MonthlyTrend>>
    
    @Query("SELECT contactId, " +
           "COUNT(*) as contactInteractions, " +
           "AVG(messageFrequency) as avgMessageFrequency, " +
           "AVG(sentimentScore) as avgSentiment " +
           "FROM analytics WHERE userId = :userId " +
           "GROUP BY contactId ORDER BY contactInteractions DESC")
    fun getContactAnalytics(userId: String): Flow<List<ContactAnalyticsSummary>>
    
    @Query("SELECT emotionalTone, COUNT(*) as count FROM analytics WHERE userId = :userId GROUP BY emotionalTone")
    fun getEmotionalToneDistribution(userId: String): Flow<List<EmotionalToneCount>>
    
    @Query("SELECT hourOfDay, AVG(messageFrequency) as avgFrequency " +
           "FROM analytics WHERE userId = :userId " +
           "GROUP BY hourOfDay ORDER BY hourOfDay")
    fun getHourlyPattern(userId: String): Flow<List<HourlyPattern>>
    
    @Query("DELETE FROM analytics WHERE userId = :userId")
    suspend fun deleteAllAnalyticsForUser(userId: String)
    
    @Query("DELETE FROM analytics WHERE date < :cutoffDate")
    suspend fun deleteOldAnalytics(cutoffDate: LocalDateTime): Int
}

// Data classes for query results
data class AnalyticsSummary(
    val avgMessageFrequency: Double = 0.0,
    val avgInteractionTime: Long = 0,
    val avgSentimentScore: Double = 0.0,
    val totalRecords: Int = 0
)

data class WeeklyTrend(
    val week: String,
    val avgMessageFrequency: Double = 0.0,
    val avgInteractionTime: Long = 0,
    val avgSentimentScore: Double = 0.0
)

data class MonthlyTrend(
    val month: String,
    val avgMessageFrequency: Double = 0.0,
    val avgInteractionTime: Long = 0,
    val avgSentimentScore: Double = 0.0
)

data class ContactAnalyticsSummary(
    val contactId: String,
    val contactInteractions: Int = 0,
    val avgMessageFrequency: Double = 0.0,
    val avgSentiment: Double = 0.0
)

data class EmotionalToneCount(
    val emotionalTone: String,
    val count: Int
)

data class HourlyPattern(
    val hourOfDay: Int,
    val avgFrequency: Double = 0.0
)

data class ContactActivitySummary(
    val contactId: String,
    val contactName: String = "",
    val totalMessages: Int = 0,
    val avgResponseTime: Long = 0
)

data class DailyAnalyticsTrend(
    val date: String,
    val messageCount: Int = 0,
    val avgSentiment: Double = 0.0
)
