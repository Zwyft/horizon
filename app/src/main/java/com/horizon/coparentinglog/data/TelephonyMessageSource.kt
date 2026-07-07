package com.horizon.coparentinglog.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.Telephony
import com.horizon.coparentinglog.core.AttachmentRecord
import com.horizon.coparentinglog.core.ContactIdentity
import com.horizon.coparentinglog.core.MessageDirection
import com.horizon.coparentinglog.core.MessageNormalization
import com.horizon.coparentinglog.core.MessageRecord
import com.horizon.coparentinglog.core.MessageTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelephonyMessageSource(private val context: Context) {
    suspend fun fetchSmsAndMms(sinceEpochMs: Long, regionIso: String): List<MessageRecord> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val messages = buildList {
            addAll(loadSms(resolver, sinceEpochMs, regionIso))
            addAll(loadMms(resolver, sinceEpochMs, regionIso))
        }
        messages.sortedBy { it.sentAtEpochMs }
    }

    suspend fun loadContacts(): List<ContactIdentity> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )
        cursor?.use {
            buildList {
                val lookupIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val rawNumber = it.getString(numberIndex) ?: continue
                    val normalized = MessageNormalization.normalize(rawNumber) ?: continue
                    add(
                        ContactIdentity(
                            lookupKey = it.getString(lookupIndex) ?: normalized,
                            displayName = it.getString(nameIndex) ?: rawNumber,
                            normalizedNumber = normalized,
                            rawNumber = rawNumber,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    private fun loadSms(resolver: ContentResolver, sinceEpochMs: Long, regionIso: String): List<MessageRecord> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )
        val selection = "${Telephony.Sms.DATE} > ?"
        val cursor = resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            arrayOf(sinceEpochMs.toString()),
            "${Telephony.Sms.DATE} ASC",
        )
        return cursor?.use { cursorToSmsRecords(it, regionIso) } ?: emptyList()
    }

    private fun cursorToSmsRecords(cursor: Cursor, regionIso: String): List<MessageRecord> {
        val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
        val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
        val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
        return buildList {
            while (cursor.moveToNext()) {
                val rawAddress = cursor.getString(addressIndex)
                val normalized = MessageNormalization.normalize(rawAddress, regionIso)
                add(
                    MessageRecord(
                        sourceId = "sms:${cursor.getLong(idIndex)}",
                        threadKey = MessageNormalization.canonicalThreadKey(rawAddress, cursor.getLong(threadIndex), regionIso),
                        normalizedAddress = normalized,
                        rawAddress = rawAddress,
                        direction = when (cursor.getInt(typeIndex)) {
                            Telephony.Sms.MESSAGE_TYPE_SENT -> MessageDirection.OUTBOUND
                            Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageDirection.INBOUND
                            else -> MessageDirection.SYSTEM
                        },
                        transport = MessageTransport.SMS,
                        sentAtEpochMs = cursor.getLong(dateIndex),
                        body = cursor.getString(bodyIndex),
                    ),
                )
            }
        }
    }

    private fun loadMms(resolver: ContentResolver, sinceEpochMs: Long, regionIso: String): List<MessageRecord> {
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.SUBJECT,
        )
        val selection = "${Telephony.Mms.DATE} > ?"
        val cursor = resolver.query(
            Telephony.Mms.CONTENT_URI,
            projection,
            selection,
            arrayOf((sinceEpochMs / 1000L).toString()),
            "${Telephony.Mms.DATE} ASC",
        )
        return cursor?.use { cursorToMmsRecords(resolver, it, regionIso) } ?: emptyList()
    }

    private fun cursorToMmsRecords(resolver: ContentResolver, cursor: Cursor, regionIso: String): List<MessageRecord> {
        val idIndex = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
        val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
        val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
        val boxIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
        val subjectIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)

        return buildList {
            while (cursor.moveToNext()) {
                val mmsId = cursor.getLong(idIndex)
                val parts = loadMmsParts(resolver, mmsId)
                val body = parts.firstNotNullOfOrNull { it.body }
                add(
                    MessageRecord(
                        sourceId = "mms:$mmsId",
                        threadKey = "thread:${cursor.getLong(threadIndex)}",
                        direction = when (cursor.getInt(boxIndex)) {
                            Telephony.Mms.MESSAGE_BOX_SENT -> MessageDirection.OUTBOUND
                            Telephony.Mms.MESSAGE_BOX_INBOX -> MessageDirection.INBOUND
                            else -> MessageDirection.SYSTEM
                        },
                        transport = MessageTransport.MMS,
                        sentAtEpochMs = cursor.getLong(dateIndex) * 1000L,
                        body = body,
                        subject = cursor.getString(subjectIndex),
                        attachments = parts.flatMap { it.attachments },
                        rawAddress = null,
                        normalizedAddress = null,
                    ),
                )
            }
        }
    }

    private data class PartResult(
        val body: String? = null,
        val attachments: List<AttachmentRecord> = emptyList(),
    )

    private fun loadMmsParts(resolver: ContentResolver, mmsId: Long): List<PartResult> {
        val uri = android.net.Uri.parse("content://mms/part")
        val projection = arrayOf("_id", "ct", "_data", "text", "cl", "name", "sz")
        val selection = "mid=?"
        val cursor = resolver.query(uri, projection, selection, arrayOf(mmsId.toString()), null)
        return cursor?.use {
            val idIndex = it.getColumnIndexOrThrow("_id")
            val contentTypeIndex = it.getColumnIndexOrThrow("ct")
            val dataIndex = it.getColumnIndexOrThrow("_data")
            val textIndex = it.getColumnIndexOrThrow("text")
            val nameIndex = it.getColumnIndexOrThrow("name")
            val sizeIndex = it.getColumnIndexOrThrow("sz")
            buildList {
                while (it.moveToNext()) {
                    val contentType = it.getString(contentTypeIndex) ?: "application/octet-stream"
                    val text = it.getString(textIndex)
                    val data = it.getString(dataIndex)
                    val attachment = when {
                        contentType.startsWith("text/") -> null
                        else -> AttachmentRecord(
                            mimeType = contentType,
                            displayName = it.getString(nameIndex),
                            localUri = data,
                            byteCount = runCatching { it.getLong(sizeIndex) }.getOrNull(),
                        )
                    }
                    add(
                        PartResult(
                            body = text?.takeIf { contentType == "text/plain" || contentType == "text/html" } ?: text,
                            attachments = listOfNotNull(attachment),
                        ),
                    )
                }
            }
        } ?: emptyList()
    }
}

