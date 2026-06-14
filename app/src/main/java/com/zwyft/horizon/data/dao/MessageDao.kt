package com.zwyft.horizon.data.dao

import androidx.room.*
import com.zwyft.horizon.data.entity.AddressNameCount
import com.zwyft.horizon.data.entity.AddressNameTuple
import com.zwyft.horizon.data.entity.MessageEntity
import com.zwyft.horizon.data.entity.MonitoredDateRange
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface MessageDao {

    // ── Inserts ─────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>): List<Long>

    // ── Updates ──────────────────────────────────────────────
    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET monitored = :monitored WHERE address IN (:addresses)")
    suspend fun setMonitoredByAddresses(addresses: List<String>, monitored: Boolean)

    @Query("UPDATE messages SET journalProcessed = 1 WHERE id IN (:ids)")
    suspend fun markJournalProcessed(ids: List<Long>)

    @Query("UPDATE messages SET contactName = :name WHERE address = :address")
    suspend fun updateContactName(address: String, name: String)

    // ── Deletes ──────────────────────────────────────────────
    @Query("DELETE FROM messages WHERE importedFrom = :batchTag")
    suspend fun deleteByBatch(batchTag: String): Int

    @Query("DELETE FROM messages WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: Date): Int

    // ── Queries ──────────────────────────────────────────────
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: Long): MessageEntity?

    @Query("""
        SELECT * FROM messages 
        WHERE monitored = 1 
        ORDER BY date DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getMonitoredPaged(limit: Int, offset: Int): List<MessageEntity>

    @Query("""
        SELECT * FROM messages 
        WHERE monitored = 1 
          AND date BETWEEN :start AND :end
        ORDER BY date ASC
    """)
    suspend fun getMonitoredInRange(start: Date, end: Date): List<MessageEntity>

    @Query("""
        SELECT DISTINCT address, contactName 
        FROM messages 
        WHERE monitored = 1 
        ORDER BY contactName ASC
    """)
    suspend fun getMonitoredContacts(): List<AddressNameTuple>

    @Query("SELECT COUNT(*) FROM messages WHERE monitored = 1")
    suspend fun countMonitored(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE journalProcessed = 0 AND monitored = 1")
    suspend fun countUnprocessedForJournal(): Int

    @Query("""
        SELECT * FROM messages 
        WHERE monitored = 1 
          AND journalProcessed = 0 
        ORDER BY date ASC 
        LIMIT :batchSize
    """)
    suspend fun getUnprocessedForJournal(batchSize: Int = 200): List<MessageEntity>

    // ── FTS / Search ─────────────────────────────────────────
    @Query("""
        SELECT * FROM messages 
        WHERE monitored = 1 
          AND body LIKE '%' || :query || '%'
        ORDER BY date DESC 
        LIMIT :limit
    """)
    suspend fun searchBody(query: String, limit: Int = 100): List<MessageEntity>

    // ── Flow queries (UI) ───────────────────────────────────
    @Query("""
        SELECT * FROM messages 
        WHERE monitored = 1 
        ORDER BY date DESC
    """)
    fun observeMonitored(): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE monitored = 1 
          AND address = :address 
        ORDER BY date ASC
    """)
    fun observeThread(address: String): Flow<List<MessageEntity>>

    // ── Import resume support ────────────────────────────────
    @Query("SELECT MAX(date) FROM messages WHERE importedFrom = :batchTag")
    suspend fun getLatestDateForBatch(batchTag: String): Date?

    @Query("""
        SELECT address, COUNT(*) as cnt
        FROM messages
        WHERE monitored = 1
        GROUP BY address
        ORDER BY cnt DESC
        LIMIT :limit
    """)
    suspend fun getTopAddresses(limit: Int = 50): List<AddressNameCount>

    @Query("SELECT MIN(date) as minDate, MAX(date) as maxDate FROM messages WHERE monitored = 1")
    suspend fun getMonitoredDateRange(): MonitoredDateRange?
}
