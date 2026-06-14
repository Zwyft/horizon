package com.coparenting.chronicle.horizon.data.remote.sms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.coparenting.chronicle.horizon.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

class SmsDataSource @Inject constructor(private val context: Context) {

    data class SmsMessage(
        val id: Long,
        val address: String,
        val body: String,
        val timestamp: LocalDateTime,
        val type: MessageType,
        val contactName: String?,
        val threadId: String,
        val messageType: MessageType?,
        val isRead: Boolean,
        val folder: String,
        val attachmentCount: Int = 0
    )

    data class SmsContact(
        val name: String?,
        val phoneNumber: String
    )

    // PDU address types used in MMS addr table
    private val MMS_PDU_FROM = 137
    private val MMS_PDU_TO = 151

    suspend fun getSmsMessages(): List<SmsMessage> = getSmsMessagesSince(0L)

    suspend fun getSmsMessagesSince(sinceMillis: Long = 0L): List<SmsMessage> = withContext(Dispatchers.IO) {
        val smsList = mutableListOf<SmsMessage>()
        val selection = if (sinceMillis > 0) "${Telephony.Sms.DATE} >= ?" else null
        val selectionArgs = if (sinceMillis > 0) arrayOf(sinceMillis.toString()) else null

        context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.THREAD_ID,
                Telephony.Sms.READ
            ),
            selection, selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: continue
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)) ?: ""
                val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1

                val smsType = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageType.INCOMING
                    Telephony.Sms.MESSAGE_TYPE_SENT -> MessageType.OUTGOING
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageType.DRAFT
                    else -> MessageType.UNKNOWN
                }

                smsList.add(
                    SmsMessage(
                        id = id,
                        address = address,
                        body = body,
                        timestamp = LocalDateTime.ofEpochSecond(date / 1000, 0, ZoneOffset.UTC),
                        type = smsType,
                        contactName = getContactName(address),
                        threadId = threadId,
                        messageType = MessageType.TEXT,
                        isRead = isRead,
                        folder = if (smsType == MessageType.INCOMING) "inbox" else "sent"
                    )
                )
            }
        }
        smsList
    }

    suspend fun getMmsMessages(sinceMillis: Long = 0L): List<SmsMessage> = withContext(Dispatchers.IO) {
        val mmsList = mutableListOf<SmsMessage>()
        // MMS DATE column is in seconds (unlike SMS which is in millis)
        val sinceSeconds = sinceMillis / 1000
        val selection = if (sinceMillis > 0) "${Telephony.Mms.DATE} >= ?" else null
        val selectionArgs = if (sinceMillis > 0) arrayOf(sinceSeconds.toString()) else null

        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.READ, Telephony.Mms.THREAD_ID
            ),
            selection, selectionArgs,
            "${Telephony.Mms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                val dateSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE))
                val msgBox = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1
                val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)) ?: ""

                if (msgBox != Telephony.Mms.MESSAGE_BOX_INBOX && msgBox != Telephony.Mms.MESSAGE_BOX_SENT) continue

                val address = getMmsAddress(mmsId, msgBox) ?: continue
                val body = getMmsBody(mmsId) ?: "[Media message]"

                val mmsType = if (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX) MessageType.INCOMING
                              else MessageType.OUTGOING

                mmsList.add(
                    SmsMessage(
                        id = mmsId,
                        address = address,
                        body = body,
                        timestamp = LocalDateTime.ofEpochSecond(dateSeconds, 0, ZoneOffset.UTC),
                        type = mmsType,
                        contactName = getContactName(address),
                        threadId = threadId,
                        messageType = MessageType.MMS,
                        isRead = isRead,
                        folder = if (mmsType == MessageType.INCOMING) "inbox" else "sent",
                        attachmentCount = 1
                    )
                )
            }
        }
        mmsList
    }

    suspend fun getAllMessagesSince(sinceMillis: Long = 0L): List<SmsMessage> = withContext(Dispatchers.IO) {
        val sms = getSmsMessagesSince(sinceMillis)
        val mms = getMmsMessages(sinceMillis)
        (sms + mms).sortedBy { it.timestamp }
    }

    suspend fun getMessagesForDateAndContact(
        date: LocalDateTime,
        phoneNumber: String
    ): List<SmsMessage> = withContext(Dispatchers.IO) {
        val dayStart = date.withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toEpochSecond(ZoneOffset.UTC) * 1000
        val dayEnd = date.withHour(23).withMinute(59).withSecond(59).withNano(0)
            .toEpochSecond(ZoneOffset.UTC) * 1000
        val normalized = normalizePhone(phoneNumber)

        val result = mutableListOf<SmsMessage>()

        // SMS
        context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.THREAD_ID,
                Telephony.Sms.READ
            ),
            "${Telephony.Sms.DATE} BETWEEN ? AND ?",
            arrayOf(dayStart.toString(), dayEnd.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: continue
                if (normalizePhone(address) != normalized) continue

                val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date2 = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)) ?: ""
                val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                val smsType = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageType.INCOMING
                    Telephony.Sms.MESSAGE_TYPE_SENT -> MessageType.OUTGOING
                    else -> MessageType.UNKNOWN
                }
                result.add(
                    SmsMessage(
                        id = id, address = address, body = body,
                        timestamp = LocalDateTime.ofEpochSecond(date2 / 1000, 0, ZoneOffset.UTC),
                        type = smsType,
                        contactName = getContactName(address),
                        threadId = threadId,
                        messageType = MessageType.TEXT,
                        isRead = isRead,
                        folder = if (smsType == MessageType.INCOMING) "inbox" else "sent"
                    )
                )
            }
        }

        // MMS (DATE column is in seconds)
        val dayStartSec = dayStart / 1000
        val dayEndSec = dayEnd / 1000
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.READ, Telephony.Mms.THREAD_ID
            ),
            "${Telephony.Mms.DATE} BETWEEN ? AND ?",
            arrayOf(dayStartSec.toString(), dayEndSec.toString()),
            "${Telephony.Mms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                val dateSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE))
                val msgBox = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1
                val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)) ?: ""

                if (msgBox != Telephony.Mms.MESSAGE_BOX_INBOX && msgBox != Telephony.Mms.MESSAGE_BOX_SENT) continue

                val address = getMmsAddress(mmsId, msgBox) ?: continue
                if (normalizePhone(address) != normalized) continue

                val body = getMmsBody(mmsId) ?: "[Media message]"
                val mmsType = if (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX) MessageType.INCOMING
                              else MessageType.OUTGOING

                result.add(
                    SmsMessage(
                        id = mmsId, address = address, body = body,
                        timestamp = LocalDateTime.ofEpochSecond(dateSeconds, 0, ZoneOffset.UTC),
                        type = mmsType,
                        contactName = getContactName(address),
                        threadId = threadId,
                        messageType = MessageType.MMS,
                        isRead = isRead,
                        folder = if (mmsType == MessageType.INCOMING) "inbox" else "sent",
                        attachmentCount = 1
                    )
                )
            }
        }

        result.sortedBy { it.timestamp }
    }

    suspend fun searchMessages(keywords: List<String>, limitDays: Int = 90): List<SmsMessage> =
        withContext(Dispatchers.IO) {
            val cutoffMillis = System.currentTimeMillis() - limitDays.toLong() * 86_400_000
            val cutoffSeconds = cutoffMillis / 1000
            val all = mutableListOf<SmsMessage>()

            // SMS search
            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.THREAD_ID,
                    Telephony.Sms.READ
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoffMillis.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    if (keywords.none { it.lowercase() in body.lowercase() }) continue

                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)) ?: ""
                    val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    val smsType = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) MessageType.INCOMING else MessageType.OUTGOING
                    all.add(
                        SmsMessage(
                            id = id, address = address, body = body,
                            timestamp = LocalDateTime.ofEpochSecond(date / 1000, 0, ZoneOffset.UTC),
                            type = smsType, contactName = getContactName(address),
                            threadId = threadId, messageType = MessageType.TEXT,
                            isRead = isRead, folder = if (smsType == MessageType.INCOMING) "inbox" else "sent"
                        )
                    )
                }
            }

            // MMS search (body in separate part table)
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ, Telephony.Mms.THREAD_ID
                ),
                "${Telephony.Mms.DATE} >= ?",
                arrayOf(cutoffSeconds.toString()),
                "${Telephony.Mms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                    val dateSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE))
                    val msgBox = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                    val isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1
                    val threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)) ?: ""

                    if (msgBox != Telephony.Mms.MESSAGE_BOX_INBOX && msgBox != Telephony.Mms.MESSAGE_BOX_SENT) continue

                    val body = getMmsBody(mmsId) ?: continue
                    if (keywords.none { it.lowercase() in body.lowercase() }) continue

                    val address = getMmsAddress(mmsId, msgBox) ?: continue
                    val mmsType = if (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX) MessageType.INCOMING else MessageType.OUTGOING

                    all.add(
                        SmsMessage(
                            id = mmsId, address = address, body = body,
                            timestamp = LocalDateTime.ofEpochSecond(dateSeconds, 0, ZoneOffset.UTC),
                            type = mmsType, contactName = getContactName(address),
                            threadId = threadId, messageType = MessageType.MMS,
                            isRead = isRead, folder = if (mmsType == MessageType.INCOMING) "inbox" else "sent",
                            attachmentCount = 1
                        )
                    )
                }
            }

            all.sortedByDescending { it.timestamp }
        }

    suspend fun getSmsContacts(): List<SmsContact> = withContext(Dispatchers.IO) {
        val contacts = linkedSetOf<String>()
        context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf(Telephony.Sms.ADDRESS),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0)
                if (!address.isNullOrEmpty()) contacts.add(address)
            }
        }
        contacts.map { SmsContact(getContactName(it), it) }
    }

    private fun getContactName(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getMmsAddress(mmsId: Long, msgBox: Int): String? {
        // For inbox messages we want the FROM address; for sent the TO address.
        val preferredType = if (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX) MMS_PDU_FROM else MMS_PDU_TO
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/addr"),
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                null, null, null
            )?.use { cursor ->
                var fallback: String? = null
                while (cursor.moveToNext()) {
                    val addr = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS))
                    if (addr.isNullOrBlank() || addr == "insert-address-token") continue
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE))
                    if (type == preferredType) return@use addr
                    if (fallback == null) fallback = addr
                }
                fallback
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getMmsBody(mmsId: Long): String? {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/part"),
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contentType = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE))
                    if (contentType?.startsWith("text/") == true) {
                        val text = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT))
                        if (!text.isNullOrBlank()) return@use text
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "").let {
            when {
                it.startsWith("+1") && it.length == 12 -> it.substring(2)
                it.startsWith("1") && it.length == 11 -> it.substring(1)
                else -> it
            }
        }
    }
}
