package com.coparenting.chronicle.horizon.data.local.repository.impl

import com.coparenting.chronicle.horizon.data.local.database.dao.DiaryDao
import com.coparenting.chronicle.horizon.domain.model.DiaryEntry
import com.coparenting.chronicle.horizon.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepositoryImpl @Inject constructor(
    private val diaryDao: DiaryDao
) : DiaryRepository {
    
    override suspend fun saveDiaryEntry(entry: DiaryEntry): Long {
        return diaryDao.insertDiaryEntry(entry)
    }
    
    override suspend fun saveDiaryEntries(entries: List<DiaryEntry>): List<Long> {
        return diaryDao.insertDiaryEntries(entries)
    }
    
    override fun getAllDiaryEntries(): Flow<List<DiaryEntry>> {
        return diaryDao.getAllDiaryEntries()
    }
    
    override suspend fun getDiaryEntryByDate(date: LocalDateTime): DiaryEntry? {
        return diaryDao.getDiaryEntryByDate(date)
    }
    
    override fun getDiaryEntriesForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<DiaryEntry>> {
        return diaryDao.getDiaryEntriesForDateRange(startDate, endDate)
    }
    
    override fun getDiaryEntriesByTone(emotionalTone: com.coparenting.chronicle.horizon.domain.model.EmotionalTone): Flow<List<DiaryEntry>> {
        return diaryDao.getDiaryEntriesByTone(emotionalTone)
    }
    
    override fun getGeneratedDiaryEntries(): Flow<List<DiaryEntry>> {
        return diaryDao.getGeneratedDiaryEntries()
    }
    
    override fun getDiaryEntriesSince(since: LocalDateTime): Flow<List<DiaryEntry>> {
        return diaryDao.getDiaryEntriesSince(since)
    }
    
    override suspend fun getDiaryCountForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Int {
        return diaryDao.getDiaryCountForDateRange(startDate, endDate)
    }
    
    override suspend fun getDiaryEntryById(entryId: String): DiaryEntry? {
        return diaryDao.getDiaryEntryById(entryId)
    }
    
    override suspend fun updateDiaryContent(
        entryId: String,
        content: String,
        insights: List<String>,
        perspective: String?,
        updatedAt: LocalDateTime
    ): Int {
        return diaryDao.updateDiaryContent(entryId, content, insights, perspective, updatedAt)
    }
    
    override suspend fun updateDiaryTone(entryId: String, tone: com.coparenting.chronicle.horizon.domain.model.EmotionalTone, updatedAt: LocalDateTime): Int {
        return diaryDao.updateDiaryTone(entryId, tone, updatedAt)
    }
    
    override suspend fun deleteDiaryEntry(entryId: String): Int {
        return diaryDao.deleteDiaryEntry(entryId)
    }
    
    override suspend fun deleteDiaryEntriesOlderThan(olderThan: LocalDateTime): Int {
        return diaryDao.deleteDiaryEntriesOlderThan(olderThan)
    }
    
    override fun getDiaryEntriesByDateAndTone(startDate: LocalDateTime, endDate: LocalDateTime, tone: com.coparenting.chronicle.horizon.domain.model.EmotionalTone): Flow<List<DiaryEntry>> {
        return diaryDao.getDiaryEntriesByDateAndTone(startDate, endDate, tone)
    }
    
    override fun getDiaryStatsForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<com.coparenting.chronicle.horizon.data.local.database.dao.DiaryStats>> {
        return diaryDao.getDiaryStatsForDateRange(startDate, endDate)
    }
    
    override fun searchDiaryEntries(query: String): Flow<List<DiaryEntry>> {
        return diaryDao.searchDiaryEntries(query)
    }
    
    override suspend fun getTotalDiaryEntryCount(): Int {
        return diaryDao.getTotalDiaryEntryCount()
    }
    
    override suspend fun getLatestDiaryEntry(): DiaryEntry? {
        return diaryDao.getLatestDiaryEntry()
    }
}
