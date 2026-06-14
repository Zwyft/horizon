package com.coparenting.chronicle.horizon.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.coparenting.chronicle.horizon.domain.usecase.analytics.GenerateAnalyticsReportUseCase
import com.coparenting.chronicle.horizon.domain.usecase.diary.GenerateDiaryEntryUseCase
import com.coparenting.chronicle.horizon.domain.usecase.message.ProcessNewMessagesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyScanWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val processNewMessagesUseCase: ProcessNewMessagesUseCase,
    private val generateDiaryEntryUseCase: GenerateDiaryEntryUseCase,
    private val generateAnalyticsReportUseCase: GenerateAnalyticsReportUseCase
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "DailyScanWorker"
        const val WORK_NAME = "daily_scan"

        fun buildRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<DailyScanWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(TAG)
                .build()
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily scan at ${LocalDateTime.now()}")
        return try {
            processNewMessagesUseCase(LocalDateTime.now().minusHours(24))
            // Generate diary for yesterday so the day is complete before summarising
            val yesterday = LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay()
            generateDiaryEntryUseCase(yesterday)
            generateAnalyticsReportUseCase("current_user")
            Log.d(TAG, "Daily scan completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Daily scan failed", e)
            Result.retry()
        }
    }
}
