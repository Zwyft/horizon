package com.zwyft.horizon.cloud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Drive sync settings.
 */
@HiltViewModel
class DriveViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DriveUiState())
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private val db = HorizonDatabase.getInstance(application)
    private val repo = DriveRepository(application, db)

    init {
        checkSignIn()
    }

    private fun checkSignIn() {
        _uiState.update { it.copy(signedIn = repo.isSignedIn()) }
    }

    fun getSignInIntent() = repo.getSignInIntent()

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

    fun refreshBackups() {
        listBackups()
    }

    fun restoreBackup(fileId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(restoring = true) }
            val success = repo.restoreBackup(fileId)
            _uiState.update { it.copy(restoring = false, lastRestoreSucceeded = success, error = if (!success) "Restore failed" else null) }
        }
    }

    fun deleteBackup(fileId: String) {
        viewModelScope.launch {
            repo.deleteBackup(fileId)
            listBackups()
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
    val lastRestoreSucceeded: Boolean? = null,
    val error: String? = null
)
