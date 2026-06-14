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
    
    override suspend fun syncMessagesFromSms(sinceMillis: Long): List<Message> {
        val allMessages = smsDataSource.getAllMessagesSince(sinceMillis)
        if (allMessages.isEmpty()) return emptyList()

        val existingContacts = contactDao.getAllContacts().first()

        // Map phone -> contact for fast lookup
        val contactByPhone = existingContacts.associateBy { it.phoneNumber }

        val processedMessages = allMessages.map { smsMsg ->
            val contact = contactByPhone[smsMsg.address]
                ?: Contact(
                    name = smsMsg.contactName ?: smsMsg.address,
                    phoneNumber = smsMsg.address,
                    lastContactDate = smsMsg.timestamp,
                    lastMessageText = smsMsg.body,
                    lastMessageTimestamp = smsMsg.timestamp
                )

            // Stable ID prevents duplicate rows across syncs
            val stableId = if (smsMsg.messageType == com.coparenting.chronicle.horizon.domain.model.MessageType.MMS)
                "mms_${smsMsg.id}" else "sms_${smsMsg.id}"

            Message(
                id = stableId,
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = smsMsg.address,
                messageText = smsMsg.body,
                timestamp = smsMsg.timestamp,
                messageType = smsMsg.messageType ?: com.coparenting.chronicle.horizon.domain.model.MessageType.TEXT,
                isIncoming = smsMsg.type == com.coparenting.chronicle.horizon.domain.model.MessageType.INCOMING,
                threadId = smsMsg.threadId,
                isRead = smsMsg.isRead,
                folder = smsMsg.folder,
                attachmentCount = smsMsg.attachmentCount
            )
        }

        // Save any new contacts
        val newContacts = allMessages.mapNotNull { smsMsg ->
            if (contactByPhone[smsMsg.address] == null) {
                Contact(
                    name = smsMsg.contactName ?: smsMsg.address,
                    phoneNumber = smsMsg.address,
                    lastContactDate = smsMsg.timestamp,
                    lastMessageText = smsMsg.body,
                    lastMessageTimestamp = smsMsg.timestamp,
                    messageCount = 1
                )
            } else null
        }.distinctBy { it.phoneNumber }

        if (newContacts.isNotEmpty()) {
            contactDao.insertContacts(newContacts)
        }

        // REPLACE strategy in DAO means stable IDs make this idempotent
        messageDao.insertMessages(processedMessages)

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
