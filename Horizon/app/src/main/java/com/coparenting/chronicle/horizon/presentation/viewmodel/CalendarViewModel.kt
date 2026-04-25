package com.coparenting.chronicle.horizon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import com.coparenting.chronicle.horizon.domain.repository.ManualJournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDateTime = LocalDateTime.now().toLocalDate().atStartOfDay(),
    val datesWithEntries: Set<LocalDateTime> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<ManualJournalEntry> = emptyList(),
    val isSearchActive: Boolean = false
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val journalRepository: ManualJournalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            journalRepository.getDatesWithEntries().collect { dates ->
                _uiState.update { it.copy(datesWithEntries = dates.toSet()) }
            }
        }
    }

    fun prevMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
    }

    fun nextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
    }

    fun selectDate(date: LocalDateTime) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchActive = query.isNotBlank()) }
        if (query.isNotBlank()) {
            viewModelScope.launch {
                journalRepository.search(query).collect { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", isSearchActive = false, searchResults = emptyList()) }
    }
}
