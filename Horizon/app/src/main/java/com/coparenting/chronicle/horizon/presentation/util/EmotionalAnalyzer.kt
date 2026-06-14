package com.coparenting.chronicle.horizon.presentation.util

import com.coparenting.chronicle.horizon.domain.model.EmotionalTone
import com.coparenting.chronicle.horizon.domain.model.Message
import javax.inject.Inject
import javax.inject.Singleton

data class EmotionalInsight(
    val tone: EmotionalTone,
    val insights: List<String>,
    val keyEvents: List<String>,
    val perspective: String?
)

@Singleton
class EmotionalAnalyzer @Inject constructor() {

    private val positiveWords = setOf("great", "thanks", "appreciate", "happy", "good", "okay", "yes", "sure", "love", "wonderful")
    private val negativeWords = setOf("no", "can't", "won't", "upset", "angry", "problem", "issue", "never", "wrong", "bad", "late", "missed")

    fun analyzeEmotionalTone(messages: List<Message>): EmotionalInsight {
        val allText = messages.joinToString(" ") { it.messageText }.lowercase()
        return EmotionalInsight(
            tone = detectTone(allText),
            insights = buildInsights(messages),
            keyEvents = extractKeyEvents(messages),
            perspective = null
        )
    }

    fun analyzeTextTone(text: String): EmotionalInsight {
        return EmotionalInsight(
            tone = detectTone(text.lowercase()),
            insights = emptyList(),
            keyEvents = emptyList(),
            perspective = null
        )
    }

    private fun detectTone(text: String): EmotionalTone {
        val pos = positiveWords.count { it in text }
        val neg = negativeWords.count { it in text }
        return when {
            pos > neg + 2 -> EmotionalTone.POSITIVE
            neg > pos + 2 -> EmotionalTone.NEGATIVE
            pos > 0 && neg > 0 -> EmotionalTone.MIXED
            else -> EmotionalTone.NEUTRAL
        }
    }

    private fun buildInsights(messages: List<Message>): List<String> {
        val insights = mutableListOf<String>()
        val incoming = messages.count { it.isIncoming }
        val outgoing = messages.count { !it.isIncoming }
        if (incoming + outgoing > 0) {
            insights.add("${incoming + outgoing} messages exchanged (${incoming} received, ${outgoing} sent)")
        }
        return insights
    }

    private fun extractKeyEvents(messages: List<Message>): List<String> =
        messages.take(3).map { it.messageText.take(80) }
}
