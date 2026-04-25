package com.coparenting.chronicle.horizon.domain.repository

import com.coparenting.chronicle.horizon.domain.model.Message
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface MessageRepository {
    
    suspend fun saveMessage(message: Message): Long
    
    suspend fun saveMessages(messages: List<Message>): List<Long>
    
    fun getMessagesByContact(contactId: String): Flow<List<Message>>
    
    fun getMessagesForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Message>>
    
    fun getMessagesByThread(threadId: String): Flow<List<Message>>
    
    fun getMessagesByPhoneNumber(phoneNumber: String): Flow<List<Message>>
    
    fun getAllContacts(): Flow<List<com.coparenting.chronicle.horizon.data.local.database.dao.ContactSummary>>
    
    fun getMessagesByType(messageType: com.coparenting.chronicle.horizon.domain.model.MessageType): Flow<List<Message>>
    
    suspend fun getMessageCountForContact(contactId: String, startDate: LocalDateTime, endDate: LocalDateTime): Int
    
    suspend fun getTotalMessageCount(startDate: LocalDateTime, endDate: LocalDateTime): Int
    
    fun getMessagesSince(since: LocalDateTime): Flow<List<Message>>
    
    suspend fun deleteMessagesOlderThan(olderThan: LocalDateTime): Int
    
    suspend fun getMessageById(messageId: String): Message?
    
    suspend fun markMessageAsRead(messageId: String): Int
    
    suspend fun markAllMessagesAsRead(contactId: String): Int
    
    fun getMessageCountsByContact(startDate: LocalDateTime, endDate: LocalDateTime): Flow<Map<String, Int>>
    
    suspend fun syncMessagesFromSms(): List<Message>
    
    suspend fun refreshMessageContacts(): Int
}
