package com.zwyft.horizon.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.JournalEntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for the Journal screen.
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
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

    // ── Journal Edit / Annotate ─────────────────────────────────

    /**
     * Save user annotation notes for a journal entry.
     * Also marks the entry as user-edited so the UI can distinguish
     * AI-generated content from user additions.
     */
    fun saveAnnotation(entryId: Long, notes: String) {
        viewModelScope.launch {
            journalDao.setUserNotes(entryId, notes)
            _uiState.update { state ->
                state.copy(entries = state.entries.map {
                    if (it.id == entryId) it.copy(userNotes = notes, userEdited = true) else it
                })
            }
        }
    }

    /**
     * Regenerate the AI body for an existing journal entry,
     * preserving any user-written notes.
     */
    fun regenerateEntry(entry: JournalEntryEntity) {
        _uiState.update { it.copy(generating = true) }
        viewModelScope.launch {
            val settingDao = db.settingDao()
            val apiKey = settingDao.getValue("nous_api_key") ?: run {
                _uiState.update { it.copy(generating = false, error = "NousResearch API key not set") }
                return@launch
            }

            val repo = JournalRepository(db, apiKey)
            val newEntry = repo.generateJournalEntry(entry.dateStart, entry.dateEnd)

            if (newEntry != null) {
                // Preserve user notes from the old entry
                if (entry.userNotes != null) {
                    journalDao.setUserNotes(newEntry.id, entry.userNotes)
                }
                _uiState.update { it.copy(generating = false, lastEntry = newEntry) }
            } else {
                _uiState.update { it.copy(generating = false, error = "Failed to regenerate journal entry") }
            }
        }
    }

    /**
     * Fetch a single journal entry by ID.
     */
    fun getEntryFlow(entryId: Long) = journalDao.observeById(entryId)

    /**
     * Clear any error state (e.g. after the user dismisses an error dialog).
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class JournalUiState(
    val entries: List<JournalEntryEntity> = emptyList(),
    val loading: Boolean = true,
    val generating: Boolean = false,
    val lastEntry: JournalEntryEntity? = null,
    val error: String? = null
)
