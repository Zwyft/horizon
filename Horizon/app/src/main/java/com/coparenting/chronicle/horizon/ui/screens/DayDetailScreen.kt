package com.coparenting.chronicle.horizon.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import com.coparenting.chronicle.horizon.domain.model.MessageType
import com.coparenting.chronicle.horizon.presentation.viewmodel.DayDetailViewModel
import com.coparenting.chronicle.horizon.presentation.viewmodel.TimelineItem
import com.coparenting.chronicle.horizon.data.remote.sms.SmsDataSource
import com.coparenting.chronicle.horizon.ui.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    date: LocalDateTime,
    hasSmsPermission: Boolean,
    onBack: () -> Unit,
    onAddEntry: (LocalDateTime) -> Unit,
    onEditEntry: (String) -> Unit,
    onAskAI: () -> Unit,
    viewModel: DayDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(date, hasSmsPermission) {
        viewModel.load(date, hasSmsPermission)
    }

    val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    val yearLabel = date.format(DateTimeFormatter.ofPattern("yyyy"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(dateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(yearLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onAskAI) {
                        Icon(Icons.Default.AutoAwesome, "Ask AI", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { onAddEntry(date) }) {
                        Icon(Icons.Default.Add, "Add entry", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Error banner
            state.generationError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Generate AI Summary button
            if (!state.isGeneratingSummary && state.generatedDiary == null && (state.smsMessages.isNotEmpty() || state.journalEntries.isNotEmpty())) {
                AiSummaryButton(onClick = { viewModel.generateAiSummary() })
            }

            if (state.isGeneratingSummary) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Generating AI diary entry…",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!hasSmsPermission && state.coParentPhone.isNotBlank()) {
                SmsPermissionBanner()
            }

            if (state.timeline.isEmpty() && !state.isGeneratingSummary) {
                EmptyDayMessage(date = date, onAddEntry = { onAddEntry(date) })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.timeline, key = {
                        when (it) {
                            is TimelineItem.JournalItem -> "journal_${it.entry.id}"
                            is TimelineItem.SmsItem -> "sms_${it.msg.id}"
                            is TimelineItem.DiaryItem -> "diary_${it.entry.id}"
                        }
                    }) { item ->
                        when (item) {
                            is TimelineItem.JournalItem -> JournalEntryCard(
                                entry = item.entry,
                                onEdit = { onEditEntry(item.entry.id) },
                                onDelete = { viewModel.deleteEntry(item.entry) }
                            )
                            is TimelineItem.SmsItem -> SmsBubble(msg = item.msg)
                            is TimelineItem.DiaryItem -> AiDiaryCard(content = item.entry.content, title = item.entry.title)
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AiSummaryButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50)
    ) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Generate AI Diary Entry from Messages")
    }
}

@Composable
private fun SmsPermissionBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sms, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "Grant SMS permission in Settings to see text messages here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun EmptyDayMessage(date: LocalDateTime, onAddEntry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.EventNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Text("Nothing recorded for this day.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAddEntry) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Journal Entry")
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: ManualJournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isImportant) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (entry.isImportant) Icons.Default.Star else Icons.Default.BookmarkBorder,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = if (entry.isImportant) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        entry.timestamp.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (entry.title.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(6.dp))
            Text(entry.content, style = MaterialTheme.typography.bodyMedium)

            // Tags
            val tags = entry.tags.split(",").filter { it.isNotBlank() }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TagChipRow(tags)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete this entry? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TagChipRow(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        tags.forEach { tag ->
            TagChip(tag)
        }
    }
}

@Composable
fun TagChip(tag: String) {
    val (bg, fg) = tagColors(tag)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(tag, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

private fun tagColors(tag: String): Pair<Color, Color> = when (tag.lowercase()) {
    "handoff" -> TagHandoff to Color.White
    "pickup" -> TagPickup to Color.White
    "dropoff" -> TagDropoff to Color.White
    "incident" -> TagIncident to Color.White
    "medical" -> TagMedical to Color.White
    "school" -> TagSchool to Color.White
    "expense" -> TagExpense to Color.White
    "communication" -> TagComm to Color.White
    "violation" -> TagViolation to Color.White
    else -> TagNote to Color.White
}

@Composable
private fun SmsBubble(msg: SmsDataSource.SmsMessage) {
    val isIncoming = msg.type == MessageType.INCOMING
    val bubbleColor = if (isIncoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isIncoming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    val alignment = if (isIncoming) Arrangement.Start else Arrangement.End

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isIncoming) 4.dp else 16.dp,
                        bottomEnd = if (isIncoming) 16.dp else 4.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(msg.body, style = MaterialTheme.typography.bodyMedium, color = textColor)
            Spacer(Modifier.height(2.dp))
            Text(
                msg.timestamp.format(DateTimeFormatter.ofPattern("h:mm a")),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun AiDiaryCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Text("AI Diary Entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary)
            }
            if (title.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
