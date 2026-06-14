package com.coparenting.chronicle.horizon.presentation.util

import com.coparenting.chronicle.horizon.domain.model.Message
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageProcessor @Inject constructor() {

    fun groupByHour(messages: List<Message>): Map<Int, List<Message>> =
        messages.groupBy { it.timestamp.hour }

    fun extractTopics(messages: List<Message>): List<String> {
        val keywords = setOf("pickup", "dropoff", "school", "doctor", "hospital", "money",
            "pay", "schedule", "weekend", "holiday", "birthday", "appointment")
        val text = messages.joinToString(" ") { it.messageText }.lowercase()
        return keywords.filter { it in text }
    }
}
