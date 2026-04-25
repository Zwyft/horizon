package com.coparenting.chronicle.horizon.domain.model

import java.util.Date

/**
 * Domain model for analytics reporting
 */
data class AnalyticsReport(
    val userId: String,
    val analytics: Analytics,
    val generatedAt: Long = System.currentTimeMillis()
)

data class Analytics(
    val totalInteractions: Int,
    val averageMessageFrequency: Double,
    val averageInteractionTime: Long,
    val averageSentimentScore: Double,
    val weeklyTrends: List<WeeklyTrend>,
    val monthlyTrends: List<MonthlyTrend>,
    val contactAnalytics: List<ContactAnalytics>,
    val emotionalToneDistribution: Map<String, Int>,
    val hourlyPattern: Map<Int, Double>
)

data class WeeklyTrend(
    val week: String,
    val messageFrequency: Double,
    val interactionTime: Long,
    val sentimentScore: Double
)

data class MonthlyTrend(
    val month: String,
    val messageFrequency: Double,
    val interactionTime: Long,
    val sentimentScore: Double
)

data class ContactAnalytics(
    val contactId: String,
    val interactionCount: Int,
    val messageFrequency: Double,
    val sentimentScore: Double
)

data class TrendAnalysis(
    val weeklyTrends: List<WeeklyTrend>,
    val monthlyTrends: List<MonthlyTrend>,
    val analysisPeriodDays: Int,
    val generatedAt: Long = System.currentTimeMillis()
)

/**
 * Summary model for DAO queries
 */
data class AnalyticsSummary(
    val avgMessageFrequency: Double = 0.0,
    val avgInteractionTime: Long = 0,
    val avgSentimentScore: Double = 0.0,
    val totalRecords: Int = 0
)
