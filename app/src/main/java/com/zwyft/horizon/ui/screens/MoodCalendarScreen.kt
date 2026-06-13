package com.zwyft.horizon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.JournalEntryEntity
import com.zwyft.horizon.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodCalendarScreen(
    navController: NavController,
    viewModel: MoodCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood Calendar") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(uiState.currentMonth),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day-of-week headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Calendar grid
            val days = uiState.calendarDays
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(days) { day ->
                    DayCell(
                        day = day,
                        onClick = {
                            if (day.entryId != null) {
                                navController.navigate(Routes.journalDetail(day.entryId))
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem("😊", "Positive", MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem("😐", "Neutral", MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem("😟", "Tense", MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(" ", "No data", MaterialTheme.colorScheme.surfaceVariant)
            }

            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, onClick: () -> Unit) {
    val bgColor = when {
        day.isPlaceholder -> MaterialTheme.colorScheme.surface
        day.entryId == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> dayColor(day.sentiment ?: 0f)
    }

    val textColor = when {
        day.isPlaceholder -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        day.isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(bgColor, MaterialTheme.shapes.small)
            .clickable(enabled = day.entryId != null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (day.isPlaceholder) "" else "${day.dayOfMonth}",
                style = if (day.isToday) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelSmall,
                color = textColor
            )
            if (day.sentiment != null) {
                val emoji = when {
                    day.sentiment > 0.5f -> "😊"
                    day.sentiment > 0.2f -> "🙂"
                    day.sentiment > -0.2f -> "😐"
                    day.sentiment > -0.5f -> "😟"
                    else -> "😤"
                }
                Text(emoji, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LegendItem(emoji: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, MaterialTheme.shapes.extraSmall)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("$emoji $label", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun dayColor(sentiment: Float): androidx.compose.ui.graphics.Color {
    val c = MaterialTheme.colorScheme
    return when {
        sentiment > 0.5f -> c.primary.copy(alpha = 0.6f)
        sentiment > 0.2f -> c.primary.copy(alpha = 0.35f)
        sentiment > -0.2f -> c.outline.copy(alpha = 0.2f)
        sentiment > -0.5f -> c.error.copy(alpha = 0.35f)
        else -> c.error.copy(alpha = 0.6f)
    }
}

// ── Data model ────────────────────────────────────────────────

data class CalendarDay(
    val dayOfMonth: Int,
    val isPlaceholder: Boolean = false,
    val isToday: Boolean = false,
    val sentiment: Float? = null,
    val entryId: Long? = null
)

data class MoodCalendarUiState(
    val currentMonth: Date = Date(),
    val calendarDays: List<CalendarDay> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class MoodCalendarViewModel @Inject constructor(
    private val db: HorizonDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoodCalendarUiState())
    val uiState: StateFlow<MoodCalendarUiState> = _uiState.asStateFlow()

    init { loadMonth() }

    fun previousMonth() {
        val cal = Calendar.getInstance().apply { time = _uiState.value.currentMonth; add(Calendar.MONTH, -1) }
        _uiState.update { it.copy(currentMonth = cal.time) }
        loadMonth()
    }

    fun nextMonth() {
        val cal = Calendar.getInstance().apply { time = _uiState.value.currentMonth; add(Calendar.MONTH, 1) }
        _uiState.update { it.copy(currentMonth = cal.time) }
        loadMonth()
    }

    private fun loadMonth() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val monthCal = Calendar.getInstance().apply { time = _uiState.value.currentMonth }

            // First and last day of month
            monthCal.set(Calendar.DAY_OF_MONTH, 1)
            monthCal.set(Calendar.HOUR_OF_DAY, 0)
            monthCal.set(Calendar.MINUTE, 0)
            monthCal.set(Calendar.SECOND, 0)
            monthCal.set(Calendar.MILLISECOND, 0)
            val monthStart = monthCal.time

            val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            monthCal.add(Calendar.MONTH, 1)
            val monthEnd = monthCal.time

            // Fetch all journal entries for this month
            val entries = db.journalEntryDao().getInRange(monthStart, monthEnd)
            val entryByDay = mutableMapOf<Int, JournalEntryEntity>()
            entries.forEach { entry ->
                cal.time = entry.dateStart
                val day = cal.get(Calendar.DAY_OF_MONTH)
                entryByDay[day] = entry
            }

            // Day of week for the 1st (0=Sun, 6=Sat)
            monthCal.time = monthStart
            val firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK) - 1 // 0-based

            // Today
            cal.time = Date()
            val today = cal.get(Calendar.DAY_OF_MONTH)
            val todayMonth = cal.get(Calendar.MONTH)
            val currentMonthIndex = Calendar.getInstance().apply { time = _uiState.value.currentMonth }.get(Calendar.MONTH)

            val days = mutableListOf<CalendarDay>()

            // Placeholders before 1st
            repeat(firstDayOfWeek) { days.add(CalendarDay(dayOfMonth = 0, isPlaceholder = true)) }

            // Actual days
            for (d in 1..daysInMonth) {
                val entry = entryByDay[d]
                days.add(
                    CalendarDay(
                        dayOfMonth = d,
                        isToday = d == today && currentMonthIndex == todayMonth,
                        sentiment = entry?.sentimentOverall,
                        entryId = entry?.id
                    )
                )
            }

            _uiState.update { it.copy(calendarDays = days, loading = false) }
        }
    }
}
