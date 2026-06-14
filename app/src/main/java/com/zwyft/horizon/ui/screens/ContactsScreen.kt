package com.zwyft.horizon.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.contacts.ContactViewModel
import com.zwyft.horizon.data.entity.ContactEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: androidx.navigation.NavController,
    viewModel: ContactViewModel = hiltViewModel()
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ContactEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Monitored Contacts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        ContactsContent(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            showAddDialog = showAddDialog,
            onShowAddDialog = { showAddDialog = it },
            editingContact = editingContact,
            onEditingContact = { editingContact = it }
        )
    }
}

@Composable
fun ContactsContent(
    modifier: Modifier = Modifier,
    viewModel: ContactViewModel = hiltViewModel(),
    showAddDialog: Boolean = false,
    onShowAddDialog: (Boolean) -> Unit = {},
    editingContact: ContactEntity? = null,
    onEditingContact: (ContactEntity?) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.contacts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        Icons.Filled.Contacts,
                        contentDescription = null,
                        modifier = Modifier.padding(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("No monitored contacts", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add contacts to track their messages in your journal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.contacts) { contact ->
                    ContactCardEnhanced(
                        contact = contact,
                        onToggleMonitored = { viewModel.toggleMonitored(contact) },
                        onEdit = { onEditingContact(contact) },
                        onDelete = { viewModel.removeContact(contact) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditContactDialogEnhanced(
            onDismiss = { onShowAddDialog(false) },
            onSave = { name, phone, relationship ->
                viewModel.addContact(name, phone, relationship)
                onShowAddDialog(false)
            }
        )
    }

    if (editingContact != null) {
        AddEditContactDialogEnhanced(
            contact = editingContact,
            onDismiss = { onEditingContact(null) },
            onSave = { name, phone, relationship ->
                onEditingContact(null)
            }
        )
    }
}

@Composable
private fun ContactCardEnhanced(
    contact: ContactEntity,
    onToggleMonitored: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val initials = contact.name.split(" ").take(2).joinToString("") { it.firstOrNull()?.toString() ?: "" }.uppercase()
    val avatarColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    )
    val avatarColor = avatarColors[contact.id.toInt() % avatarColors.size]

    val relationshipLabel = when (contact.relationship?.lowercase()) {
        "mom", "mother" -> "Mother"
        "dad", "father" -> "Father"
        "grandparent", "grandma", "grandpa" -> "Grandparent"
        "babysitter", "nanny" -> "Babysitter"
        else -> contact.relationship?.replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (contact.monitored)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (contact.monitored) 1.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                shape = CircleShape,
                color = avatarColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name + details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (contact.monitored) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                }

                if (contact.phoneNumber.isNotBlank()) {
                    Text(
                        contact.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (relationshipLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            relationshipLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Switch(
                    checked = contact.monitored,
                    onCheckedChange = { onToggleMonitored() }
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AddEditContactDialogEnhanced(
    contact: ContactEntity? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, relationship: String?) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var phone by remember { mutableStateOf(contact?.phoneNumber ?: "") }
    var relationship by remember { mutableStateOf(contact?.relationship ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (contact == null) Icons.Filled.PersonAdd else Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(if (contact == null) "Add Contact" else "Edit Contact")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship") },
                    placeholder = { Text("Mom, Dad, Grandparent, Babysitter...") },
                    leadingIcon = { Icon(Icons.Filled.FavoriteBorder, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, phone, relationship.ifBlank { null }) }) {
                Text(if (contact == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
