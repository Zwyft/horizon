package com.zwyft.horizon.export

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageEntity
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Portable, compressed message archive (gzip JSON).
 *
 * Exports ALL monitored messages as a gzip-compressed JSON file
 * that can be transferred, stored, and restored with full fidelity.
 *
 * Unlike the Room DB backup, this is portable across app versions
 * and can be inspected manually.
 */
class MessageArchiveExporter(private val context: Context) {

    private val db: HorizonDatabase by lazy { HorizonDatabase.getInstance(context) }
    private val gson = Gson()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

    /**
     * Export all monitored messages to a gzip-compressed JSON file.
     * @return The output file
     */
    suspend fun exportAll(outputFile: File): File {
        val messages = db.messageDao().getMonitoredPaged(limit = Int.MAX_VALUE, offset = 0)

        val archive = MessageArchive(
            version = 1,
            exportedAt = Date().time,
            appVersion = "1.0.0",
            messageCount = messages.size,
            messages = messages.map { it.toArchiveMessage() }
        )

        GZIPOutputStream(FileOutputStream(outputFile)).use { gzip ->
            BufferedWriter(OutputStreamWriter(gzip)).use { writer ->
                gson.toJson(archive, writer)
            }
        }

        return outputFile
    }

    /**
     * Import messages from a gzip-compressed JSON archive.
     * @return Number of messages imported
     */
    suspend fun importAll(inputFile: File): Int {
        val json = GZIPInputStream(FileInputStream(inputFile)).use { gzip ->
            BufferedReader(InputStreamReader(gzip)).use { it.readText() }
        }
        val archive: MessageArchive = gson.fromJson(json, MessageArchive::class.java)
        val batchTag = "archive_${dateFmt.format(Date())}"

        val entities = archive.messages.map { it.toMessageEntity(batchTag) }
        db.messageDao().insertAll(entities)

        return entities.size
    }
}

/**
 * Portable archive format (JSON-serializable).
 */
data class MessageArchive(
    val version: Int,
    val exportedAt: Long,
    val appVersion: String,
    val messageCount: Int,
    val messages: List<ArchiveMessage>
)

data class ArchiveMessage(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val body: String?,
    val date: Long,          // epoch millis
    val dateSent: Long?,     // epoch millis
    val type: Int,
    val read: Int,
    val seen: Int,
    val protocol: Int,
    val subject: String?,
    val mmsContentType: String?,
    val mmsData: String?,
    val attachedFilePath: String?,
    val rcs: Boolean,
    val monitored: Boolean,
    val journalProcessed: Boolean
)

fun MessageEntity.toArchiveMessage() = ArchiveMessage(
    messageId = messageId,
    threadId = threadId,
    address = address,
    contactName = contactName,
    body = body,
    date = date.time,
    dateSent = dateSent?.time,
    type = type,
    read = read,
    seen = seen,
    protocol = protocol,
    subject = subject,
    mmsContentType = mmsContentType,
    mmsData = mmsData,
    attachedFilePath = attachedFilePath,
    rcs = rcs,
    monitored = monitored,
    journalProcessed = journalProcessed
)

fun ArchiveMessage.toMessageEntity(batchTag: String) = MessageEntity(
    messageId = messageId,
    threadId = threadId,
    address = address,
    contactName = contactName,
    body = body,
    date = Date(date),
    dateSent = dateSent?.let { Date(it) },
    type = type,
    read = read,
    seen = seen,
    protocol = protocol,
    subject = subject,
    mmsContentType = mmsContentType,
    mmsData = mmsData,
    attachedFilePath = attachedFilePath,
    rcs = rcs,
    monitored = monitored,
    journalProcessed = false,   // reset — re-process after restore
    importedFrom = batchTag
)
