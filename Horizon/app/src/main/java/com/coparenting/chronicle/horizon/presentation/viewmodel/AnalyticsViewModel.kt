package com.coparenting.chronicle.horizon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.domain.usecase.analytics.GenerateAnalyticsReportUseCase
import com.coparenting.chronicle.horizon.domain.model.AnalyticsReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val generateAnalyticsReportUseCase: GenerateAnalyticsReportUseCase
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Analytics data
    private val _analyticsReport = MutableStateFlow<AnalyticsReport?>(null)
    val analyticsReport: StateFlow<AnalyticsReport?> = _analyticsReport.asStateFlow()

    init {
        loadAnalyticsReport()
    }

    fun loadAnalyticsReport(userId: String = "current_user") {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val report = generateAnalyticsReportUseCase(userId)
                _analyticsReport.value = report
                _uiState.value = UiState.Success(report)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun refreshAnalytics(userId: String = "current_user") {
        loadAnalyticsReport(userId)
    }

    // UI State sealed class
    sealed class UiState {
        object Loading : UiState()
        data class Success(val analyticsReport: AnalyticsReport) : UiState()
        data class Error(val message: String) : UiState()
    }
}
