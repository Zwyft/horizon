package com.coparenting.chronicle.horizon.domain.usecase.message

import com.coparenting.chronicle.horizon.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject

class GroupMessagesByContactUseCase @Inject constructor() {
    
    operator fun invoke(messages: Flow<List<Message>>): Flow<Map<String, List<Message>>> {
        return messages.map { messageList ->
            messageList.groupBy { it.contactId }
        }
    }
    
    operator fun invoke(messages: List<Message>): Map<String, List<Message>> {
        return messages.groupBy { it.contactId }
    }
    
    fun getContactsWithMessages(messages: Flow<List<Message>>): Flow<Map<String, Triple<String, Int, LocalDateTime>>> {
        return messages.map { messageList ->
            messageList.groupBy { it.contactId }.mapValues { (_, contactMessages) ->
                val contact = contactMessages.first()
                Triple(
                    contact.contactName,
                    contactMessages.size,
                    contactMessages.maxOf { it.timestamp }
                )
            }
        }
    }
}
