package com.coparenting.chronicle.horizon.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val CHANNEL_ID = "horizon_daily_channel"
        private const val CHANNEL_NAME = "Horizon Daily Updates"

        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NotificationWorker>().build()
    }

    override suspend fun doWork(): Result {
        return try {
            sendNotification("Horizon", "Your daily journal summary is ready.")
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }
}
