package com.coparenting.chronicle.horizon.domain.usecase.message

import com.coparenting.chronicle.horizon.domain.model.Message
import com.coparenting.chronicle.horizon.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject

class GetMessagesForDateRangeUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    
    operator fun invoke(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Message>> {
        return messageRepository.getMessagesForDateRange(startDate, endDate)
    }
}
