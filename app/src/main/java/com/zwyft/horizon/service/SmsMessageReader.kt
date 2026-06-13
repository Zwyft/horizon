package com.zwyft.horizon.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Reads historical SMS messages from the Android system ContentProvider.
 *
 * This is the ONLY reliable way to read past SMS history on modern Android —
 * `NotificationListenerService` and `AccessibilityService` only see NEW messages.
 *
 * URIs:
 *  - `content://sms/inbox`  — received
 *  - `content://sms/sent`   — sent
 *  - `content://sms`        — all
 *
 * Requires `READ_SMS` runtime permission. MMS / RCS are out of scope here — RCS
 * is not exposed by the platform, and MMS requires a much more involved parser.
 */
class SmsMessageReader(private val context: Context) {

    /** True if the app currently holds READ_SMS permission. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Stream all SMS messages (inbox + sent) from the system provider.
     *
     * @param sinceMillis If non-null, only return messages newer than this timestamp.
     * @param batchTag    Tag written to MessageEntity.importedFrom for resume support.
     * @param limit       Maximum number of messages to return.
     */
    suspend fun readAll(
        sinceMillis: Long? = null,
        batchTag: String = "sms_sync",
        limit: Int = 5000
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val results = mutableListOf<MessageEntity>()

        // Read inbox
        results += readUri(
            Telephony.Sms.Inbox.CONTENT_URI,
            type = 1,
            batchTag = batchTag,
            sinceMillis = sinceMillis,
            remaining = limit
        )

        // Read sent (only if we still have budget)
        val remaining = limit - results.size
        if (remaining > 0) {
            results += readUri(
                Telephony.Sms.Sent.CONTENT_URI,
                type = 2,
                batchTag = batchTag,
                sinceMillis = sinceMillis,
                remaining = remaining
            )
        }

        results
    }

    private fun readUri(
        uri: Uri,
        type: Int,
        batchTag: String,
        sinceMillis: Long?,
        remaining: Int
    ): List<MessageEntity> {
        if (remaining <= 0) return emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.PROTOCOL,  // 0 = SMS, 1 = MMS (when this row is from content://mms)
        )

        val selection = if (sinceMillis != null) "${Telephony.Sms.DATE} > ?" else null
        val selectionArgs = if (sinceMillis != null) arrayOf(sinceMillis.toString()) else null
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $remaining"

        val out = mutableListOf<MessageEntity>()
        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { c ->
            val idIdx       = c.getColumnIndex(Telephony.Sms._ID)
            val threadIdx   = c.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIdx  = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx     = c.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx     = c.getColumnIndex(Telephony.Sms.DATE)
            val dateSentIdx = c.getColumnIndexOrNull(Telephony.Sms.DATE_SENT)
            val readIdx     = c.getColumnIndexOrNull(Telephony.Sms.READ)
            val seenIdx     = c.getColumnIndexOrNull(Telephony.Sms.SEEN)

            while (c.moveToNext()) {
                val rawId = if (idIdx >= 0) c.getLong(idIdx) else System.currentTimeMillis()
                val dateMillis = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()

                out += MessageEntity(
                    messageId       = rawId,
                    threadId        = if (threadIdx >= 0) c.getLong(threadIdx) else 0L,
                    address         = if (addressIdx >= 0) c.getString(addressIdx) ?: "unknown" else "unknown",
                    contactName     = null,
                    body            = if (bodyIdx >= 0) c.getString(bodyIdx) else "",
                    date            = Date(dateMillis),
                    dateSent        = if (dateSentIdx != null && dateSentIdx >= 0) c.getLong(dateSentIdx).let { Date(it) } else null,
                    type            = type,
                    read            = if (readIdx != null && readIdx >= 0) c.getInt(readIdx) else 0,
                    seen            = if (seenIdx != null && seenIdx >= 0) c.getInt(seenIdx) else 0,
                    protocol        = 0, // SMS rows are always protocol=0
                    rcs             = false,
                    monitored       = false, // set later by sync manager
                    importedFrom    = batchTag,
                    journalProcessed = false
                )
            }
        }
        return out
    }

    private fun android.database.Cursor.getColumnIndexOrNull(name: String): Int? {
        val idx = getColumnIndex(name)
        return if (idx < 0) null else idx
    }
}
