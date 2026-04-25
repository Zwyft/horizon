package com.coparenting.chronicle.horizon.data.local.database.dao

import androidx.room.*
import com.coparenting.chronicle.horizon.domain.model.DiaryEntry
import com.coparenting.chronicle.horizon.domain.model.EmotionalTone
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface DiaryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryEntry(entry: DiaryEntry): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryEntries(entries: List<DiaryEntry>): List<Long>
    
    @Query("SELECT * FROM diary_entries ORDER BY date DESC")
    fun getAllDiaryEntries(): Flow<List<DiaryEntry>>
    
    @Query("SELECT * FROM diary_entries WHERE date = :date")
    suspend fun getDiaryEntryByDate(date: LocalDateTime): DiaryEntry?
    
    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun getDiaryEntriesForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<DiaryEntry>>
    
    @Query("SELECT * FROM diary_entries WHERE emotionalTone = :tone ORDER BY date DESC")
    fun getDiaryEntriesByTone(tone: EmotionalTone): Flow<List<DiaryEntry>>
    
    @Query("SELECT * FROM diary_entries WHERE isGenerated = 1 ORDER BY date DESC")
    fun getGeneratedDiaryEntries(): Flow<List<DiaryEntry>>
    
    @Query("SELECT * FROM diary_entries WHERE date >= :since ORDER BY date")
    fun getDiaryEntriesSince(since: LocalDateTime): Flow<List<DiaryEntry>>
    
    @Query("SELECT COUNT(*) FROM diary_entries WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getDiaryCountForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Int
    
    @Query("SELECT * FROM diary_entries WHERE id = :entryId")
    suspend fun getDiaryEntryById(entryId: String): DiaryEntry?
    
    @Query("UPDATE diary_entries SET content = :content, insights = :insights, perspectiveComparison = :perspective, updatedAt = :updatedAt WHERE id = :entryId")
    suspend fun updateDiaryContent(
        entryId: String,
        content: String,
        insights: List<String>,
        perspective: String?,
        updatedAt: LocalDateTime
    ): Int
    
    @Query("UPDATE diary_entries SET emotionalTone = :tone, updatedAt = :updatedAt WHERE id = :entryId")
    suspend fun updateDiaryTone(entryId: String, tone: EmotionalTone, updatedAt: LocalDateTime): Int
    
    @Query("DELETE FROM diary_entries WHERE id = :entryId")
    suspend fun deleteDiaryEntry(entryId: String): Int
    
    @Query("DELETE FROM diary_entries WHERE date < :olderThan")
    suspend fun deleteDiaryEntriesOlderThan(olderThan: LocalDateTime): Int
    
    @Query("SELECT * FROM diary_entries WHERE date >= :startDate AND date <= :endDate AND emotionalTone = :tone")
    fun getDiaryEntriesByDateAndTone(startDate: LocalDateTime, endDate: LocalDateTime, tone: EmotionalTone): Flow<List<DiaryEntry>>
    
    @Query("SELECT date, COUNT(*) as messageCount, SUM(interactionDuration) as totalTime, AVG(emotionalScore) as avgEmotionalScore FROM diary_entries WHERE date BETWEEN :startDate AND :endDate GROUP BY date")
    fun getDiaryStatsForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<DiaryStats>>
    
    @Query("SELECT * FROM diary_entries WHERE title LIKE :query OR content LIKE :query OR insights LIKE :query")
    fun searchDiaryEntries(query: String): Flow<List<DiaryEntry>>
    
    @Query("SELECT COUNT(*) FROM diary_entries")
    suspend fun getTotalDiaryEntryCount(): Int
    
    @Query("SELECT * FROM diary_entries ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDiaryEntry(): DiaryEntry?
}

data class DiaryStats(
    val date: LocalDateTime,
    val messageCount: Int,
    val totalTime: Long,
    val avgEmotionalScore: Double
)
