package com.zwyft.horizon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zwyft.horizon.R
import com.zwyft.horizon.data.HorizonDatabase
import kotlinx.coroutines.*
import java.util.*

/**
 * Foreground service that processes captured messages.
 *
 * Responsibilities:
 * - Resolve contact names from phone numbers
 * - Mark messages from monitored contacts
 * - Trigger journal generation (optional, can be batched)
 */
class MessageProcessingService : Service() {

    companion object {
        const val CHANNEL_ID = "horizon_msg_processing"
        const val NOTIF_ID  = 1001
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: HorizonDatabase

    override fun onCreate() {
        super.onCreate()
        db = HorizonDatabase.getInstance(applicationContext)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Processing messages..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            processMessages()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun processMessages() {
        // Fetch unprocessed messages
        val unprocessed = db.messageDao().getUnprocessedForJournal(batchSize = 100)
        if (unprocessed.isEmpty()) {
            updateNotification("No new messages to process")
            return
        }

        updateNotification("Processing ${unprocessed.size} messages...")

        // Resolve contact names + mark monitored
        val contacts = db.contactDao().getMonitored().first()
        val monitoredNumbers = contacts.associateBy { it.normalizedPhoneNumber }

        unprocessed.forEach { msg ->
            val normalized = msg.address.normalizePhone()
            val contact = monitoredNumbers[normalized]
            if (contact != null) {
                msg.contactName = contact.name
                msg.monitored = true
            }
        }

        // Mark as processed (journalProcessed = true)
        db.messageDao().markJournalProcessed(unprocessed.map { it.id })

        updateNotification("Processed ${unprocessed.size} messages")
    }

    private fun String.normalizePhone(): String = this.replace(Regex("[^0-9]"), "")

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Message Processing",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Background message processing for Horizon" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(content: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Horizon")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun updateNotification(content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
