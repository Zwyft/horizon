package com.coparenting.chronicle.horizon.worker

import android.content.Context
import androidx.work.*
import com.coparenting.chronicle.horizon.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltAndroidApp

@AndroidEntryPoint
class WorkManagerSetup {

    companion object {
        private const val DAILY_SCAN_TAG = "DAILY_SCAN_WORKER"
        private const val NOTIFICATION_TAG = "NOTIFICATION_WORKER"

        /**
         * Initialize and schedule periodic work
         */
        fun initializeWorkManager(context: Context) {
            // Cancel existing work to avoid duplicates
            WorkManager.getInstance(context).cancelAllWorkByTag(DAILY_SCAN_TAG)
            WorkManager.getInstance(context).cancelAllWorkByTag(NOTIFICATION_TAG)

            // Schedule daily scan worker (runs every 24 hours)
            val dailyScanWork = PeriodicWorkRequestBuilder<DailyScanWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .setRequiresDeviceIdle(false) // Can run even if device is not idle
                        .build()
                )
                .addTag(DAILY_SCAN_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            // Schedule notification worker to run after daily scan (with delay)
            val notificationWork = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(30, TimeUnit.MINUTES) // Notify 30 minutes after scan starts
                .addTag(NOTIFICATION_TAG)
                .build()

            // Enqueue the work
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY_SCAN_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                dailyScanWork
            )

            WorkManager.getInstance(context).enqueueUniqueWork(
                NOTIFICATION_TAG,
                ExistingWorkPolicy.KEEP,
                notificationWork
            )
        }

        /**
         * Cancel all Horizon-related work
         */
        fun cancelAllWork(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(DAILY_SCAN_TAG)
            WorkManager.getInstance(context).cancelAllWorkByTag(NOTIFICATION_TAG)
        }

        /**
         * Get status of daily scan work
         */
        fun getDailyScanWorkStatus(context: Context): List<WorkInfo> {
            return WorkManager.getInstance(context)
                .getWorkInfosByTagLiveData(DAILY_SCAN_TAG)
                .getValueOrNull() ?: emptyList()
        }

        /**
         * Check if work is currently running
         */
        fun isWorkRunning(context: Context): Boolean {
            val workInfos = getDailyScanWorkStatus(context)
            return workInfos.any { it.state.isRunning }
        }
    }
}
