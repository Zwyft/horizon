package com.zwyft.horizon.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.JournalEntryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for the Journal screen.
 */
class JournalViewModel(
    private val db: HorizonDatabase
) : ViewModel() {

    private val journalDao = db.journalEntryDao()

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        observeJournalEntries()
    }

    private fun observeJournalEntries() {
        viewModelScope.launch {
            journalDao.observeAll().collect { entries ->
                _uiState.update { it.copy(entries = entries, loading = false) }
            }
        }
    }

    /**
     * Manually trigger journal generation for a date range.
     */
    fun generateForRange(start: Date, end: Date) {
        _uiState.update { it.copy(generating = true) }
        viewModelScope.launch {
            // Fetch API key from settings
            val settingDao = db.settingDao()
            val apiKey = settingDao.getValue("nous_api_key") ?: run {
                _uiState.update { it.copy(generating = false, error = "NousResearch API key not set") }
                return@launch
            }

            val repo = JournalRepository(db, apiKey)
            val entry = repo.generateJournalEntry(start, end)

            _uiState.update {
                it.copy(
                    generating = false,
                    lastEntry = entry,
                    error = if (entry == null) "Failed to generate journal entry" else null
                )
            }
        }
    }

    /**
     * Toggle bookmark on a journal entry.
     */
    fun toggleBookmark(entry: JournalEntryEntity) {
        viewModelScope.launch {
            journalDao.setBookmarked(entry.id, !entry.bookmarked)
        }
    }
}

data class JournalUiState(
    val entries: List<JournalEntryEntity> = emptyList(),
    val loading: Boolean = true,
    val generting: Boolean = false,
    val lastEntry: JournalEntryEntity? = null,
    val error: String? = null
)
