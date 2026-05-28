package com.zwyft.horizon.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.dao.SettingDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.*

/**
 * WorkManager worker that generates a journal entry in the background.
 *
 * InputData keys:
 * - "start_date": epoch millis
 * - "end_date": epoch millis
 */
@HiltWorker
class JournalWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_START = "start_date"
        const val KEY_END   = "end_date"
        const val KEY_ENTRY_ID = "entry_id"

        fun enqueue(context: Context, start: Date, end: Date): UUID {
            val data = Data.Builder()
                .putLong(KEY_START, start.time)
                .putLong(KEY_END, end.time)
                .build()

            val request = OneTimeWorkRequestBuilder<JournalWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "journal_${start.time}_${end.time}",
                ExistingWorkPolicy.KEEP,
                request
            )
            return request.id
        }
    }

    override suspend fun doWork(): Result {
        val startMillis = inputData.getLong(KEY_START, 0L)
        val endMillis   = inputData.getLong(KEY_END, 0L)
        if (startMillis == 0L || endMillis == 0L) return Result.failure()

        val start = Date(startMillis)
        val end   = Date(endMillis)

        return try {
            // Fetch API key from settings
            val db = HorizonDatabase.getInstance(applicationContext)
            val settingDao: SettingDao = db.settingDao()
            val apiKey = settingDao.getValue("nous_api_key") ?: return Result.failure()

            val repo = JournalRepository(db, apiKey)
            val entry = repo.generateJournalEntry(start, end)

            if (entry == null) {
                Result.retry()
            } else {
                Result.success(
                    Data.Builder()
                        .putLong(KEY_ENTRY_ID, entry.id)
                        .build()
                )
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
