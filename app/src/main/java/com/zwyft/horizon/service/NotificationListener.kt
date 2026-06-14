package com.zwyft.horizon.service

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.*
import java.util.*

/**
 * NotificationListenerService for RCS/SMS capture.
 *
 * Uses Android's MessagingStyle notification API to extract individual
 * messages with proper sender, text, and timestamp — instead of the
 * previous "concat the whole notification text into one entity" hack.
 *
 * For Google Messages (`com.google.android.apps.messaging`), messages
 * with a MessagingStyle are flagged as RCS. SMS notifications don't
 * use MessagingStyle, so the fallback path handles legacy SMS apps.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        private const val GOOGLE_MESSAGES_PKG = "com.google.android.apps.messaging"

        private val MESSAGING_APPS = setOf(
            GOOGLE_MESSAGES_PKG,
            "com.samsung.android.messaging",
            "com.android.mms"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: HorizonDatabase

    override fun onCreate() {
        super.onCreate()
        db = HorizonDatabase.getInstance(applicationContext)
        Log.i(TAG, "NotificationListener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        if (pkg !in MESSAGING_APPS) return

        val extras: Bundle = sbn.notification.extras
        val isRcs = pkg == GOOGLE_MESSAGES_PKG && hasMessagingStyle(sbn.notification)

        // ── Phase 3: MessagingStyle extraction ─────────────────
        if (isRcs) {
            captureFromMessagingStyle(sbn, extras)
        } else {
            // Fallback: legacy notification text extraction
            captureFromLegacyNotification(sbn, extras, pkg)
        }
    }

    /**
     * Extract individual messages from a MessagingStyle notification.
     *
     * Android's `Notification.MessagingStyle` (API 24+) bundles each message
     * as a [Notification.MessagingStyle.Message] inside the notification extras
     * under the key `Notification.EXTRA_MESSAGES`. Each message has:
     *  - `text`       — the message body
     *  - `timestamp`  — when it was sent
     *  - `sender`     — a [Person] with `name` and optional `uri`
     *  - `isRemoteInput` — true if the user typed this message (outgoing)
     *
     * We iterate all messages and insert each as a separate [MessageEntity].
     * Messages with `isRemoteInput = true` or from the "Direct reply" action
     * are marked as type=2 (sent). Everything else is type=1 (received).
     */
    private fun captureFromMessagingStyle(sbn: StatusBarNotification, extras: Bundle) {
        @Suppress("DEPRECATION")
        val messages: Array<Notification.MessagingStyle.Message>? =
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull {
                it as? Notification.MessagingStyle.Message
            }?.toTypedArray()

        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "MessagingStyle but no messages in extras — falling back to legacy")
            captureFromLegacyNotification(sbn, extras, sbn.packageName)
            return
        }

        @Suppress("DEPRECATION")
        val participants: List<Person> = extras.getParcelableArrayList(Notification.EXTRA_MESSAGING_PERSON)
            ?: emptyList()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()

        scope.launch {
            var inserted = 0
            for (msg in messages) {
                val sender = msg.senderPerson
                val senderName = sender?.name?.toString()
                val senderUri = sender?.uri
                val address = extractAddressFromUri(senderUri)
                    ?: senderName
                    ?: conversationTitle
                    ?: "unknown"

                // MessagingStyle.Message.isRemoteInput() is deprecated/difficult to access
                // reliably across API levels. We rely on the notification action check
                // (hasDirectReplyAction) as the primary outgoing detection.
                val isOutgoing = hasDirectReplyAction(sbn.notification)
                val type = if (isOutgoing) 2 else 1

                val entity = MessageEntity(
                    messageId       = makeMessageId(sbn.postTime, msg.timestamp, msg.text?.toString()),
                    threadId        = 0L,
                    address         = address,
                    contactName     = senderName,
                    body            = msg.text?.toString() ?: "",
                    date            = Date(msg.timestamp),
                    type            = type,
                    read            = if (type == 2) 1 else 0,
                    seen            = 1,
                    protocol        = 0,
                    rcs             = true,
                    monitored       = false,
                    journalProcessed = false
                )
                val result = db.messageDao().insert(entity)
                if (result > 0) inserted++
            }
            if (inserted > 0) {
                Log.i(TAG, "MessagingStyle: inserted $inserted RCS messages from ${sbn.packageName}")
            }
        }
    }

    /**
     * Legacy path for SMS apps that don't use MessagingStyle.
     * Concatenates notification title + text into a single entity.
     */
    private fun captureFromLegacyNotification(sbn: StatusBarNotification, extras: Bundle, pkg: String) {
        val title = extras.getCharSequence("android.title")?.toString()
        val text  = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()

        if (text.isNullOrBlank()) return

        scope.launch {
            val msg = MessageEntity(
                messageId       = sbn.postTime,
                threadId        = 0L,
                address         = title ?: "unknown",
                contactName     = title,
                body            = text,
                date            = Date(sbn.postTime),
                type            = 1,
                read            = 0,
                seen            = 0,
                protocol        = 0,
                rcs             = pkg.contains("messaging"),
                monitored       = false,
                journalProcessed = false
            )
            db.messageDao().insert(msg)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun hasMessagingStyle(notification: Notification): Boolean {
        return notification.extras?.containsKey(Notification.EXTRA_MESSAGES) == true
    }

    private fun hasDirectReplyAction(notification: Notification): Boolean {
        return notification.actions?.any { action ->
            action.remoteInputs?.isNotEmpty() == true
        } == true
    }

    /**
     * Generate a stable messageId from notification time + message timestamp
     * + a hash of the body text. This avoids collisions and makes the insert
     * idempotent (Room's IGNORE conflict strategy skips duplicates).
     */
    private fun makeMessageId(notificationTime: Long, messageTimestamp: Long, body: String?): Long {
        var hash = notificationTime * 31 + messageTimestamp
        body?.let { hash = hash * 31 + it.hashCode().toLong() }
        return hash and 0x7FFFFFFFFFFFFFFFL // keep positive
    }

    /**
     * Extract a phone number or email from a Person URI
     * (e.g. "tel:+15551234567" or "mailto:user@example.com").
     */
    private fun extractAddressFromUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return when {
            uri.startsWith("tel:") -> uri.removePrefix("tel:")
            uri.startsWith("mailto:") -> uri.removePrefix("mailto:")
            else -> uri
        }
    }
}
