package com.zwyft.horizon.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.zwyft.horizon.R
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.dao.SettingDao
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Daily WorkManager worker that generates a journal entry every day.
 *
 * Runs once daily (flex window in the evening) and generates a diary entry
 * for the day's co-parenting messages. Works even on quiet days (few messages).
 */
class AutoJournalWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "auto_journal_daily"
        const val REPEAT_INTERVAL_HOURS = 24L
        const val FLEX_INTERVAL_MINUTES = 60L    // 1 hour flex window
        const val NOTIF_CHANNEL = "horizon_journal"
        const val NOTIF_ID = 2001

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoJournalWorker>(
                REPEAT_INTERVAL_HOURS, TimeUnit.HOURS,
                FLEX_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL,
                    "Daily Journal",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Your daily co-parenting diary entry is ready"
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }

        private fun postJournalNotification(context: Context, title: String, entryId: Long, isQuiet: Boolean) {
            val contentText = if (isQuiet) "A quiet day — but your diary is updated." else title
            val notification = NotificationCompat.Builder(context, NOTIF_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(if (isQuiet) "📓 Quiet Day Recorded" else "📓 New Diary Entry")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    if (isQuiet) "No messages today, but your daily diary entry has been created. Tap to view."
                    else "Your daily co-parenting diary entry is ready. Tap to read."
                ))
                .setPriority(if (isQuiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val db = HorizonDatabase.getInstance(applicationContext)
            val settingDao: SettingDao = db.settingDao()

            val autoJournal = settingDao.getValue("auto_journal_enabled") ?: "true"
            if (autoJournal != "true") return Result.success()

            val apiKey = settingDao.getValue("nous_api_key") ?: return Result.failure()

            // Generate journal for today (midnight to now)
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStart = cal.time
            val now = Date()

            // Check if we already generated an entry for today
            val existingEntries = db.journalEntryDao().getInRange(todayStart, now)
            if (existingEntries.isNotEmpty()) return Result.success()

            // Fetch today's messages
            val todayMessages = db.messageDao().getMonitoredInRange(todayStart, now)
            val isQuiet = todayMessages.isEmpty()

            val repo = JournalRepository(db, apiKey)

            if (isQuiet) {
                // Create a minimal quiet-day entry without AI call
                val dayLabel = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(now)
                val entry = com.zwyft.horizon.data.entity.JournalEntryEntity(
                    title = "📭 Quiet Day — $dayLabel",
                    body = buildString {
                        appendLine("# 📭 A Quiet Day")
                        appendLine()
                        appendLine("> No co-parenting messages were exchanged today.")
                        appendLine()
                        appendLine("### 🎵 Overall Tone: Peaceful")
                        appendLine()
                        appendLine("Sometimes a quiet day is a good day. No news means no conflicts, " +
                            "no scheduling stress — just a peaceful day in your co-parenting journey.")
                    },
                    summary = "No messages exchanged today. A peaceful day.",
                    dateStart = todayStart,
                    dateEnd = now,
                    modelUsed = "local",
                    sentimentOverall = 0.2f
                )
                val id = db.journalEntryDao().insert(entry)
                settingDao.setValue("last_auto_journal", System.currentTimeMillis().toString())
                postJournalNotification(applicationContext, entry.title, id, isQuiet = true)
                Result.success()
            } else {
                // Generate diary entry for today's messages
                val entry = repo.generateJournalEntryFromMessages(todayMessages, todayStart, now)

                if (entry != null) {
                    settingDao.setValue("last_auto_journal", System.currentTimeMillis().toString())
                    postJournalNotification(applicationContext, entry.title, entry.id, isQuiet = false)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
