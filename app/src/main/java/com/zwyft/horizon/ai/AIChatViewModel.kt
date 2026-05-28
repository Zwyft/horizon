package com.zwyft.horizon.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for the AI Chat screen.
 */
class AIChatViewModel(
    private val db: HorizonDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AIChatEvent>()
    val events = _events.asSharedFlow()

    /**
     * Send a question to the AI.
     */
    fun ask(question: String) {
        if (question.isBlank()) return

        _uiState.update { it.copy(loading = true, error = null) }

        // Add user message to chat history
        val userMessage = ChatMessage(role = "user", content = question)
        _uiState.update { state ->
            state.copy(messages = state.messages + userMessage, loading = true)
        }

        viewModelScope.launch {
            try {
                // Fetch API key from settings
                val settingDao = db.settingDao()
                val apiKey = settingDao.getValue("nous_api_key") ?: run {
                    _uiState.update { it.copy(loading = false, error = "NousResearch API key not set") }
                    return@launch
                }

                val repo = AIChatRepository(db, apiKey)
                val (answer, results) = repo.ask(question)

                // Add AI response to chat history
                val aiMessage = ChatMessage(role = "assistant", content = answer)
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + aiMessage,
                        loading = false,
                        results = results
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}

data class AIChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val results: SearchResults? = null
)

data class ChatMessage(
    val role: String,   // "user" | "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class AIChatEvent {
    data class Error(val message: String) : AIChatEvent()
}
