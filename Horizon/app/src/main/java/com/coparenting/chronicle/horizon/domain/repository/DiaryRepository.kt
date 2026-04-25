package com.coparenting.chronicle.horizon.domain.repository

import com.coparenting.chronicle.horizon.domain.model.DiaryEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface DiaryRepository {
    
    suspend fun saveDiaryEntry(entry: DiaryEntry): Long
    
    suspend fun saveDiaryEntries(entries: List<DiaryEntry>): List<Long>
    
    fun getAllDiaryEntries(): Flow<List<DiaryEntry>>
    
    suspend fun getDiaryEntryByDate(date: LocalDateTime): DiaryEntry?
    
    fun getDiaryEntriesForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<DiaryEntry>>
    
    fun getDiaryEntriesByTone(emotionalTone: com.coparenting.chronicle.horizon.domain.model.EmotionalTone): Flow<List<DiaryEntry>>
    
    fun getGeneratedDiaryEntries(): Flow<List<DiaryEntry>>
    
    fun getDiaryEntriesSince(since: LocalDateTime): Flow<List<DiaryEntry>>
    
    suspend fun getDiaryCountForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Int
    
    suspend fun getDiaryEntryById(entryId: String): DiaryEntry?
    
    suspend fun updateDiaryContent(
        entryId: String,
        content: String,
        insights: List<String>,
        perspective: String?,
        updatedAt: LocalDateTime
    ): Int
    
    suspend fun updateDiaryTone(entryId: String, tone: com.coparenting.chronicle.horizon.domain.model.EmotionalTone, updatedAt: LocalDateTime): Int
    
    suspend fun deleteDiaryEntry(entryId: String): Int
    
    suspend fun deleteDiaryEntriesOlderThan(olderThan: LocalDateTime): Int
    
    fun getDiaryEntriesByDateAndTone(startDate: LocalDateTime, endDate: LocalDateTime, tone: com.coparenting.chronicle.horizon.domain.model.EmotionalTone): Flow<List<DiaryEntry>>
    
    fun getDiaryStatsForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<com.coparenting.chronicle.horizon.data.local.database.dao.DiaryStats>>
    
    fun searchDiaryEntries(query: String): Flow<List<DiaryEntry>>
    
    suspend fun getTotalDiaryEntryCount(): Int
    
    suspend fun getLatestDiaryEntry(): DiaryEntry?
}
