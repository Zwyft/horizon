package com.zwyft.horizon.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.*
import java.util.*

/**
 * AccessibilityService that captures RCS/SMS messages in real-time.
 *
 * Hooks into Google Messages (and other SMS apps) by listening for
 * TYPE_WINDOW_CONTENT_CHANGED and TYPE_NOTIFICATION_STATE_CHANGED events,
 * then scraping the message text, sender, and timestamp from the node tree.
 *
 * This is the only way to capture RCS messages on modern Android,
 * since RCS doesn't use the SMS ContentProvider.
 */
class MessageCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "MessageCaptureService"
        private val TARGET_PACKAGES = setOf(
            "com.google.android.apps.messaging", // Google Messages
            "com.android.mms",                   // AOSP MMS
            "com.samsung.android.messaging"       // Samsung Messages
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: HorizonDatabase

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = HorizonDatabase.getInstance(applicationContext)
        Log.i(TAG, "MessageCaptureService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Notification-based capture (backup method)
                captureFromNotification(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Scrape the current window for new messages
                // (Best-effort; layout varies by app version)
                // captureFromWindow(rootInActiveWindow)
            }
        }
    }

    private fun captureFromNotification(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: return
        val notification = event.parcelableData
        // message from notification text
        serviceScope.launch {
            val msg = MessageEntity(
                messageId       = System.currentTimeMillis(),
                threadId        = 0L,
                address         = extractAddress(text),
                contactName     = null,
                body            = text,
                date            = Date(),
                type            = 1, // received
                read            = 0,
                seen            = 0,
                protocol        = 0,
                rcs             = true,
                monitored       = false, // set later by repo
                journalProcessed = false
            )
            db.messageDao().insert(msg)
        }
    }

    /**
     * Best-effort address extraction from notification text.
     * Looks for phone number patterns.
     */
    private fun extractAddress(text: String): String {
        val phoneRegex = Regex("""\b\d{10,}\b""")
        return phoneRegex.find(text)?.value ?: "unknown"
    }

    // ── Window scraping (disabled by default; fragile) ─────────
    // private fun captureFromWindow(root: AccessibilityNodeInfo?) { ... }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
