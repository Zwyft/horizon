package com.coparenting.chronicle.horizon.ui.screens

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
    val yearLabel  = date.format(DateTimeFormatter.ofPattern("yyyy"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            yearLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onAskAI) {
                        Icon(Icons.Default.AutoAwesome, "Ask AI",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    FilledTonalIconButton(
                        onClick = { onAddEntry(date) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, "Add entry")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            state.generationError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Text(error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (!hasSmsPermission && state.coParentPhone.isNotBlank()) {
                SmsPermissionBanner()
            }

            if (state.isGeneratingSummary) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Generating AI diary entry…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (!state.isGeneratingSummary && state.generatedDiary == null &&
                (state.smsMessages.isNotEmpty() || state.journalEntries.isNotEmpty())) {
                AiSummaryButton(onClick = { viewModel.generateAiSummary() })
            }

            if (state.timeline.isEmpty() && !state.isGeneratingSummary) {
                EmptyDayMessage(date = date, onAddEntry = { onAddEntry(date) })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.timeline, key = {
                        when (it) {
                            is TimelineItem.JournalItem -> "journal_${it.entry.id}"
                            is TimelineItem.SmsItem     -> "sms_${it.msg.id}"
                            is TimelineItem.DiaryItem   -> "diary_${it.entry.id}"
                        }
                    }) { item ->
                        when (item) {
                            is TimelineItem.JournalItem -> JournalEntryCard(
                                entry = item.entry,
                                onEdit = { onEditEntry(item.entry.id) },
                                onDelete = { viewModel.deleteEntry(item.entry) }
                            )
                            is TimelineItem.SmsItem  -> SmsBubble(msg = item.msg)
                            is TimelineItem.DiaryItem -> AiDiaryCard(
                                content = item.entry.content,
                                title = item.entry.title
                            )
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
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Generate AI Summary", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SmsPermissionBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sms, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "Grant SMS permission in Settings to see text messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun EmptyDayMessage(date: LocalDateTime, onAddEntry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.EventNote, null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            "Nothing recorded",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Tap below to add a journal entry for this day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Button(
            onClick = onAddEntry,
            shape = RoundedCornerShape(14.dp)
        ) {
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
    val isImportant = entry.isImportant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isImportant) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isImportant) {
                        Icon(
                            Icons.Default.Star, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        entry.timestamp.format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isImportant) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "Edit",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }

            if (entry.title.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(entry.content, style = MaterialTheme.typography.bodyMedium)

            val tags = entry.tags.split(",").filter { it.isNotBlank() }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TagChipRow(tags)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure? This cannot be undone.") },
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 0.dp)
    ) {
        tags.forEach { tag -> TagChip(tag) }
    }
}

@Composable
fun TagChip(tag: String) {
    val (bg, fg) = tagColors(tag)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(tag, style = MaterialTheme.typography.labelSmall, color = bg)
    }
}

private fun tagColors(tag: String): Pair<Color, Color> = when (tag.lowercase()) {
    "handoff"       -> TagHandoff   to Color.White
    "pickup"        -> TagPickup    to Color.White
    "dropoff"       -> TagDropoff   to Color.White
    "incident"      -> TagIncident  to Color.White
    "medical"       -> TagMedical   to Color.White
    "school"        -> TagSchool    to Color.White
    "expense"       -> TagExpense   to Color.White
    "communication" -> TagComm      to Color.White
    "violation"     -> TagViolation to Color.White
    else            -> TagNote      to Color.White
}

@Composable
private fun SmsBubble(msg: SmsDataSource.SmsMessage) {
    val isIncoming = msg.type == MessageType.INCOMING
    val bgColor    = if (isIncoming) MaterialTheme.colorScheme.surfaceVariant
                     else MaterialTheme.colorScheme.primaryContainer
    val textColor  = if (isIncoming) MaterialTheme.colorScheme.onSurfaceVariant
                     else MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isIncoming) Arrangement.Start else Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isIncoming) 4.dp else 18.dp,
                        bottomEnd   = if (isIncoming) 18.dp else 4.dp
                    )
                )
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(msg.body, style = MaterialTheme.typography.bodyMedium, color = textColor)
            Spacer(Modifier.height(3.dp))
            Text(
                msg.timestamp.format(DateTimeFormatter.ofPattern("h:mm a")),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AiDiaryCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "AI Summary",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (title.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
