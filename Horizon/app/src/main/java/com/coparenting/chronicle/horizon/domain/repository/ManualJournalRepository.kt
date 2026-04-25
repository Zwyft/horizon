package com.coparenting.chronicle.horizon.domain.repository

import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface ManualJournalRepository {
    fun getAll(): Flow<List<ManualJournalEntry>>
    fun getForDate(date: LocalDateTime): Flow<List<ManualJournalEntry>>
    fun getDatesWithEntries(): Flow<List<LocalDateTime>>
    fun search(query: String): Flow<List<ManualJournalEntry>>
    suspend fun getById(id: String): ManualJournalEntry?
    suspend fun getEntriesSince(since: LocalDateTime): List<ManualJournalEntry>
    suspend fun save(entry: ManualJournalEntry)
    suspend fun delete(entry: ManualJournalEntry)
}
