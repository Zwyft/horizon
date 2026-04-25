package com.coparenting.chronicle.horizon.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.data.preferences.AppPreferences
import com.coparenting.chronicle.horizon.data.remote.claude.ClaudeApiService
import com.coparenting.chronicle.horizon.data.remote.sms.SmsDataSource
import com.coparenting.chronicle.horizon.domain.model.DiaryEntry
import com.coparenting.chronicle.horizon.domain.model.EmotionalTone
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import com.coparenting.chronicle.horizon.domain.repository.DiaryRepository
import com.coparenting.chronicle.horizon.domain.repository.ManualJournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class TimelineItem {
    data class JournalItem(val entry: ManualJournalEntry) : TimelineItem()
    data class SmsItem(val msg: SmsDataSource.SmsMessage) : TimelineItem()
    data class DiaryItem(val entry: DiaryEntry) : TimelineItem()
}

data class DayDetailUiState(
    val date: LocalDateTime = LocalDateTime.now().toLocalDate().atStartOfDay(),
    val journalEntries: List<ManualJournalEntry> = emptyList(),
    val smsMessages: List<SmsDataSource.SmsMessage> = emptyList(),
    val generatedDiary: DiaryEntry? = null,
    val timeline: List<TimelineItem> = emptyList(),
    val hasSmsPermission: Boolean = false,
    val isGeneratingSummary: Boolean = false,
    val generationError: String? = null,
    val coParentPhone: String = "",
    val showSms: Boolean = true
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    application: Application,
    private val journalRepository: ManualJournalRepository,
    private val diaryRepository: DiaryRepository,
    private val smsDataSource: SmsDataSource,
    private val claudeApiService: ClaudeApiService,
    private val preferences: AppPreferences
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DayDetailUiState())
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    fun load(date: LocalDateTime, hasSmsPermission: Boolean) {
        viewModelScope.launch {
            val phone = preferences.coParentPhone.first()
            val showSms = preferences.showSmsInTimeline.first()

            _uiState.update { it.copy(date = date, hasSmsPermission = hasSmsPermission, coParentPhone = phone, showSms = showSms) }

            // Load journal entries reactively
            journalRepository.getForDate(date).collect { entries ->
                val sms = if (hasSmsPermission && showSms && phone.isNotBlank()) {
                    runCatching { smsDataSource.getMessagesForDateAndContact(date, phone) }.getOrElse { emptyList() }
                } else emptyList()

                val diary = runCatching {
                    diaryRepository.getDiaryEntryByDate(date)
                }.getOrNull()

                _uiState.update { state ->
                    state.copy(
                        journalEntries = entries,
                        smsMessages = sms,
                        generatedDiary = diary,
                        timeline = buildTimeline(entries, sms, diary)
                    )
                }
            }
        }
    }

    fun deleteEntry(entry: ManualJournalEntry) {
        viewModelScope.launch { journalRepository.delete(entry) }
    }

    fun generateAiSummary() {
        val state = _uiState.value
        viewModelScope.launch {
            val apiKey = preferences.claudeApiKey.first()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(generationError = "No API key configured. Add it in Settings.") }
                return@launch
            }
            _uiState.update { it.copy(isGeneratingSummary = true, generationError = null) }

            val dateLabel = state.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
            val messagesText = buildSmsContext(state.smsMessages, state.coParentPhone)
            val journalContext = state.journalEntries.joinToString("\n\n") {
                "[${it.timestamp.format(DateTimeFormatter.ofPattern("h:mm a"))}] ${it.title.ifBlank { "Note" }}: ${it.content}"
            }
            val fullContext = buildString {
                if (state.smsMessages.isNotEmpty()) append("Text messages:\n$messagesText\n\n")
                if (state.journalEntries.isNotEmpty()) append("Manual notes:\n$journalContext")
            }

            if (fullContext.isBlank()) {
                _uiState.update { it.copy(isGeneratingSummary = false, generationError = "No messages or notes to summarize for this day.") }
                return@launch
            }

            claudeApiService.generateDiaryEntry(dateLabel, fullContext, apiKey).fold(
                onSuccess = { text ->
                    val diary = DiaryEntry(
                        date = state.date,
                        title = "Summary — $dateLabel",
                        content = text,
                        emotionalTone = EmotionalTone.NEUTRAL,
                        keyEvents = emptyList(),
                        messageCount = state.smsMessages.size,
                        contactInteractions = emptyMap(),
                        interactionDuration = 0L,
                        insights = emptyList(),
                        perspectiveComparison = null,
                        isGenerated = true
                    )
                    runCatching { diaryRepository.saveDiaryEntry(diary) }
                    _uiState.update { it.copy(isGeneratingSummary = false, generatedDiary = diary, timeline = buildTimeline(it.journalEntries, it.smsMessages, diary)) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isGeneratingSummary = false, generationError = e.message ?: "Failed to generate summary.") }
                }
            )
        }
    }

    private fun buildTimeline(
        entries: List<ManualJournalEntry>,
        sms: List<SmsDataSource.SmsMessage>,
        diary: DiaryEntry?
    ): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()
        diary?.let { items.add(TimelineItem.DiaryItem(it)) }
        entries.forEach { items.add(TimelineItem.JournalItem(it)) }
        sms.forEach { items.add(TimelineItem.SmsItem(it)) }
        return items.sortedBy {
            when (it) {
                is TimelineItem.JournalItem -> it.entry.timestamp
                is TimelineItem.SmsItem -> it.msg.timestamp
                is TimelineItem.DiaryItem -> it.entry.date
            }
        }
    }

    private fun buildSmsContext(messages: List<SmsDataSource.SmsMessage>, myPhone: String): String {
        return messages.joinToString("\n") { msg ->
            val who = if (msg.type == com.coparenting.chronicle.horizon.domain.model.MessageType.INCOMING) "Co-parent" else "Me"
            val time = msg.timestamp.format(DateTimeFormatter.ofPattern("h:mm a"))
            "[$time $who]: ${msg.body}"
        }
    }
}
