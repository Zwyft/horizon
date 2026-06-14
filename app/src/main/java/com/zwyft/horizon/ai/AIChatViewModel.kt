package com.zwyft.horizon.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageEntity
import com.zwyft.horizon.service.local.LocalLlmServerService
import com.zwyft.horizon.service.local.LocalModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for the AI Chat screen.
 */
@HiltViewModel
class AIChatViewModel @Inject constructor(
    application: Application,
    private val db: HorizonDatabase
) : AndroidViewModel(application) {

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
                val settingDao = db.settingDao()

                // Resolve provider
                val providerName = settingDao.getValue("ai_provider") ?: "NOUS"
                val provider = when (providerName.uppercase()) {
                    "OPENROUTER" -> AiProvider.OPENROUTER
                    "LOCAL"      -> AiProvider.LOCAL
                    else         -> AiProvider.NOUS
                }

                // Resolve API key
                val apiKey = when (provider) {
                    AiProvider.NOUS       -> settingDao.getValue("nous_api_key")
                    AiProvider.OPENROUTER -> settingDao.getValue("openrouter_api_key")
                    AiProvider.LOCAL      -> "local-no-key"
                }

                if (apiKey == null && provider != AiProvider.LOCAL) {
                    _uiState.update {
                        it.copy(loading = false, error = "API key not set. Configure in Settings.")
                    }
                    return@launch
                }

                // LOCAL provider pre-flight checks
                if (provider == AiProvider.LOCAL) {
                    val modelId = settingDao.getValue("local_ai_model")
                        ?: ModelRegistry.LOCAL_GEMMA3_1B
                    if (!LocalModelManager.isModelDownloaded(
                            getApplication(),
                            modelId
                        )
                    ) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                error = "Local model not downloaded. Open Settings → Local AI to download."
                            )
                        }
                        return@launch
                    }
                    if (!LocalLlmServerService.isRunning) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                error = "Local AI not running. Tap 'Start' in Settings."
                            )
                        }
                        return@launch
                    }
                }

                val resolvedKey = apiKey ?: "local-no-key"
                val repo = AIChatRepository(db, resolvedKey, provider)
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
