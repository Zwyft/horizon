package com.coparenting.chronicle.horizon.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "manual_journal_entries")
data class ManualJournalEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: LocalDateTime,        // midnight of the day this entry belongs to
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val lastModified: LocalDateTime = LocalDateTime.now(),
    val title: String = "",
    val content: String,
    val tags: String = "",          // comma-separated: Handoff,Pickup,Dropoff,Incident,Medical,School,Expense,Communication,Violation,Note
    val isImportant: Boolean = false
)
