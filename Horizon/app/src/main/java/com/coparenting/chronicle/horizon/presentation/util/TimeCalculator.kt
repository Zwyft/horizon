package com.coparenting.chronicle.horizon.presentation.util

import com.coparenting.chronicle.horizon.domain.model.Message
import javax.inject.Inject
import javax.inject.Singleton
import java.time.temporal.ChronoUnit

@Singleton
class TimeCalculator @Inject constructor() {

    fun calculateTotalInteractionDuration(messages: List<Message>): Long {
        if (messages.size < 2) return 0L
        val sorted = messages.sortedBy { it.timestamp }
        val first = sorted.first().timestamp
        val last = sorted.last().timestamp
        return ChronoUnit.MINUTES.between(first, last)
    }
}
