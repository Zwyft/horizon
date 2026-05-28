package com.zwyft.horizon.importing

import android.util.JsonReader
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileReader
import java.util.*

/**
 * Streaming JSON parser for SMS exports (e.g. JSON from SMS Backup & Restore,
 * or custom JSON formats).
 *
 * Uses JsonReader (streaming, not in-memory) so large files don't OOM.
 *
 * Expected JSON array format:
 * [
 *   {
 *     "address": "+1234567890",
 *     "date": 1234567890000,
 *     "type": 1,
 *     "body": "hello",
 *     "read": 1,
 *     "seen": 1
 *   },
 *   ...
 * ]
 */
class JsonImportParser {

    /**
     * Parse a JSON file and emit MessageEntity objects one at a time.
     */
    fun parse(jsonFile: File, batchTag: String? = null): Flow<MessageEntity> = flow {
        val reader = JsonReader(FileReader(jsonFile))
        try {
            reader.beginArray()
            while (reader.hasNext()) {
                val msg = parseMessage(reader, batchTag)
                if (msg != null) emit(msg)
            }
            reader.endArray()
        } finally {
            reader.close()
        }
    }

    private fun parseMessage(reader: JsonReader, batchTag: String?): MessageEntity? {
        var address: String? = null
        var dateMillis: Long? = null
        var type = 1
        var body: String? = null
        var read = 0
        var seen = 0
        var dateSent: Long? = null
        var messageId: Long? = null
        var contactName: String? = null
        var protocol = 0

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "address", "phone", "from" -> address = reader.nextString()
                "date", "timestamp", "date_sent" -> {
                    val v = reader.nextLong()
                    if (dateMillis == null) dateMillis = v else dateSent = v
                }
                "type", "msg_type", "box" -> {
                    val v = reader.nextString().toIntOrNull() ?: reader.nextInt()
                    type = v
                }
                "body", "text", "message" -> body = reader.nextString()
                "read" -> read = if (reader.nextBoolean()) 1 else 0
                "seen" -> seen = if (reader.nextBoolean()) 1 else 0
                "date_sent", "dateSent" -> dateSent = reader.nextLong()
                "_id", "msg_id", "message_id" -> messageId = reader.nextLong()
                "contact_name", "name" -> contactName = reader.nextString()
                "protocol" -> protocol = reader.nextString().toIntOrNull() ?: 0
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (address == null || dateMillis == null) return null

        return MessageEntity(
            messageId       = messageId ?: dateMillis,
            threadId        = 0L,
            address         = address,
            contactName     = contactName,
            body            = body,
            date            = Date(dateMillis),
            dateSent        = dateSent?.let { Date(it) },
            type            = type,
            read            = read,
            seen            = seen,
            protocol        = protocol,
            subject         = null,
            mmsContentType  = null,
            mmsData         = null,
            attachedFilePath= null,
            rcs             = false,
            monitored       = false,
            importedFrom    = batchTag,
            journalProcessed = false
        )
    }
}
