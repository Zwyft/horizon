package com.coparenting.chronicle.horizon.domain.usecase.message

import com.coparenting.chronicle.horizon.data.preferences.AppPreferences
import com.coparenting.chronicle.horizon.domain.model.Message
import com.coparenting.chronicle.horizon.domain.repository.MessageRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject

class ProcessNewMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val preferences: AppPreferences
) {
    suspend operator fun invoke(lastSyncTime: LocalDateTime): Result<List<Message>> {
        return try {
            val sinceMillis = preferences.lastSyncTimeMillis.first()
            val messages = messageRepository.syncMessagesFromSms(sinceMillis)
            // Only advance the watermark when messages were actually fetched
            preferences.setLastSyncTimeMillis(System.currentTimeMillis())
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
