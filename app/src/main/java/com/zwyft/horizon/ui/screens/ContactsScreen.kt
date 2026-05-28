package com.zwyft.horizon.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.contacts.ContactViewModel
import com.zwyft.horizon.data.entity.ContactEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: androidx.navigation.NavController,
    viewModel: ContactViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ContactEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Monitored Contacts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No monitored contacts yet. Tap + to add.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.contacts) { contact ->
                        ContactCard(
                            contact = contact,
                            onToggleMonitored = { viewModel.toggleMonitored(contact) },
                            onEdit = { editingContact = contact },
                            onDelete = { viewModel.removeContact(contact) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditContactDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, relationship ->
                viewModel.addContact(name, phone, relationship)
                showAddDialog = false
            }
        )
    }

    if (editingContact != null) {
        AddEditContactDialog(
            contact = editingContact,
            onDismiss = { editingContact = null },
            onSave = { name, phone, relationship ->
                // update logic
                editingContact = null
            }
        )
    }
}

@Composable
fun ContactCard(
    contact: ContactEntity,
    onToggleMonitored: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (contact.monitored) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                Text(contact.phoneNumber, style = MaterialTheme.typography.bodyMedium)
                if (contact.relationship != null) {
                    Text(contact.relationship, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row {
                Switch(checked = contact.monitored, onCheckedChange = { onToggleMonitored() })
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun AddEditContactDialog(
    contact: ContactEntity? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, relationship: String?) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var phone by remember { mutableStateOf(contact?.phoneNumber ?: "") }
    var relationship by remember { mutableStateOf(contact?.relationship ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (contact == null) "Add Contact" else "Edit Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                OutlinedTextField(value = relationship, onValueChange = { relationship = it }, label = { Text("Relationship (optional)") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, phone, relationship.ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
