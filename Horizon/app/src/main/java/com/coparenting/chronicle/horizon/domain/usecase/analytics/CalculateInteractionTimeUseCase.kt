package com.coparenting.chronicle.horizon.domain.usecase.analytics

import com.coparenting.chronicle.horizon.domain.model.Message
import com.coparenting.chronicle.horizon.domain.repository.MessageRepository
import com.coparenting.chronicle.horizon.presentation.util.TimeCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject

class CalculateInteractionTimeUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val timeCalculator: TimeCalculator
) {
    
    suspend operator fun invoke(
        contactId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long {
        val messages = messageRepository.getMessagesForDateRange(startDate, endDate).first()
        val contactMessages = messages.filter { it.contactId == contactId }
        
        return timeCalculator.calculateTotalInteractionDuration(contactMessages)
    }
    
    suspend fun getAllInteractionTimes(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Map<String, Long> {
        val messages = messageRepository.getMessagesForDateRange(startDate, endDate).first()
        val contactGroups = messages.groupBy { it.contactId }
        
        return contactGroups.mapValues { (_, contactMessages) ->
            timeCalculator.calculateTotalInteractionDuration(contactMessages)
        }
    }
    
    suspend fun calculateAverageResponseTime(
        contactId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long {
        val messages = messageRepository.getMessagesForDateRange(startDate, endDate).first()
        val contactMessages = messages.filter { it.contactId == contactId && !it.isIncoming }
        
        if (contactMessages.size < 2) return 0L
        
        val responseTimes = mutableListOf<Long>()
        val sortedMessages = contactMessages.sortedBy { it.timestamp }
        
        for (i in 1 until sortedMessages.size) {
            val currentMessage = sortedMessages[i]
            val previousMessage = sortedMessages[i - 1]
            
            val responseTime = java.time.Duration.between(previousMessage.timestamp, currentMessage.timestamp)
                .toMinutes()
            
            if (responseTime > 0 && responseTime <= 1440) { // Ignore very long delays
                responseTimes.add(responseTime)
            }
        }
        
        return if (responseTimes.isNotEmpty()) {
            responseTimes.average().toLong()
        } else {
            0L
        }
    }
}
