package com.zwyft.horizon.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zwyft.horizon.ai.JournalViewModel
import com.zwyft.horizon.data.entity.JournalEntryEntity
import java.text.SimpleDateFormat
import java.util.*

// ── Journal Detail Screen (with Edit/Annotate) ─────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    navController: NavController,
    entryId: Long,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val entry by viewModel.getEntryFlow(entryId).collectAsState(initial = null)
    var showEditDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        entry?.userNotes?.let { editText = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal Entry") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Edit / Annotate button
                    IconButton(onClick = {
                        editText = entry?.userNotes ?: ""
                        showEditDialog = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit notes")
                    }
                    // Regenerate button
                    IconButton(onClick = { showRegenerateConfirm = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Regenerate")
                    }
                }
            )
        }
    ) { padding ->
        if (entry == null && !uiState.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Entry not found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            JournalDetailContent(
                entry = entry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }

        // Loading indicator during regenerate
        if (uiState.generating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    // ── Edit Annotation Dialog ──────────────────────────────
    if (showEditDialog && entry != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Your Notes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add your personal notes or annotations. " +
                        "These are preserved if the AI entry is regenerated.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text("Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveAnnotation(entryId, editText)
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Regenerate Confirm Dialog ───────────────────────────
    if (showRegenerateConfirm && entry != null) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("Regenerate Entry?") },
            text = {
                Text("This will replace the AI-generated body with a new version. " +
                     "Your personal notes will be preserved.")
            },
            confirmButton = {
                Button(onClick = {
                    showRegenerateConfirm = false
                    entry?.let { viewModel.regenerateEntry(it) }
                }) { Text("Regenerate") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Error dialog
    uiState.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun JournalDetailContent(
    entry: JournalEntryEntity?,
    modifier: Modifier = Modifier
) {
    if (entry == null) return

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = entry.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Date range
        Text(
            text = "${dateFormat.format(entry.dateStart)} — ${dateFormat.format(entry.dateEnd)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Tags
        if (!entry.tags.isNullOrBlank()) {
            Text(
                text = "Tags: ${entry.tags}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        // AI body
        if (entry.summary != null) {
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = entry.body,
            style = MaterialTheme.typography.bodyMedium
        )

        // User Notes section (if present)
        if (!entry.userNotes.isNullOrBlank()) {
            HorizontalDivider()
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Your Notes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.userNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Sentiment (if available)
        entry.sentimentOverall?.let { sentiment ->
            val label = when {
                sentiment > 0.3f -> "Positive"
                sentiment < -0.3f -> "Negative"
                else -> "Neutral"
            }
            Text(
                text = "Sentiment: $label (${sentiment})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Generation metadata
        entry.modelUsed?.let { model ->
            Text(
                text = "Generated by $model",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
