package com.zwyft.horizon.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zwyft.horizon.ai.JournalViewModel
import com.zwyft.horizon.data.entity.JournalEntryEntity
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    navController: androidx.navigation.NavController,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showGenerateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Journal") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showGenerateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Generate Journal")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (uiState.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No journal entries yet. Tap + to generate.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.entries) { entry ->
                        JournalCard(
                            entry = entry,
                            onClick = { navController.navigate("journal/${entry.id}") },
                            onBookmark = { viewModel.toggleBookmark(entry) }
                        )
                    }
                }
            }

            if (uiState.generating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
    }

    if (showGenerateDialog) {
        GenerateJournalDialog(
            onDismiss = { showGenerateDialog = false },
            onGenerate = { start, end ->
                viewModel.generateForRange(start, end)
                showGenerateDialog = false
            }
        )
    }
}

@Composable
fun JournalCard(
    entry: JournalEntryEntity,
    onClick: () -> Unit,
    onBookmark: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            if (entry.summary != null) {
                Text(entry.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${entry.dateStart}", style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onBookmark) {
                    Icon(
                        if (entry.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark"
                    )
                }
            }
        }
    }
}

@Composable
fun GenerateJournalDialog(
    onDismiss: () -> Unit,
    onGenerate: (start: Date, end: Date) -> Unit
) {
    var startDate by remember { mutableStateOf(Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000)) }
    var endDate by remember { mutableStateOf(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Journal Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Generate a journal entry from messages in this date range:")
                // Simplified: just show text fields (real app uses DatePicker)
                OutlinedTextField(
                    value = startDate.toString(),
                    onValueChange = { },
                    label = { Text("Start Date") },
                    readOnly = true
                )
                OutlinedTextField(
                    value = endDate.toString(),
                    onValueChange = { },
                    label = { Text("End Date") },
                    readOnly = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onGenerate(startDate, endDate) }) {
                Text("Generate")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
