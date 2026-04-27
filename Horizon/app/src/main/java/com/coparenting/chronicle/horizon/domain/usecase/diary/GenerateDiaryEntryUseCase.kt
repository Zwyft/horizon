package com.coparenting.chronicle.horizon.domain.usecase.diary

import com.coparenting.chronicle.horizon.domain.model.*
import com.coparenting.chronicle.horizon.domain.repository.MessageRepository
import com.coparenting.chronicle.horizon.domain.repository.DiaryRepository
import com.coparenting.chronicle.horizon.domain.repository.ContactRepository
import com.coparenting.chronicle.horizon.domain.repository.AnalyticsRepository
import com.coparenting.chronicle.horizon.domain.usecase.message.GroupMessagesByContactUseCase
import com.coparenting.chronicle.horizon.presentation.util.EmotionalAnalyzer
import com.coparenting.chronicle.horizon.presentation.util.MessageProcessor
import com.coparenting.chronicle.horizon.presentation.util.TimeCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class GenerateDiaryEntryUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val diaryRepository: DiaryRepository,
    private val contactRepository: ContactRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val emotionalAnalyzer: EmotionalAnalyzer,
    private val messageProcessor: MessageProcessor,
    private val timeCalculator: TimeCalculator
) {
    
    suspend operator fun invoke(date: LocalDateTime): DiaryGenerationResult {
        return try {
            val startOfDay = date.withHour(0).withMinute(0).withSecond(0).withNano(0)
            val endOfDay = date.withHour(23).withMinute(59).withSecond(59).withNano(999)
            
            val messages = messageRepository.getMessagesForDateRange(startOfDay, endOfDay).first()
            if (messages.isEmpty()) {
                return DiaryGenerationResult.Error("No messages found for ${date.format(DateTimeFormatter.ISO_DATE)}")
            }
            
            val groupedMessages = GroupMessagesByContactUseCase()(messages)
            
            val contactInteractions = mutableMapOf<String, Int>()
            val timelineEvents = mutableListOf<TimelineEvent>()
            
            groupedMessages.forEach { (contactId, contactMessages) ->
                val contact = contactRepository.getContactById(contactId)
                if (contact != null) {
                    contactInteractions[contact.name] = contactMessages.size
                    
                    val sortedMessages = contactMessages.sortedBy { it.timestamp }
                    
                    sortedMessages.forEachIndexed { index, message ->
                        val eventType = if (message.isIncoming) {
                            EventType.MESSAGE_EXCHANGE
                        } else {
                            EventType.MESSAGE_EXCHANGE
                        }
                        
                        timelineEvents.add(
                            TimelineEvent(
                                id = UUID.randomUUID().toString(),
                                diaryEntryId = "", // Will be set after diary entry is created
                                timestamp = message.timestamp,
                                eventType = eventType,
                                title = "Message from ${contact.name}",
                                description = message.messageText.take(100) + if (message.messageText.length > 100) "..." else "",
                                contactId = contactId,
                                durationMinutes = 0 // Messages are instantaneous
                            )
                        )
                    }
                }
            }
            
            val sortedEvents = timelineEvents.sortedBy { it.timestamp }
            val keyEvents = sortedEvents.take(5).map { "${it.eventType.name}: ${it.title}" }
            
            val emotionalInsights = emotionalAnalyzer.analyzeEmotionalTone(messages)
            val insights = emotionalInsights.insights
            
            val perspectiveComparison = generatePerspectiveComparison(messages, emotionalInsights)
            
            val interactionDuration = timeCalculator.calculateTotalInteractionDuration(messages)
            
            val diaryEntry = DiaryEntry(
                id = UUID.randomUUID().toString(),
                date = date,
                title = "Daily Chronicle - ${date.format(DateTimeFormatter.ISO_DATE)}",
                content = generateDiaryContent(
                    date = date,
                    messages = messages,
                    contactInteractions = contactInteractions,
                    emotionalInsights = emotionalInsights,
                    interactionDuration = interactionDuration
                ),
                emotionalTone = emotionalInsights.tone,
                keyEvents = keyEvents,
                messageCount = messages.size,
                contactInteractions = contactInteractions.mapValues { it.value }.toMap(),
                interactionDuration = interactionDuration,
                insights = insights,
                perspectiveComparison = perspectiveComparison,
                isGenerated = true
            )
            
            val diaryEntryId = diaryRepository.saveDiaryEntry(diaryEntry).toInt()
            val updatedEvents = sortedEvents.map { it.copy(diaryEntryId = diaryEntryId.toString()) }
            if (updatedEvents.isNotEmpty()) {
                // TODO: Add TimelineEvent repository and save events
            }
            
            DiaryGenerationResult.Success(diaryEntry)
        } catch (e: Exception) {
            DiaryGenerationResult.Error("Failed to generate diary entry: ${e.message}")
        }
    }
    
    private fun generateDiaryContent(
        date: LocalDateTime,
        messages: List<Message>,
        contactInteractions: Map<String, Int>,
        emotionalInsights: EmotionalInsight,
        interactionDuration: Long
    ): String {
        val dateStr = date.format(DateTimeFormatter.ISO_DATE)
        val totalMessages = messages.size
        val totalContacts = contactInteractions.size
        val avgMessagesPerContact = if (totalContacts > 0) totalMessages / totalContacts else 0
        
        val sb = StringBuilder()
        sb.append("## Daily Chronicle - $dateStr\n\n")
        
        sb.append("### Overview\n")
        sb.append("- Total messages: $totalMessages\n")
        sb.append("- Contacts interacted with: $totalContacts\n")
        sb.append("- Average messages per contact: $avgMessagesPerContact\n")
        sb.append("- Total interaction duration: ${interactionDuration} minutes\n")
        sb.append("- Emotional tone: ${emotionalInsights.tone.name}\n\n")
        
        sb.append("### Contact Interactions\n")
        contactInteractions.entries
            .sortedByDescending { it.value }
            .forEach { (contactName, count) ->
                sb.append("- **$contactName**: $count messages\n")
            }
        
        sb.append("\n### Key Events\n")
        emotionalInsights.keyEvents.forEachIndexed { index, event ->
            sb.append("${index + 1}. $event\n")
        }
        
        sb.append("\n### Insights\n")
        emotionalInsights.insights.forEach { insight ->
            sb.append("- $insight\n")
        }
        
        if (emotionalInsights.perspective != null) {
            sb.append("\n### Perspective Analysis\n")
            sb.append(emotionalInsights.perspective)
        }
        
        return sb.toString()
    }
    
    private fun generatePerspectiveComparison(messages: List<Message>, emotionalInsights: EmotionalInsight): String? {
        if (messages.size < 2) return null
        
        val incomingMessages = messages.filter { it.isIncoming }
        val outgoingMessages = messages.filter { !it.isIncoming }
        
        if (incomingMessages.isEmpty() || outgoingMessages.isEmpty()) return null
        
        val incomingTone = emotionalAnalyzer.analyzeTextTone(incomingMessages.joinToString("\n") { it.messageText })
        val outgoingTone = emotionalAnalyzer.analyzeTextTone(outgoingMessages.joinToString("\n") { it.messageText })
        
        return "Perspective Comparison:\n" +
                "Incoming messages show ${incomingTone.tone.name} tone\n" +
                "Outgoing messages show ${outgoingTone.tone.name} tone\n" +
                "Communication balance suggests ${communicationBalance(incomingMessages.size, outgoingMessages.size)}"
    }
    
    private fun communicationBalance(incoming: Int, outgoing: Int): String {
        val ratio = if (outgoing > 0) incoming.toDouble() / outgoing else incoming.toDouble()
        return when {
            ratio > 1.5 -> "You're more of a listener"
            ratio < 0.67 -> "You're more of a talker"
            else -> "Good communication balance"
        }
    }
}
