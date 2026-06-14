package com.zwyft.horizon.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.zwyft.horizon.ai.JournalViewModel
import com.zwyft.horizon.data.entity.JournalEntryEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    navController: androidx.navigation.NavController,
    viewModel: JournalViewModel = hiltViewModel()
) {
    var showGenerateDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Journal") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showGenerateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Generate Journal")
            }
        }
    ) { padding ->
        JournalContent(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            showGenerateDialog = showGenerateDialog,
            onShowGenerateDialog = { showGenerateDialog = it },
            navController = navController
        )
    }
}

@Composable
fun JournalContent(
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = hiltViewModel(),
    showGenerateDialog: Boolean = false,
    onShowGenerateDialog: (Boolean) -> Unit = {},
    navController: androidx.navigation.NavController? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.entries.isEmpty() -> {
                // ── Polished empty state ──
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
                            Icons.Filled.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.padding(20.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No journal entries yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + to generate your first AI-powered journal entry from your messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
            else -> {
                val groupedByMonth = remember(uiState.entries) {
                    uiState.entries.groupBy {
                        val cal = Calendar.getInstance().apply { time = it.dateStart }
                        Calendar.getInstance().apply {
                            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1)
                        }.time
                    }.toSortedMap(reverseOrder())
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedByMonth.forEach { (month, entries) ->
                        item(key = "month-$month") {
                            Text(
                                monthFmt.format(month),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(entries, key = { it.id }) { entry ->
                            JournalCardEnhanced(
                                entry = entry,
                                dateFmt = dateFmt,
                                onClick = { navController?.navigate("journal/${entry.id}") },
                                onBookmark = { viewModel.toggleBookmark(entry) }
                            )
                        }
                    }
                }
            }
        }

        // Generating indicator
        AnimatedVisibility(visible = uiState.generating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Error banner
        uiState.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    err,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    if (showGenerateDialog) {
        GenerateJournalDialogEnhanced(
            onDismiss = { onShowGenerateDialog(false) },
            onGenerate = { start, end ->
                viewModel.generateForRange(start, end)
                onShowGenerateDialog(false)
            }
        )
    }
}

@Composable
private fun JournalCardEnhanced(
    entry: JournalEntryEntity,
    dateFmt: SimpleDateFormat,
    onClick: () -> Unit,
    onBookmark: () -> Unit
) {
    val sentimentColor = entry.sentimentOverall?.let { score ->
        when {
            score > 0.3f -> MaterialTheme.colorScheme.secondary
            score < -0.3f -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.tertiary
        }
    } ?: MaterialTheme.colorScheme.outline

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.bookmarked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            // Sentiment indicator bar
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = sentimentColor
            ) {}

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (entry.bookmarked) {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (entry.summary != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${dateFmt.format(entry.dateStart)} – ${dateFmt.format(entry.dateEnd)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Sentiment label
                        if (entry.sentimentOverall != null) {
                            val label = when {
                                entry.sentimentOverall > 0.3f -> "Positive"
                                entry.sentimentOverall < -0.3f -> "Negative"
                                else -> "Neutral"
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = sentimentColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = sentimentColor
                                )
                            }
                        }

                        // Tags (first 2)
                        entry.tags?.split(",")?.take(2)?.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    tag.trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerateJournalDialogEnhanced(
    onDismiss: () -> Unit,
    onGenerate: (start: Date, end: Date) -> Unit
) {
    var startDate by remember { mutableStateOf(Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)) }
    var endDate by remember { mutableStateOf(Date()) }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Generate Journal Entry")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "AI will analyze your monitored messages and write a thoughtful journal entry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = dateFmt.format(startDate),
                    onValueChange = {},
                    label = { Text("From") },
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateFmt.format(endDate),
                    onValueChange = {},
                    label = { Text("To") },
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onGenerate(startDate, endDate) }) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
