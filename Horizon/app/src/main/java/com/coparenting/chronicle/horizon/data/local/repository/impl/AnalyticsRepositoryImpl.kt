package com.coparenting.chronicle.horizon.data.local.repository.impl

import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao
import com.coparenting.chronicle.horizon.data.local.database.entity.AnalyticsEntity
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao.AnalyticsSummary
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao.ContactAnalyticsSummary
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao.EmotionalToneCount
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao.HourlyPattern
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao.MonthlyTrend
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao.WeeklyTrend
import com.coparenting.chronicle.horizon.domain.model.AnalyticsReport
import com.coparenting.chronicle.horizon.domain.model.Analytics as DomainAnalytics
import com.coparenting.chronicle.horizon.domain.repository.AnalyticsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val analyticsDao: AnalyticsDao
) : AnalyticsRepository {
    
    override suspend fun getAnalyticsReport(userId: String): AnalyticsReport {
        // Get analytics for last 30 days for comprehensive report
        val endDate = LocalDateTime.now()
        val startDate = endDate.minusDays(30)
        
        val summary = analyticsDao.getAnalyticsSummary(userId, startDate, endDate).first()
        val weeklyTrends = analyticsDao.getWeeklyTrends(userId).first()
        val monthlyTrends = analyticsDao.getMonthlyTrends(userId).first()
        val contactAnalytics = analyticsDao.getContactAnalytics(userId).first()
        val emotionalToneDistribution = analyticsDao.getEmotionalToneDistribution(userId).first()
        val hourlyPattern = analyticsDao.getHourlyPattern(userId).first()
        
        val domainAnalytics = DomainAnalytics(
            totalInteractions = summary.totalRecords,
            averageMessageFrequency = summary.avgMessageFrequency,
            averageInteractionTime = summary.avgInteractionTime,
            averageSentimentScore = summary.avgSentimentScore,
            weeklyTrends = mapWeeklyTrends(weeklyTrends),
            monthlyTrends = mapMonthlyTrends(monthlyTrends),
            contactAnalytics = mapContactAnalytics(contactAnalytics),
            emotionalToneDistribution = mapEmotionalToneDistribution(emotionalToneDistribution),
            hourlyPattern = mapHourlyPattern(hourlyPattern),
            generatedAt = System.currentTimeMillis()
        )
        
        return AnalyticsReport(
            userId = userId,
            analytics = domainAnalytics,
            generatedAt = System.currentTimeMillis()
        )
    }
    
    private fun mapWeeklyTrends(trends: List<WeeklyTrend>): List<DomainAnalytics.WeeklyTrend> {
        return trends.map { 
            DomainAnalytics.WeeklyTrend(
                week = it.week,
                messageFrequency = it.avgMessageFrequency,
                interactionTime = it.avgInteractionTime,
                sentimentScore = it.avgSentimentScore
            )
        }
    }
    
    private fun mapMonthlyTrends(trends: List<MonthlyTrend>): List<DomainAnalytics.MonthlyTrend> {
        return trends.map { 
            DomainAnalytics.MonthlyTrend(
                month = it.month,
                messageFrequency = it.avgMessageFrequency,
                interactionTime = it.avgInteractionTime,
                sentimentScore = it.avgSentimentScore
            )
        }
    }
    
    private fun mapContactAnalytics(contacts: List<ContactAnalyticsSummary>): List<DomainAnalytics.ContactAnalytics> {
        return contacts.map { 
            DomainAnalytics.ContactAnalytics(
                contactId = it.contactId,
                interactionCount = it.contactInteractions,
                messageFrequency = it.avgMessageFrequency,
                sentimentScore = it.avgSentiment
            )
        }
    }
    
    private fun mapEmotionalToneDistribution(distribution: List<EmotionalToneCount>): Map<String, Int> {
        return distribution.associate { it.emotionalTone to it.count }
    }
    
    private fun mapHourlyPattern(pattern: List<HourlyPattern>): Map<Int, Double> {
        return pattern.associate { it.hourOfDay to it.avgFrequency }
    }
    
    // Additional methods for specific analytics queries
    override suspend fun getAnalyticsSummary(userId: String, startDate: LocalDateTime, endDate: LocalDateTime): 
            com.coparenting.chronicle.horizon.domain.model.AnalyticsSummary {
        val summary = analyticsDao.getAnalyticsSummary(userId, startDate, endDate).first()
        return com.coparenting.chronicle.horizon.domain.model.AnalyticsSummary(
            avgMessageFrequency = summary.avgMessageFrequency,
            avgInteractionTime = summary.avgInteractionTime,
            avgSentimentScore = summary.avgSentimentScore,
            totalRecords = summary.totalRecords
        )
    }
    
    override suspend fun getTrendAnalysis(userId: String, days: Int = 30): 
            com.coparenting.chronicle.horizon.domain.model.TrendAnalysis {
        val endDate = LocalDateTime.now()
        val startDate = endDate.minusDays(days.toLong())
        
        val weeklyTrends = analyticsDao.getWeeklyTrends(userId).first()
        val monthlyTrends = analyticsDao.getMonthlyTrends(userId).first()
        
        return com.coparenting.chronicle.horizon.domain.model.TrendAnalysis(
            weeklyTrends = mapWeeklyTrends(weeklyTrends),
            monthlyTrends = mapMonthlyTrends(monthlyTrends),
            analysisPeriodDays = days,
            generatedAt = System.currentTimeMillis()
        )
    }
    
    override suspend fun deleteOldAnalytics(daysToKeep: Int = 90): Int {
        val cutoffDate = LocalDateTime.now().minusDays(daysToKeep.toLong())
        return analyticsDao.deleteOldAnalytics(cutoffDate)
    }
}
