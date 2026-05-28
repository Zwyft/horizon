package com.zwyft.horizon.importing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.zwyft.horizon.R
import kotlinx.coroutines.channels.awlish
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Import screen.
 * Handles file selection, enqueuing ImportWorker, observing progress.
 */
class ImportViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ImportEvent>()
    val events = _events.asSharedFlow()

    /**
     * Called when user picks a file (from SAF file picker).
     */
    fun onFileSelected(context: Context, uri: Uri, batchTag: String? = null) {
        val path = uri.path ?: run {
            viewModelScope.launch {
                _events.emit(ImportEvent.Error("Cannot read file path"))
            }
            return
        }
        val file = java.io.File(path)
        if (!file.exists()) {
            viewModelScope.launch {
                _events.emit(ImportEvent.Error("File not found"))
            }
            return
        }

        _uiState.update { it.copy(importing = true, filePath = path) }

        val workId = ImportWorker.enqueue(context, file, batchTag)
        observeWorker(context, workId)
    }

    private fun observeWorker(context: Context, workId: java.util.UUID) {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfoByIdFlow(workId)
                .collect { info ->
                    when (info?.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getInt(ImportWorker.KEY_PROGRESS, 0)
                            _uiState.update { it.copy(progress = progress) }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val total = info.outputData.getInt(ImportWorker.KEY_DONE, 0)
                            _uiState.update { it.copy(importing = false, totalImported = total) }
                            _events.emit(ImportEvent.Done(total))
                        }
                        WorkInfo.State.FAILED -> {
                            val error = info.outputData.getString(ImportWorker.KEY_ERROR) ?: "Unknown error"
                            _uiState.update { it.copy(importing = false) }
                            _events.emit(ImportEvent.Error(error))
                        }
                        else -> {}
                    }
                }
        }
    }
}

data class ImportUiState(
    val importing: Boolean = false,
    val progress: Int = 0,
    val totalImported: Int = 0,
    val filePath: String? = null
)

sealed class ImportEvent {
    data class Done(val total: Int) : ImportEvent()
    data class Error(val message: String) : ImportEvent()
}
