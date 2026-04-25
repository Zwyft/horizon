package com.coparenting.chronicle.horizon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import com.coparenting.chronicle.horizon.domain.repository.ManualJournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

val ALL_TAGS = listOf("Handoff", "Pickup", "Dropoff", "Incident", "Medical", "School", "Expense", "Communication", "Violation", "Note")

data class EntryEditorUiState(
    val title: String = "",
    val content: String = "",
    val selectedTags: Set<String> = emptySet(),
    val isImportant: Boolean = false,
    val date: LocalDateTime = LocalDateTime.now().toLocalDate().atStartOfDay(),
    val existingId: String? = null,
    val isSaved: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class EntryEditorViewModel @Inject constructor(
    private val repository: ManualJournalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EntryEditorUiState())
    val state: StateFlow<EntryEditorUiState> = _state.asStateFlow()

    fun initNew(date: LocalDateTime) {
        _state.value = EntryEditorUiState(date = date)
    }

    fun loadEntry(id: String) {
        viewModelScope.launch {
            val entry = repository.getById(id) ?: return@launch
            _state.value = EntryEditorUiState(
                title = entry.title,
                content = entry.content,
                selectedTags = entry.tags.split(",").filter { it.isNotBlank() }.toSet(),
                isImportant = entry.isImportant,
                date = entry.date,
                existingId = id
            )
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setContent(v: String) = _state.update { it.copy(content = v) }
    fun toggleTag(tag: String) = _state.update { s ->
        val tags = s.selectedTags.toMutableSet()
        if (!tags.add(tag)) tags.remove(tag)
        s.copy(selectedTags = tags)
    }
    fun toggleImportant() = _state.update { it.copy(isImportant = !it.isImportant) }

    fun save() {
        val s = _state.value
        if (s.content.isBlank()) {
            _state.update { it.copy(saveError = "Entry cannot be empty.") }
            return
        }
        viewModelScope.launch {
            val entry = ManualJournalEntry(
                id = s.existingId ?: java.util.UUID.randomUUID().toString(),
                date = s.date,
                timestamp = if (s.existingId != null) {
                    repository.getById(s.existingId)?.timestamp ?: LocalDateTime.now()
                } else LocalDateTime.now(),
                lastModified = LocalDateTime.now(),
                title = s.title,
                content = s.content,
                tags = s.selectedTags.joinToString(","),
                isImportant = s.isImportant
            )
            runCatching { repository.save(entry) }.fold(
                onSuccess = { _state.update { it.copy(isSaved = true, saveError = null) } },
                onFailure = { e -> _state.update { it.copy(saveError = e.message) } }
            )
        }
    }
}
