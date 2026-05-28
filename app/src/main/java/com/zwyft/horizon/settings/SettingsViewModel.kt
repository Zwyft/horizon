package com.zwyft.horizon.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.dao.SettingDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Settings screen — wraps all SettingDao operations.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingDao: SettingDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val apiKey  = settingDao.getValue("nous_api_key") ?: ""
            val encryption = settingDao.getValue("encryption_enabled") == "true"
            val cloudSync = settingDao.getValue("cloud_sync_enabled") == "true"
            _uiState.update {
                it.copy(
                    apiKey = apiKey,
                    encryptionEnabled = encryption,
                    cloudSyncEnabled = cloudSync,
                    loading = false
                )
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingDao.setValue("nous_api_key", key)
            _uiState.update { it.copy(apiKey = key, apiKeySaved = true) }
        }
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingDao.setValue("encryption_enabled", enabled.toString())
            _uiState.update { it.copy(encryptionEnabled = enabled) }
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingDao.setValue("cloud_sync_enabled", enabled.toString())
            _uiState.update { it.copy(cloudSyncEnabled = enabled) }
        }
    }

    fun clearApiKeySaved() {
        _uiState.update { it.copy(apiKeySaved = false) }
    }
}

data class SettingsUiState(
    val loading: Boolean = true,
    val apiKey: String = "",
    val apiKeySaved: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val error: String? = null
)
