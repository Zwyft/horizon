package com.zwyft.horizon.importing

import android.content.Context
import androidx.work.*
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.dao.ContactDao
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Repository that orchestrates importing messages from XML/JSON files into Room.
 *
 * Responsibilities:
 * - Detect file type (XML vs JSON)
 * - Stream-parse the file (via SmsBackupXmlParser / JsonImportParser)
 * - Resolve contact names + mark monitored flag
 * - Bulk-insert into Room in batches (avoids transaction overhead)
 * - Track progress (for UI / WorkManager)
 * - Support resume (skip messages already imported in this batch)
 */
class ImportRepository(
    private val context: Context,
    private val db: HorizonDatabase,
    private val contactDao: ContactDao
) {
    companion object {
        const val BATCH_SIZE = 200   // insert every 200 messages
    }

    sealed class ImportProgress {
        object Started : ImportProgress()
        data class Progress(val processed: Int, val batch: String) : ImportProgress()
        data class Done(val totalImported: Int, val batchTag: String) : ImportProgress()
        data class Error(val message: String) : ImportProgress()
    }

    /**
     * Import a file. Returns a Flow of progress updates.
     *
     * @param file The XML or JSON export file (may be 40 GB)
     * @param batchTag Optional tag to mark messages (for resume support)
     */
    fun importFile(
        file: File,
        batchTag: String = UUID.randomUUID().toString()
    ): Flow<ImportProgress> = kotlinx.coroutines.flow.flow {
        emit(ImportProgress.Started)

        try {
            val parser = when (file.extension.lowercase()) {
                "xml"  -> SmsBackupXmlParser(context)
                "json" -> JsonImportParser()
                else    -> throw IllegalArgumentException("Unsupported file type: ${file.extension}")
            }

            // Fetch monitored contacts for fast lookup
            val monitoredContacts = contactDao.getMonitored().first()
            val monitoredNumbers = monitoredContacts.map { it.normalizedPhoneNumber }.toSet()

            var processed = 0
            val batchBuffer = mutableListOf<MessageEntity>()

            val flow = if (parser is SmsBackupXmlParser) {
                parser.parse(file, batchTag)
            } else {
                (parser as JsonImportParser).parse(file, batchTag)
            }

            flow.collect { msg ->
                // Mark monitored flag
                val normalized = msg.address.normalizePhone()
                msg.monitored = monitoredNumbers.contains(normalized)

                batchBuffer.add(msg)
                processed++

                if (batchBuffer.size >= BATCH_SIZE) {
                    insertBatch(batchBuffer, batchTag)
                    emit(ImportProgress.Progress(processed, batchTag))
                    batchBuffer.clear()
                }
            }

            // Final batch
            if (batchBuffer.isNotEmpty()) {
                insertBatch(batchBuffer, batchTag)
            }

            emit(ImportProgress.Done(processed, batchTag))
        } catch (e: Exception) {
            emit(ImportProgress.Error(e.message ?: "Unknown error"))
        }
    }

    private suspend fun insertBatch(buffer: List<MessageEntity>, batchTag: String) {
        withContext(Dispatchers.IO) {
            db.messageDao().insertAll(buffer)
        }
    }

    /**
     * Find the latest message date for a given batch tag (resume support).
     */
    suspend fun getLatestDateForBatch(batchTag: String): Date? =
        db.messageDao().getLatestDateForBatch(batchTag)

    /**
     * Normalize a phone number: strip all non-digits.
     */
    private fun String.normalizePhone(): String =
        this.replace(Regex("[^0-9]"), "")
}
