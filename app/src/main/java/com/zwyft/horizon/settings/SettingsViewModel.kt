package com.zwyft.horizon.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.ai.AiProvider
import com.zwyft.horizon.ai.ModelRegistry
import com.zwyft.horizon.data.dao.SettingDao
import com.zwyft.horizon.service.local.LocalLlmServerService
import com.zwyft.horizon.service.local.LocalModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Settings screen — wraps all SettingDao operations
 * plus local AI state management.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingDao: SettingDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeDownloadProgress()
        observeServerStatus()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val apiKey       = settingDao.getValue("nous_api_key") ?: ""
            val openrouterKey = settingDao.getValue("openrouter_api_key") ?: ""
            val encryption   = settingDao.getValue("encryption_enabled") == "true"
            val cloudSync    = settingDao.getValue("cloud_sync_enabled") == "true"
            val localEnabled = settingDao.getValue("local_ai_enabled") == "true"
            val localModel   = settingDao.getValue("local_ai_model") ?: ModelRegistry.LOCAL_GEMMA3_1B
            val providerName = settingDao.getValue("ai_provider") ?: "NOUS"
            val provider = when (providerName.uppercase()) {
                "OPENROUTER" -> AiProvider.OPENROUTER
                "LOCAL"      -> AiProvider.LOCAL
                else         -> AiProvider.NOUS
            }

            _uiState.update {
                it.copy(
                    apiKey = apiKey,
                    openrouterKey = openrouterKey,
                    encryptionEnabled = encryption,
                    cloudSyncEnabled = cloudSync,
                    localAiEnabled = localEnabled,
                    localAiModelId = localModel,
                    localAiModelDownloaded = LocalModelManager.isModelDownloaded(
                        getApplication(), localModel
                    ),
                    selectedProvider = provider,
                    loading = false
                )
            }
        }
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            LocalModelManager.downloadState.collect { ds ->
                _uiState.update {
                    it.copy(
                        localAiDownloading = ds.downloading,
                        localAiDownloadBytes = ds.bytesDownloaded,
                        localAiDownloadTotal = ds.totalBytes,
                        localAiDownloadError = ds.error
                    )
                }
            }
        }
    }

    private fun observeServerStatus() {
        viewModelScope.launch {
            // Poll the server running flag periodically
            while (true) {
                _uiState.update {
                    it.copy(localAiServerRunning = LocalLlmServerService.isRunning)
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingDao.setValue("nous_api_key", key)
            _uiState.update { it.copy(apiKey = key, apiKeySaved = true) }
        }
    }

    fun saveOpenRouterKey(key: String) {
        viewModelScope.launch {
            settingDao.setValue("openrouter_api_key", key)
            _uiState.update { it.copy(openrouterKey = key, apiKeySaved = true) }
        }
    }

    fun setSelectedProvider(provider: AiProvider) {
        viewModelScope.launch {
            settingDao.setValue("ai_provider", provider.name)
            _uiState.update { it.copy(selectedProvider = provider) }
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

    // ── Local AI ───────────────────────────────────────────

    fun setLocalAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingDao.setValue("local_ai_enabled", enabled.toString())
            _uiState.update { it.copy(localAiEnabled = enabled) }

            if (enabled) {
                val modelId = _uiState.value.localAiModelId
                if (LocalModelManager.isModelDownloaded(getApplication(), modelId)) {
                    LocalLlmServerService.start(getApplication(), modelId)
                }
            } else {
                LocalLlmServerService.stop(getApplication())
            }
        }
    }

    fun setLocalAiModel(modelId: String) {
        viewModelScope.launch {
            settingDao.setValue("local_ai_model", modelId)
            val downloaded = LocalModelManager.isModelDownloaded(getApplication(), modelId)
            _uiState.update {
                it.copy(
                    localAiModelId = modelId,
                    localAiModelDownloaded = downloaded
                )
            }
        }
    }

    fun downloadLocalModel() {
        viewModelScope.launch {
            val modelId = _uiState.value.localAiModelId
            val result = LocalModelManager.downloadModel(getApplication(), modelId)
            result.onSuccess {
                _uiState.update { it.copy(localAiModelDownloaded = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(localAiDownloadError = e.message) }
            }
        }
    }

    fun clearApiKeySaved() {
        _uiState.update { it.copy(apiKeySaved = false) }
    }
}

data class SettingsUiState(
    val loading: Boolean = true,
    val apiKey: String = "",
    val openrouterKey: String = "",
    val apiKeySaved: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val error: String? = null,

    // ── AI Provider ────────────────────────────────────────
    val selectedProvider: AiProvider = AiProvider.NOUS,

    // ── Local AI ───────────────────────────────────────────
    val localAiEnabled: Boolean = false,
    val localAiModelId: String = ModelRegistry.LOCAL_GEMMA3_1B,
    val localAiModelDownloaded: Boolean = false,
    val localAiServerRunning: Boolean = false,
    val localAiDownloading: Boolean = false,
    val localAiDownloadBytes: Long = 0,
    val localAiDownloadTotal: Long = 0,
    val localAiDownloadError: String? = null
)
