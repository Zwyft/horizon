package com.coparenting.chronicle.horizon.domain.usecase.analytics

import com.coparenting.chronicle.horizon.domain.model.Message
import com.coparenting.chronicle.horizon.domain.repository.MessageRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class CalculateMessageFrequencyUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    
    suspend operator fun invoke(
        contactId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double {
        val messages = messageRepository.getMessagesForDateRange(startDate, endDate).first()
        val contactMessages = messages.filter { it.contactId == contactId }
        
        if (contactMessages.isEmpty()) return 0.0
        
        val durationHours = ChronoUnit.HOURS.between(startDate, endDate).toDouble()
        return if (durationHours > 0) {
            contactMessages.size / durationHours
        } else {
            0.0
        }
    }
    
    suspend fun getContactFrequencies(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Map<String, Double> {
        val messages = messageRepository.getMessagesForDateRange(startDate, endDate).first()
        val contactGroups = messages.groupBy { it.contactId }
        
        val durationHours = ChronoUnit.HOURS.between(startDate, endDate).toDouble()
        
        return if (durationHours > 0) {
            contactGroups.mapValues { (_, contactMessages) ->
                contactMessages.size / durationHours
            }
        } else {
            emptyMap()
        }
    }
}
