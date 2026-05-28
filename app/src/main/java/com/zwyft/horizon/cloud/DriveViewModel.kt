package com.zwyft.horizon.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Drive sync settings.
 */
class DriveViewModel(
    private val db: HorizonDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveUiState())
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private val repo = DriveRepository(db, db)

    init {
        checkSignIn()
    }

    private fun checkSignIn() {
        _uiState.update { it.copy(signedIn = repo.isSignedIn()) }
    }

    fun signIn(data: android.content.Intent?) {
        viewModelScope.launch {
            val success = repo.handleSignInResult(data)
            _uiState.update { it.copy(signedIn = success) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            _uiState.update { it.copy(signedIn = false) }
        }
    }

    fun uploadBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true) }
            try {
                val fileId = repo.uploadBackup()
                _uiState.update { it.copy(uploading = false, lastBackupId = fileId) }
            } catch (e: Exception) {
                _uiState.update { it.copy(uploading = false, error = e.message) }
            }
        }
    }

    fun listBackups() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingBackups = true) }
            try {
                val backups = repo.listBackups()
                _uiState.update { it.copy(loadingBackups = false, backups = backups) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loadingBackups = false, error = e.message) }
            }
        }
    }

    fun restoreBackup(fileId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(restoring = true) }
            val success = repo.restoreBackup(fileId)
            _uiState.update { it.copy(restoring = false, error = if (!success) "Restore failed" else null) }
        }
    }
}

data class DriveUiState(
    val signedIn: Boolean = false,
    val uploading: Boolean = false,
    val restoring: Boolean = false,
    val loadingBackups: Boolean = false,
    val backups: List<com.google.api.services.drive.model.File> = emptyList(),
    val lastBackupId: String? = null,
    val error: String? = null
)
