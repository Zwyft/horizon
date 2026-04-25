package com.coparenting.chronicle.horizon.data.local.database.dao

import androidx.room.*
import com.coparenting.chronicle.horizon.domain.model.Message
import com.coparenting.chronicle.horizon.domain.model.MessageType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface MessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>): List<Long>
    
    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getMessagesByContact(contactId: String): Flow<List<Message>>
    
    @Query("SELECT * FROM messages WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp")
    fun getMessagesForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Message>>
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp")
    fun getMessagesByThread(threadId: String): Flow<List<Message>>
    
    @Query("SELECT * FROM messages WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getMessagesByPhoneNumber(phoneNumber: String): Flow<List<Message>>
    
    @Query("SELECT DISTINCT contactId, contactName, phoneNumber FROM messages ORDER BY contactName")
    fun getAllContacts(): Flow<List<ContactSummary>>
    
    @Query("SELECT * FROM messages WHERE messageType = :messageType ORDER BY timestamp DESC")
    fun getMessagesByType(messageType: MessageType): Flow<List<Message>>
    
    @Query("SELECT COUNT(*) FROM messages WHERE contactId = :contactId AND timestamp BETWEEN :startDate AND :endDate")
    suspend fun getMessageCountForContact(contactId: String, startDate: LocalDateTime, endDate: LocalDateTime): Int
    
    @Query("SELECT COUNT(*) FROM messages WHERE timestamp BETWEEN :startDate AND :endDate")
    suspend fun getTotalMessageCount(startDate: LocalDateTime, endDate: LocalDateTime): Int
    
    @Query("SELECT * FROM messages WHERE timestamp >= :since ORDER BY timestamp")
    fun getMessagesSince(since: LocalDateTime): Flow<List<Message>>
    
    @Query("DELETE FROM messages WHERE timestamp < :olderThan")
    suspend fun deleteMessagesOlderThan(olderThan: LocalDateTime): Int
    
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?
    
    @Query("UPDATE messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markMessageAsRead(messageId: String): Int
    
    @Query("UPDATE messages SET isRead = 1 WHERE contactId = :contactId")
    suspend fun markAllMessagesAsRead(contactId: String): Int
    
    @Query("SELECT contactId, COUNT(*) as count FROM messages WHERE timestamp BETWEEN :startDate AND :endDate GROUP BY contactId")
    fun getMessageCountsByContact(startDate: LocalDateTime, endDate: LocalDateTime): Flow<Map<String, Int>>
}

data class ContactSummary(
    val contactId: String,
    val contactName: String,
    val phoneNumber: String
)
