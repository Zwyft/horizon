package com.coparenting.chronicle.horizon.domain.usecase.message

import com.coparenting.chronicle.horizon.domain.model.Message
import com.coparenting.chronicle.horizon.domain.repository.MessageRepository
import com.coparenting.chronicle.horizon.domain.repository.ContactRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

class ProcessNewMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository
) {
    
    suspend operator fun invoke(lastSyncTime: LocalDateTime): Result<List<Message>> {
        return try {
            val syncResult = messageRepository.syncMessagesFromSms()
            
            val existingContacts = contactRepository.getAllContacts().first()
            
            val processedMessages = syncResult.map { smsMessage ->
                val contact = existingContacts.find { it.phoneNumber == smsMessage.phoneNumber }
                    ?: run {
                        val newContact = com.coparenting.chronicle.horizon.domain.model.Contact(
                            name = smsMessage.contactName ?: smsMessage.phoneNumber,
                            phoneNumber = smsMessage.phoneNumber,
                            lastContactDate = smsMessage.timestamp,
                            lastMessageText = smsMessage.messageText,
                            lastMessageTimestamp = smsMessage.timestamp,
                            messageCount = 1
                        )
                        contactRepository.saveContact(newContact)
                        newContact
                    }
                
                com.coparenting.chronicle.horizon.domain.model.Message(
                    id = UUID.randomUUID().toString(),
                    contactId = contact.id,
                    contactName = contact.name,
                    phoneNumber = smsMessage.phoneNumber,
                    messageText = smsMessage.messageText,
                    timestamp = smsMessage.timestamp,
                    messageType = smsMessage.messageType ?: com.coparenting.chronicle.horizon.domain.model.MessageType.TEXT,
                    isIncoming = smsMessage.isIncoming,
                    threadId = smsMessage.threadId,
                    isRead = smsMessage.isRead,
                    folder = smsMessage.folder
                )
            }
            
            val messageIds = messageRepository.saveMessages(processedMessages)
            
            processedMessages.forEach { message ->
                contactRepository.updateContactLastContact(
                    message.contactId,
                    message.timestamp,
                    message.messageText,
                    message.timestamp
                )
            }
            
            Result.success(processedMessages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
