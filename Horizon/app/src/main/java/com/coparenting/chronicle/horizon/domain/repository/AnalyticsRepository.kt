package com.coparenting.chronicle.horizon.domain.repository

import com.coparenting.chronicle.horizon.domain.model.AnalyticsData
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface AnalyticsRepository {
    
    suspend fun saveAnalyticsData(data: AnalyticsData): Long
    
    suspend fun saveAnalyticsDataList(dataList: List<AnalyticsData>): List<Long>
    
    fun getAllAnalyticsData(): Flow<List<AnalyticsData>>
    
    fun getAnalyticsForContact(contactId: String): Flow<List<AnalyticsData>>
    
    fun getAnalyticsForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<AnalyticsData>>
    
    suspend fun getAnalyticsForContactOnDate(contactId: String, date: LocalDateTime): AnalyticsData?
    
    fun getContactAnalyticsSummary(startDate: LocalDateTime, endDate: LocalDateTime): Flow<Map<String, com.coparenting.chronicle.horizon.data.local.database.dao.ContactAnalyticsSummary>>
    
    suspend fun getOverallEmotionalScore(startDate: LocalDateTime, endDate: LocalDateTime): Pair<Double, Int>?
    
    fun getMostPositiveContacts(startDate: LocalDateTime, endDate: LocalDateTime): Flow<Map<String, Double>>
    
    fun getMostNegativeContacts(startDate: LocalDateTime, endDate: LocalDateTime): Flow<Map<String, Double>>
    
    fun getMostActiveContactsByTime(startDate: LocalDateTime, endDate: LocalDateTime): Flow<Map<String, com.coparenting.chronicle.horizon.data.local.database.dao.ContactActivitySummary>>
    
    fun getDailyAnalyticsTrends(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<com.coparenting.chronicle.horizon.data.local.database.dao.DailyAnalyticsTrend>>
    
    suspend fun updateEmotionalScore(analyticsId: String, score: Double, topics: List<String>, updatedAt: LocalDateTime): Int
    
    suspend fun getLatestAnalyticsForContact(contactId: String): AnalyticsData?
    
    suspend fun deleteAnalyticsDataOlderThan(olderThan: LocalDateTime): Int
    
    suspend fun getAnalyticsCountForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Int
}
