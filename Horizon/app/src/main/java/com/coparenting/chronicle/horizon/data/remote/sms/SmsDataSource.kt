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

    suspend fun getSmsMessages(): List<SmsMessage> = withContext(Dispatchers.IO) {
        val smsList = mutableListOf<SmsMessage>()

        context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.THREAD_ID,
                Telephony.Sms.READ
            ),
            null, null,
            "${Telephony.Sms.DATE} DESC"
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
        result
    }

    suspend fun searchMessages(keywords: List<String>, limitDays: Int = 90): List<SmsMessage> =
        withContext(Dispatchers.IO) {
            val cutoff = (System.currentTimeMillis() - limitDays.toLong() * 86_400_000)
            val all = mutableListOf<SmsMessage>()

            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.THREAD_ID,
                    Telephony.Sms.READ
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val bodyLower = body.lowercase()
                    if (keywords.any { it.lowercase() in bodyLower }) {
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
            }
            all
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

    private fun getMmsAddress(mmsId: Long): String? {
        return try {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/addr"),
                arrayOf(Telephony.Mms.Addr.ADDRESS),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS))
                else null
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
                    if (contentType.startsWith("text/")) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT))
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
