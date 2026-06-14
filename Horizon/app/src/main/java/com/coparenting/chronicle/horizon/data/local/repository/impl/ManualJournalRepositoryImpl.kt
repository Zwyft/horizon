package com.coparenting.chronicle.horizon.data.local.repository.impl

import com.coparenting.chronicle.horizon.data.local.database.dao.ManualJournalDao
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import com.coparenting.chronicle.horizon.domain.repository.ManualJournalRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualJournalRepositoryImpl @Inject constructor(
    private val dao: ManualJournalDao
) : ManualJournalRepository {
    override fun getAll() = dao.getAll()
    override fun getForDate(date: LocalDateTime) = dao.getForDate(date)
    override fun getDatesWithEntries() = dao.getDatesWithEntries()
    override fun search(query: String) = dao.search(query)
    override suspend fun getById(id: String) = dao.getById(id)
    override suspend fun getEntriesSince(since: LocalDateTime) = dao.getEntriesSince(since)
    override suspend fun save(entry: ManualJournalEntry) {
        if (dao.getById(entry.id) == null) dao.insert(entry) else dao.update(entry)
    }
    override suspend fun delete(entry: ManualJournalEntry) = dao.delete(entry)
}
