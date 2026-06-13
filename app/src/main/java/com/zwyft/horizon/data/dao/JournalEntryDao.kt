package com.zwyft.horizon.data.dao

import androidx.room.*
import com.zwyft.horizon.data.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface JournalEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<JournalEntryEntity>): List<Long>

    @Update
    suspend fun update(entry: JournalEntryEntity)

    @Delete
    suspend fun delete(entry: JournalEntryEntity)

    @Query("SELECT * FROM journal_entries ORDER BY dateStart DESC")
    suspend fun getAll(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getById(id: Long): JournalEntryEntity?

    @Query("""
        SELECT * FROM journal_entries 
        WHERE dateStart BETWEEN :start AND :end 
        ORDER BY dateStart DESC
    """)
    suspend fun getInRange(start: Date, end: Date): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE bookmarked = 1 ORDER BY dateStart DESC")
    suspend fun getBookmarked(): List<JournalEntryEntity>

    @Query("UPDATE journal_entries SET bookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query("UPDATE journal_entries SET userNotes = :notes, userEdited = 1 WHERE id = :id")
    suspend fun setUserNotes(id: Long, notes: String)

    @Query("UPDATE journal_entries SET userEdited = :edited WHERE id = :id")
    suspend fun setUserEdited(id: Long, edited: Boolean)

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM journal_entries ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getMostRecent(): JournalEntryEntity?

    // Flow for UI
    @Query("SELECT * FROM journal_entries ORDER BY dateStart DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE bookmarked = 1 ORDER BY dateStart DESC")
    fun observeBookmarked(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    fun observeById(id: Long): Flow<JournalEntryEntity?>
}
