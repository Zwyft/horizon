package com.coparenting.chronicle.horizon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.domain.model.AnalyticsReport
import com.coparenting.chronicle.horizon.domain.usecase.analytics.GenerateAnalyticsReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val generateAnalyticsReportUseCase: GenerateAnalyticsReportUseCase
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val report: AnalyticsReport) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadAnalyticsReport() }

    fun loadAnalyticsReport(userId: String = "current_user") {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val report = generateAnalyticsReportUseCase(userId)
                _uiState.value = UiState.Success(report)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }
}
