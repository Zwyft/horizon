package com.coparenting.chronicle.horizon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val coParentName: String = "",
    val coParentPhone: String = "",
    val claudeApiKey: String = "",
    val showSmsInTimeline: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        preferences.coParentName,
        preferences.coParentPhone,
        preferences.claudeApiKey,
        preferences.showSmsInTimeline
    ) { name, phone, key, showSms ->
        SettingsUiState(name, phone, key, showSms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setCoParentName(v: String) = viewModelScope.launch { preferences.setCoParentName(v) }
    fun setCoParentPhone(v: String) = viewModelScope.launch { preferences.setCoParentPhone(v) }
    fun setClaudeApiKey(v: String) = viewModelScope.launch { preferences.setClaudeApiKey(v) }
    fun setShowSms(v: Boolean) = viewModelScope.launch { preferences.setShowSmsInTimeline(v) }
}
