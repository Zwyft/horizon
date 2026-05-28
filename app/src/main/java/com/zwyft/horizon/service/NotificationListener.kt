package com.zwyft.horizon.service

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.*
import java.util.*

/**
 * NotificationListenerService (backup capture method).
 *
 * Captures incoming SMS/RCS notifications from messaging apps.
 * Not as reliable as AccessibilityService for RCS, but catches
 * notifications that AccessibilityService might miss.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
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
        if (!isMessagingApp(pkg)) return

        val extras: Bundle? = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString()
        val text  = extras?.getCharSequence("android.text")?.toString()
            ?: extras?.getCharSequence("android.bigText")?.toString()

        if (text.isNullOrBlank()) return

        scope.launch {
            val msg = MessageEntity(
                messageId       = sbn.postTime,
                threadId        = 0L,
                address         = title ?: "unknown",
                contactName     = title,
                body            = text,
                date            = Date(sbn.postTime),
                type            = 1, // received
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { }

    private fun isMessagingApp(pkg: String): Boolean {
        return pkg.contains("messaging") ||
               pkg.contains("mms") ||
               pkg == "com.google.android.apps.messaging" ||
               pkg == "com.android.mms"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
