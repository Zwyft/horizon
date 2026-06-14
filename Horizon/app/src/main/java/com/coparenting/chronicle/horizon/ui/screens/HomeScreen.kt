package com.coparenting.chronicle.horizon.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import com.coparenting.chronicle.horizon.presentation.viewmodel.CalendarViewModel
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDaySelected: (LocalDateTime) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedVisibility(visible = !searchActive, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            "Horizon",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) viewModel.clearSearch()
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchActive) "Close" else "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = searchActive) {
                SearchBarRow(
                    query = state.searchQuery,
                    onQueryChange = viewModel::updateSearch,
                    onClear = { viewModel.clearSearch() }
                )
            }

            if (searchActive && state.searchQuery.isNotBlank()) {
                SearchResultsList(
                    results = state.searchResults,
                    onResultClick = { entry -> onDaySelected(entry.date) }
                )
            } else {
                CalendarView(
                    currentMonth = state.currentMonth,
                    selectedDate = state.selectedDate,
                    datesWithEntries = state.datesWithEntries,
                    onPrevMonth = viewModel::prevMonth,
                    onNextMonth = viewModel::nextMonth,
                    onDayClick = { date ->
                        viewModel.selectDate(date)
                        onDaySelected(date)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchBarRow(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search journal entries…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Clear, "Clear") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun SearchResultsList(results: List<ManualJournalEntry>, onResultClick: (ManualJournalEntry) -> Unit) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant)
                Text("No matching entries", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(results) { entry ->
            SearchResultCard(entry = entry, onClick = { onResultClick(entry) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SearchResultCard(entry: ManualJournalEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (entry.title.isNotBlank()) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                entry.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarView(
    currentMonth: YearMonth,
    selectedDate: LocalDateTime,
    datesWithEntries: Set<LocalDateTime>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDateTime) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {

        // Month navigation header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Default.ChevronLeft, "Previous month",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM")),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ChevronRight, "Next month",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (i == 0 || i == 6) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Calendar grid
        val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value % 7
        val daysInMonth = currentMonth.lengthOfMonth()
        val today = LocalDateTime.now().toLocalDate()
        val entryDates = datesWithEntries.map { it.toLocalDate() }.toSet()
        val rows = (firstDayOffset + daysInMonth + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - firstDayOffset + 1
                    if (dayNum < 1 || dayNum > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).height(56.dp))
                    } else {
                        val dayDate = currentMonth.atDay(dayNum).atStartOfDay()
                        DayCell(
                            day = dayNum,
                            isToday = dayDate.toLocalDate() == today,
                            isSelected = dayDate.toLocalDate() == selectedDate.toLocalDate(),
                            hasEntries = dayDate.toLocalDate() in entryDates,
                            isWeekend = col == 0 || col == 6,
                            onClick = { onDayClick(dayDate) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Entry count summary strip
        val entryCount = datesWithEntries.count { it.month == currentMonth.month && it.year == currentMonth.year }
        if (entryCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.BookmarkBorder, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "$entryCount ${if (entryCount == 1) "entry" else "entries"} this month",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntries: Boolean,
    isWeekend: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor: Color = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday    -> MaterialTheme.colorScheme.primaryContainer
        else       -> Color.Transparent
    }
    val textColor: Color = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday    -> MaterialTheme.colorScheme.primary
        isWeekend  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else       -> MaterialTheme.colorScheme.onSurface
    }
    val dotColor: Color = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        else       -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier
            .height(56.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        if (hasEntries) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
