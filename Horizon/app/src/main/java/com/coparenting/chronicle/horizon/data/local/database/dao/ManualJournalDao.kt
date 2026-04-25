package com.coparenting.chronicle.horizon.data.local.database.dao

import androidx.room.*
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ManualJournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ManualJournalEntry)

    @Update
    suspend fun update(entry: ManualJournalEntry)

    @Delete
    suspend fun delete(entry: ManualJournalEntry)

    @Query("SELECT * FROM manual_journal_entries ORDER BY date DESC, timestamp DESC")
    fun getAll(): Flow<List<ManualJournalEntry>>

    @Query("SELECT * FROM manual_journal_entries WHERE date = :date ORDER BY timestamp ASC")
    fun getForDate(date: LocalDateTime): Flow<List<ManualJournalEntry>>

    @Query("SELECT DISTINCT date FROM manual_journal_entries")
    fun getDatesWithEntries(): Flow<List<LocalDateTime>>

    @Query("SELECT * FROM manual_journal_entries WHERE id = :id")
    suspend fun getById(id: String): ManualJournalEntry?

    @Query("""
        SELECT * FROM manual_journal_entries
        WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%'
        ORDER BY date DESC
    """)
    fun search(query: String): Flow<List<ManualJournalEntry>>

    @Query("SELECT * FROM manual_journal_entries WHERE date >= :since ORDER BY date ASC")
    suspend fun getEntriesSince(since: LocalDateTime): List<ManualJournalEntry>
}
