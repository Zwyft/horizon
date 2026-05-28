package com.zwyft.horizon.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

/**
 * ViewModel for the Export screen.
 */
class ExportViewModel(
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val db: HorizonDatabase = HorizonDatabase.getInstance(context)

    /**
     * Generate PDF export.
     */
    fun generatePdf(
        startDate: Date? = null,
        endDate: Date? = null,
        contacts: List<Long> = emptyList()
    ) {
        _uiState.update { it.copy(exporting = true) }

        viewModelScope.launch {
            try {
                val outputFile = File(context.getExternalFilesDir(null), "horizon_export_${Date().time}.pdf")
                val exporter = PdfExporter(context)
                val hash = exporter.generatePdf(startDate, endDate, contacts, outputFile)

                _uiState.update {
                    it.copy(
                        exporting = false,
                        lastExportPath = outputFile.absolutePath,
                        lastExportHash = hash,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(exporting = false, error = e.message) }
            }
        }
    }
}

data class ExportUiState(
    val exporting: Boolean = false,
    val lastExportPath: String? = null,
    val lastExportHash: String? = null,
    val error: String? = null
)
