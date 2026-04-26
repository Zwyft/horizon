package com.coparenting.chronicle.horizon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.data.preferences.AppPreferences
import com.coparenting.chronicle.horizon.data.remote.openrouter.FREE_JOURNAL_MODELS
import com.coparenting.chronicle.horizon.data.remote.openrouter.OpenRouterModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val coParentName: String = "",
    val coParentPhone: String = "",
    val claudeApiKey: String = "",
    val showSmsInTimeline: Boolean = true,
    val openRouterApiKey: String = "",
    val aiProvider: String = "claude",
    val selectedOpenRouterModel: String = "meta-llama/llama-3.3-70b-instruct:free",
    val availableModels: List<OpenRouterModel> = FREE_JOURNAL_MODELS
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.coParentName,
                preferences.coParentPhone,
                preferences.claudeApiKey,
                preferences.showSmsInTimeline,
                preferences.openRouterApiKey
            ) { name, phone, claudeKey, showSms, orKey ->
                _state.update {
                    it.copy(
                        coParentName = name,
                        coParentPhone = phone,
                        claudeApiKey = claudeKey,
                        showSmsInTimeline = showSms,
                        openRouterApiKey = orKey
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            combine(
                preferences.aiProvider,
                preferences.selectedOpenRouterModel
            ) { provider, model ->
                _state.update { it.copy(aiProvider = provider, selectedOpenRouterModel = model) }
            }.collect()
        }
    }

    fun setCoParentName(v: String) = viewModelScope.launch { preferences.setCoParentName(v) }
    fun setCoParentPhone(v: String) = viewModelScope.launch { preferences.setCoParentPhone(v) }
    fun setClaudeApiKey(v: String) = viewModelScope.launch { preferences.setClaudeApiKey(v) }
    fun setShowSms(v: Boolean) = viewModelScope.launch { preferences.setShowSmsInTimeline(v) }
    fun setOpenRouterApiKey(v: String) = viewModelScope.launch { preferences.setOpenRouterApiKey(v) }
    fun setAiProvider(v: String) = viewModelScope.launch { preferences.setAiProvider(v) }
    fun setSelectedOpenRouterModel(v: String) = viewModelScope.launch { preferences.setSelectedOpenRouterModel(v) }
}
