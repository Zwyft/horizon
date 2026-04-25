package com.coparenting.chronicle.horizon.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.coparenting.chronicle.horizon.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.worker.HiltWorker
import dagger.hilt.android.worker.HiltWorkerFactory
import dagger.hilt.android.worker.WorkerAssisted
import javax.inject.Inject
import java.time.LocalDateTime

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted @WorkerAssisted ctx: Context,
    @Assisted @WorkerAssisted params: WorkerParameters,
    private val context: Context
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "NotificationWorker"
        private const val CHANNEL_ID = "horizon_daily_channel"
        private const val CHANNEL_NAME = "Horizon Daily Updates"
        private const val CHANNEL_DESCRIPTION = "Daily Horizon app notifications"

        fun createWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<NotificationWorker>()
                .addTag(TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        try {
            sendNotification(
                "Horizon Daily Update",
                "Your daily diary entry and analytics are ready for review!"
            )
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

// Factory for Hilt Worker
class NotificationWorkerFactory @Inject constructor(
    private val workerFactory: HiltWorkerFactory
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker {
        return when (workerClassName) {
            NotificationWorker::class.java.name -> {
                NotificationWorker.Factory().create(
                    appContext,
                    workerParameters,
                    workerFactory
                )
            }
            else -> workerFactory.createWorker(appContext, workerClassName, workerParameters)
        }
    }
}

// Factory class for creating Worker with Hilt
class Factory : AssistedFactory<
    NotificationWorker,
    NotificationWorker.AssistedFactory
> {
    @AssistedInject
    constructor(
        private val assistedFactory: NotificationWorker.AssistedFactory
    ) : AssistedFactory(assistedFactory)
}
