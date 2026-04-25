package com.coparenting.chronicle.horizon.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.coparenting.chronicle.horizon.data.local.database.HorizonDatabase
import com.coparenting.chronicle.horizon.data.local.database.dao.MessageDao
import com.coparenting.chronicle.horizon.data.remote.sms.SmsDataSource
import com.coparenting.chronicle.horizon.domain.usecase.message.ProcessNewMessagesUseCase
import com.coparenting.chronicle.horizon.domain.usecase.diary.GenerateDiaryEntryUseCase
import com.coparenting.chronicle.horizon.domain.usecase.analytics.GenerateAnalyticsReportUseCase
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.worker.HiltWorker
import dagger.hilt.android.worker.HiltWorkerFactory
import dagger.hilt.android.worker.WorkerAssisted
import javax.inject.Inject
import java.time.LocalDateTime

@HiltWorker
class DailyScanWorker @AssistedInject constructor(
    @Assisted @WorkerAssisted ctx: Context,
    @Assisted @WorkerAssisted params: WorkerParameters,
    private val processNewMessagesUseCase: ProcessNewMessagesUseCase,
    private val generateDiaryEntryUseCase: GenerateDiaryEntryUseCase,
    private val generateAnalyticsReportUseCase: GenerateAnalyticsReportUseCase
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "DailyScanWorker"
        const val WORKER_ACTION = "ACTION_DAILY_SCAN"

        fun createWorkRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<DailyScanWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .addTag(TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily scan")
        
        try {
            // 1. Sync new messages from SMS
            Log.d(TAG, "Syncing new messages")
            val lastSyncTime = getLastSyncTime()
            val newMessagesResult = processNewMessagesUseCase(lastSyncTime)
            
            if (newMessagesResult.isFailure) {
                throw newMessagesResult.exceptionOrNull() ?: Exception("Unknown error processing messages")
            }
            
            val newMessages = newMessagesResult.getOrNull() ?: emptyList()
            Log.d(TAG, "Processed ${newMessages.size} new messages")
            
            // Update last sync time
            saveLastSyncTime(LocalDateTime.now())
            
            // 2. Generate diary entry for today (if not already generated)
            Log.d(TAG, "Generating diary entry for today")
            val diaryResult = generateDiaryEntryUseCase(LocalDateTime.now())
            
            if (diaryResult.isFailure) {
                Log.w(TAG, "Failed to generate diary entry: ${diaryResult.exceptionOrNull()?.message}")
            } else {
                val diaryEntry = diaryResult.getOrNull()
                Log.d(TAG, "Generated diary entry: ${diaryEntry?.title}")
            }
            
            // 3. Generate/update analytics
            Log.d(TAG, "Generating analytics report")
            val analyticsResult = generateAnalyticsReportUseCase("current_user")
            
            if (analyticsResult.isFailure) {
                Log.w(TAG, "Failed to generate analytics: ${analyticsResult.exceptionOrNull()?.message}")
            } else {
                val analyticsReport = analyticsResult.getOrNull()
                Log.d(TAG, "Generated analytics report")
            }
            
            // 4. Send notification (optional - would use NotificationWorker)
            Log.d(TAG, "Daily scan completed successfully")
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during daily scan", e)
            return Result.retry()
        }
    }

    private fun getLastSyncTime(): LocalDateTime {
        // In a real implementation, this would be stored in SharedPreferences or DB
        // For now, we'll return a fixed time (e.g., 24 hours ago)
        return LocalDateTime.now().minusHours(24)
    }

    private fun saveLastSyncTime(time: LocalDateTime) {
        // In a real implementation, this would be stored in SharedPreferences or DB
        // For now, we'll just log it
        Log.d("DailyScanWorker", "Last sync time saved: $time")
    }
}

// Factory for Hilt Worker
class DailyScanWorkerFactory @Inject constructor(
    private val workerFactory: HiltWorkerFactory
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker {
        return when (workerClassName) {
            DailyScanWorker::class.java.name -> {
                DailyScanWorker.Factory().create(
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
    DailyScanWorker,
    DailyScanWorker.AssistedFactory
> {
    @AssistedInject
    constructor(
        private val assistedFactory: DailyScanWorker.AssistedFactory
    ) : AssistedFactory(assistedFactory)
}
