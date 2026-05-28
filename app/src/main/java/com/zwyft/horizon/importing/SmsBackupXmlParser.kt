package com.zwyft.horizon.importing

import android.content.Context
import android.util.Xml
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Streaming parser for SMS Backup & Restore XML exports.
 *
 * Uses XmlPullParser (streaming, not DOM) so it can handle 40 GB files
 * without OOM. Emits MessageEntity objects one at a time via Flow.
 *
 * XML format (SMS Backup & Restore):
 * <smses count="N" backup_date="...">
 *   <sms protocol="0" address="+123..." date="1234567890" type="1"
 *        body="hello" read="1" ... />
 *   <mms ...>
 *     <parts>
 *       <part seq="0" ct="text/plain" data="hello" />
 *     </parts>
 *   </mms>
 * </smses>
 */
class SmsBackupXmlParser(private val context: Context) {

    /**
     * Parse an XML file and emit MessageEntity objects.
     *
     * @param xmlFile The SMS Backup & Restore XML file (can be 40 GB)
     * @param batchTag Optional tag to mark imported messages (for resume support)
     * @param onlyMonitoredOnly If true, only emit messages whose address
     *        matches a monitored contact (checked later, not here)
     * @return Flow of MessageEntity — one per SMS/MMS element
     */
    fun parse(
        xmlFile: File,
        batchTag: String? = null,
        onlyMonitoredOnly: Boolean = false   // we filter later in the repo
    ): Flow<MessageEntity> = flow {
        val parser = Xml.newPullParser()
        val inputStream = FileInputStream(xmlFile)
        try {
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "sms" -> {
                            val msg = parseSms(parser, batchTag)
                            if (msg != null) emit(msg)
                        }
                        "mms" -> {
                            val msg = parseMms(parser, batchTag)
                            if (msg != null) emit(msg)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            throw IOException("XML parse error at line ${parser.lineNumber}", e)
        } finally {
            inputStream.close()
        }
    }

    // ── SMS element ──────────────────────────────────────────────
    private fun parseSms(parser: XmlPullParser, batchTag: String?): MessageEntity? {
        return try {
            val address   = parser.getAttributeValue(null, "address") ?: return null
            val dateMillis = parser.getAttributeValue(null, "date")?.toLongOrNull() ?: return null
            val type      = parser.getAttributeValue(null, "type")?.toIntOrNull() ?: 1
            val body      = parser.getAttributeValue(null, "body") ?: ""
            val read      = parser.getAttributeValue(null, "read")?.toIntOrNull() ?: 0
            val seen      = parser.getAttributeValue(null, "seen")?.toIntOrNull() ?: read
            val dateSent  = parser.getAttributeValue(null, "date_sent")?.toLongOrNull()
            val protocol  = parser.getAttributeValue(null, "protocol")?.toIntOrNull() ?: 0
            val messageId = parser.getAttributeValue(null, "_id")?.toLongOrNull() ?: dateMillis

            MessageEntity(
                messageId       = messageId,
                threadId        = 0L, // will be resolved later
                address         = address,
                contactName     = parser.getAttributeValue(null, "contact_name"),
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
                rcs             = false,   // SMS Backup & Restore doesn't tag RCS
                monitored       = false,  // set later by repository
                importedFrom    = batchTag,
                journalProcessed = false
            )
        } catch (e: Exception) {
            null  // skip malformed entries
        }
    }

    // ── MMS element (simplified — text only) ────────────────────
    private fun parseMms(parser: XmlPullParser, batchTag: String?): MessageEntity? {
        return try {
            val messageId = parser.getAttributeValue(null, "_id")?.toLongOrNull() ?: 0L
            val dateMillis = parser.getAttributeValue(null, "date")?.toLongOrNull() ?: return null
            val type      = parser.getAttributeValue(null, "m_type")?.toIntOrNull() ?: 1
            val address   = parser.getAttributeValue(null, "address") ?: ""
            val read      = parser.getAttributeValue(null, "read")?.toIntOrNull() ?: 0

            // Try to extract text body from <parts><part> children
            var bodyText: String? = null
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.name == "mms")) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "part") {
                    val ct = parser.getAttributeValue(null, "ct") ?: ""
                    if (ct == "text/plain" || ct == "application/smil") {
                        bodyText = parser.getAttributeValue(null, "text")
                            ?: parser.getAttributeValue(null, "data")
                    }
                }
                eventType = parser.next()
            }

            MessageEntity(
                messageId       = messageId,
                threadId        = 0L,
                address         = address,
                contactName     = null,
                body            = bodyText,
                date            = Date(dateMillis),
                dateSent        = null,
                type            = if (type == 128) 1 else 2, // 128=inbox in MMS
                read            = read,
                seen            = read,
                protocol        = 1, // MMS
                subject         = parser.getAttributeValue(null, "sub"),
                mmsContentType  = "mms",
                mmsData         = null,
                attachedFilePath= null,
                rcs             = false,
                monitored       = false,
                importedFrom    = batchTag,
                journalProcessed = false
            )
        } catch (e: Exception) {
            null
        }
    }
}
