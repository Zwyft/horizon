package com.coparenting.chronicle.horizon.data.local.repository.impl

import com.coparenting.chronicle.horizon.data.local.database.dao.MessageDao
import com.coparenting.chronicle.horizon.data.local.database.dao.ContactDao
import com.coparenting.chronicle.horizon.domain.model.Message
import com.coparenting.chronicle.horizon.domain.model.Contact
import com.coparenting.chronicle.horizon.domain.repository.MessageRepository
import com.coparenting.chronicle.horizon.data.remote.sms.SmsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val smsDataSource: SmsDataSource
) : MessageRepository {
    
    override suspend fun saveMessage(message: Message): Long {
        return messageDao.insertMessage(message)
    }
    
    override suspend fun saveMessages(messages: List<Message>): List<Long> {
        return messageDao.insertMessages(messages)
    }
    
    override fun getMessagesByContact(contactId: String): Flow<List<Message>> {
        return messageDao.getMessagesByContact(contactId)
    }
    
    override fun getMessagesForDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Message>> {
        return messageDao.getMessagesForDateRange(startDate, endDate)
    }
    
    override fun getMessagesByThread(threadId: String): Flow<List<Message>> {
        return messageDao.getMessagesByThread(threadId)
    }
    
    override fun getMessagesByPhoneNumber(phoneNumber: String): Flow<List<Message>> {
        return messageDao.getMessagesByPhoneNumber(phoneNumber)
    }
    
    override fun getAllContacts(): Flow<List<com.coparenting.chronicle.horizon.data.local.database.dao.ContactSummary>> {
        return messageDao.getAllContacts()
    }
    
    override fun getMessagesByType(messageType: com.coparenting.chronicle.horizon.domain.model.MessageType): Flow<List<Message>> {
        return messageDao.getMessagesByType(messageType)
    }
    
    override suspend fun getMessageCountForContact(contactId: String, startDate: LocalDateTime, endDate: LocalDateTime): Int {
        return messageDao.getMessageCountForContact(contactId, startDate, endDate)
    }
    
    override suspend fun getTotalMessageCount(startDate: LocalDateTime, endDate: LocalDateTime): Int {
        return messageDao.getTotalMessageCount(startDate, endDate)
    }
    
    override fun getMessagesSince(since: LocalDateTime): Flow<List<Message>> {
        return messageDao.getMessagesSince(since)
    }
    
    override suspend fun deleteMessagesOlderThan(olderThan: LocalDateTime): Int {
        return messageDao.deleteMessagesOlderThan(olderThan)
    }
    
    override suspend fun getMessageById(messageId: String): Message? {
        return messageDao.getMessageById(messageId)
    }
    
    override suspend fun markMessageAsRead(messageId: String): Int {
        return messageDao.markMessageAsRead(messageId)
    }
    
    override suspend fun markAllMessagesAsRead(contactId: String): Int {
        return messageDao.markAllMessagesAsRead(contactId)
    }
    
    override fun getMessageCountsByContact(startDate: LocalDateTime, endDate: LocalDateTime): Flow<Map<String, Int>> {
        return messageDao.getMessageCountsByContact(startDate, endDate)
    }
    
    override suspend fun syncMessagesFromSms(): List<Message> {
        val smsMessages = smsDataSource.getSmsMessages()
        val existingContacts = contactDao.getAllContacts().first()
        
        val processedMessages = smsMessages.map { smsMessage ->
            val contact = existingContacts.find { it.phoneNumber == smsMessage.address }
                ?: Contact(
                    name = smsMessage.contactName ?: smsMessage.address,
                    phoneNumber = smsMessage.address,
                    lastContactDate = smsMessage.timestamp,
                    lastMessageText = smsMessage.body,
                    lastMessageTimestamp = smsMessage.timestamp
                )
            
            Message(
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = smsMessage.address,
                messageText = smsMessage.body,
                timestamp = smsMessage.timestamp,
                messageType = smsMessage.messageType ?: com.coparenting.chronicle.horizon.domain.model.MessageType.TEXT,
                isIncoming = smsMessage.type == com.coparenting.chronicle.horizon.data.remote.sms.SmsDataSource.MessageType.INCOMING,
                threadId = smsMessage.threadId,
                isRead = smsMessage.isRead,
                folder = smsMessage.folder
            )
        }
        
        val newContacts = processedMessages.mapNotNull { message ->
            val existingContact = existingContacts.find { it.phoneNumber == message.phoneNumber }
            if (existingContact == null) {
                Contact(
                    name = message.contactName,
                    phoneNumber = message.phoneNumber,
                    lastContactDate = message.timestamp,
                    lastMessageText = message.messageText,
                    lastMessageTimestamp = message.timestamp,
                    messageCount = 1
                )
            } else null
        }.distinct()
        
        if (newContacts.isNotEmpty()) {
            contactDao.insertContacts(newContacts)
        }
        
        val newMessageIds = messageDao.insertMessages(processedMessages)
        
        processedMessages.forEach { message ->
            contactDao.updateContactLastContact(
                message.contactId,
                message.timestamp,
                message.messageText,
                message.timestamp
            )
        }
        
        return processedMessages
    }
    
    override suspend fun refreshMessageContacts(): Int {
        val smsContacts = smsDataSource.getSmsContacts()
        val existingContacts = contactDao.getAllContacts().first()
        
        val newContacts = smsContacts.filter { smsContact ->
            existingContacts.none { it.phoneNumber == smsContact.phoneNumber }
        }.map { smsContact ->
            Contact(
                name = smsContact.name ?: smsContact.phoneNumber,
                phoneNumber = smsContact.phoneNumber
            )
        }
        
        return if (newContacts.isNotEmpty()) {
            contactDao.insertContacts(newContacts)
            newContacts.size
        } else {
            0
        }
    }
}
