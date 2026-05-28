package com.zwyft.horizon.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwyft.horizon.data.entity.ContactEntity
import com.zwyft.horizon.repository.ContactRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Contacts screen.
 */
class ContactViewModel(
    private val repo: ContactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    init {
        observeMonitoredContacts()
        repo.seedDefaults()   // seed on first launch
    }

    private fun observeMonitoredContacts() {
        viewModelScope.launch {
            repo.getMonitoredContacts().collect { contacts ->
                _uiState.update { it.copy(contacts = contacts) }
            }
        }
    }

    fun addContact(name: String, phone: String, relationship: String? = null) {
        viewModelScope.launch {
            repo.addContact(name, phone, relationship)
        }
    }

    fun removeContact(contact: ContactEntity) {
        viewModelScope.launch {
            repo.removeContact(contact)
        }
    }

    fun toggleMonitored(contact: ContactEntity) {
        viewModelScope.launch {
            repo.toggleMonitored(contact.id, !contact.monitored)
        }
    }
}

data class ContactUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)
