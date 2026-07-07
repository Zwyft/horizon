package com.horizon.coparentinglog.sync

import android.content.Context
import com.horizon.coparentinglog.core.ArchiveSnapshot
import com.horizon.coparentinglog.data.ArchiveRepository
import com.horizon.coparentinglog.data.SettingsRepository
import com.horizon.coparentinglog.data.TelephonyMessageSource

class SyncCoordinator(private val context: Context) {
    suspend fun syncArchive(): ArchiveSnapshot {
        val settingsRepository = SettingsRepository(context)
        val archiveRepository = ArchiveRepository(context)
        val messageSource = TelephonyMessageSource(context)
        val settings = settingsRepository.read()
        val snapshot = archiveRepository.read()
        if (!settings.defaultSmsGranted || !settings.messagesPermissionGranted) {
            return snapshot
        }
        val newMessages = messageSource.fetchSmsAndMms(snapshot.syncCursor.lastSyncedAtEpochMs, settings.defaultRegionIso)
        val merged = merge(snapshot, newMessages)
        archiveRepository.write(merged)
        return merged
    }

    private fun merge(snapshot: ArchiveSnapshot, newMessages: List<com.horizon.coparentinglog.core.MessageRecord>): ArchiveSnapshot {
        val existing = snapshot.messages.associateBy { it.sourceId }.toMutableMap()
        newMessages.forEach { existing[it.sourceId] = it }
        val cursor = newMessages.maxOfOrNull { it.sentAtEpochMs } ?: snapshot.syncCursor.lastSyncedAtEpochMs
        return snapshot.copy(
            syncCursor = snapshot.syncCursor.copy(lastSyncedAtEpochMs = maxOf(snapshot.syncCursor.lastSyncedAtEpochMs, cursor)),
            messages = existing.values.sortedBy { it.sentAtEpochMs },
        )
    }
}

