package com.coparenting.chronicle.horizon.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.data.preferences.AppPreferences
import com.coparenting.chronicle.horizon.data.remote.claude.ClaudeApiService
import com.coparenting.chronicle.horizon.data.remote.openrouter.OpenRouterApiService
import com.coparenting.chronicle.horizon.data.remote.sms.SmsDataSource
import com.coparenting.chronicle.horizon.domain.model.MessageType
import com.coparenting.chronicle.horizon.domain.repository.ManualJournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isLoading: Boolean = false
)

data class AiAssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val hasApiKey: Boolean = false,
    val errorBanner: String? = null
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    application: Application,
    private val journalRepository: ManualJournalRepository,
    private val smsDataSource: SmsDataSource,
    private val claudeApiService: ClaudeApiService,
    private val openRouterApiService: OpenRouterApiService,
    private val preferences: AppPreferences
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AiAssistantUiState())
    val state: StateFlow<AiAssistantUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.claudeApiKey,
                preferences.openRouterApiKey,
                preferences.aiProvider
            ) { claudeKey, orKey, provider ->
                val hasKey = if (provider == "openrouter") orKey.isNotBlank() else claudeKey.isNotBlank()
                _state.update { it.copy(hasApiKey = hasKey) }
            }.collect()
        }
    }

    fun setInput(text: String) = _state.update { it.copy(inputText = text) }

    fun sendMessage(hasSmsPermission: Boolean) {
        val question = _state.value.inputText.trim()
        if (question.isBlank()) return

        _state.update { s ->
            s.copy(
                inputText = "",
                messages = s.messages + ChatMessage(isUser = true, text = question),
                isLoading = true,
                errorBanner = null
            )
        }

        viewModelScope.launch {
            val provider = preferences.aiProvider.first()
            val apiKey = if (provider == "openrouter") preferences.openRouterApiKey.first()
                         else preferences.claudeApiKey.first()

            if (apiKey.isBlank()) {
                val providerName = if (provider == "openrouter") "OpenRouter" else "Claude"
                appendAssistantMessage("Please add your $providerName API key in Settings to use the AI assistant.")
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            val context = buildContext(question, hasSmsPermission)

            val result = if (provider == "openrouter") {
                val modelId = preferences.selectedOpenRouterModel.first()
                openRouterApiService.answerQuestion(question, context, apiKey, modelId)
            } else {
                claudeApiService.answerQuestion(question, context, apiKey)
            }

            result.fold(
                onSuccess = { answer ->
                    appendAssistantMessage(answer)
                    _state.update { it.copy(isLoading = false) }
                },
                onFailure = { e ->
                    appendAssistantMessage("I encountered an error: ${e.message ?: "Unknown error"}. Please try again.")
                    _state.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    private suspend fun buildContext(question: String, hasSmsPermission: Boolean): String {
        val keywords = extractKeywords(question)
        val sb = StringBuilder()

        val journalResults = journalRepository.search(keywords.firstOrNull() ?: question).first().take(10)
        if (journalResults.isNotEmpty()) {
            sb.append("=== Journal Entries ===\n")
            journalResults.forEach { entry ->
                val date = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                val time = entry.timestamp.format(DateTimeFormatter.ofPattern("h:mm a"))
                sb.append("[$date at $time] ${entry.title.ifBlank { "Entry" }}: ${entry.content}\n\n")
            }
        }

        if (hasSmsPermission && keywords.isNotEmpty()) {
            val phone = preferences.coParentPhone.first()
            val smsResults = runCatching {
                smsDataSource.searchMessages(keywords)
                    .filter { phone.isBlank() || it.address.contains(phone.takeLast(7)) }
                    .take(20)
            }.getOrElse { emptyList() }

            if (smsResults.isNotEmpty()) {
                sb.append("=== Text Messages ===\n")
                smsResults.forEach { msg ->
                    val date = msg.timestamp.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                    val who = if (msg.type == MessageType.INCOMING) "Co-parent" else "Me"
                    sb.append("[$date - $who]: ${msg.body}\n")
                }
            }
        }

        if (sb.isBlank()) {
            val recent = journalRepository.getEntriesSince(LocalDateTime.now().minusDays(90)).take(15)
            if (recent.isNotEmpty()) {
                sb.append("=== Recent Journal Entries (last 90 days) ===\n")
                recent.forEach { entry ->
                    val date = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                    sb.append("[$date] ${entry.title.ifBlank { "Entry" }}: ${entry.content.take(200)}\n\n")
                }
            }
        }

        return sb.toString().ifBlank { "No relevant records found in the last 90 days." }
    }

    private fun extractKeywords(question: String): List<String> {
        val stopWords = setOf("the", "a", "an", "is", "was", "did", "do", "does", "i", "me", "my",
            "she", "he", "her", "his", "they", "we", "on", "in", "at", "to", "of", "and", "or",
            "that", "this", "it", "if", "for", "with", "about", "what", "when", "where", "how",
            "could", "would", "should", "can", "will", "ask", "asked", "tell", "said", "say")
        return question.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
            .take(8)
    }

    private fun appendAssistantMessage(text: String) {
        _state.update { s -> s.copy(messages = s.messages + ChatMessage(isUser = false, text = text)) }
    }
}
