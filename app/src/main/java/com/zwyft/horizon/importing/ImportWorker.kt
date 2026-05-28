package com.zwyft.horizon.importing

import android.content.Context
import androidx.work.*
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.di.DatabaseModule
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.*

/**
 * WorkManager worker that runs an import in the background.
 * Reports progress via WorkManager progress Data.
 *
 * InputData keys:
 * - "file_path": absolute path to the XML/JSON file
 * - "batch_tag": (optional) tag for resume support
 */
class ImportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_BATCH_TAG = "batch_tag"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL    = "total"
        const val KEY_DONE     = "done"
        const val KEY_ERROR    = "error"

        fun enqueue(context: Context, file: File, batchTag: String? = null): UUID {
            val data = Data.Builder()
                .putString(KEY_FILE_PATH, file.absolutePath)
                .putString(KEY_BATCH_TAG, batchTag ?: UUID.randomUUID().toString())
                .build()

            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "import_${file.name}",
                ExistingWorkPolicy.KEEP,
                request
            )
            return request.id
        }
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val batchTag  = inputData.getString(KEY_BATCH_TAG) ?: UUID.randomUUID().toString()
        val file = File(filePath)
        if (!file.exists()) return Result.failure()

        return try {
            // Build repo manually (worker is outside Hilt graph)
            val db = DatabaseModule.provideDatabase(applicationContext)
            val contactDao = db.contactDao()
            val repo = ImportRepository(applicationContext, db, contactDao)

            var processed = 0
            repo.importFile(file, batchTag).collect { progress ->
                when (progress) {
                    is ImportRepository.ImportProgress.Started -> {
                        setProgressAsync(
                            Data.Builder().putInt(KEY_PROGRESS, 0).build()
                        )
                    }
                    is ImportRepository.ImportProgress.Progress -> {
                        processed = progress.processed
                        setProgressAsync(
                            Data.Builder().putInt(KEY_PROGRESS, processed).build()
                        )
                    }
                    is ImportRepository.ImportProgress.Done -> {
                        setProgressAsync(
                            Data.Builder()
                                .putInt(KEY_DONE, progress.totalImported)
                                .putString(KEY_BATCH_TAG, progress.batchTag)
                                .build()
                        )
                    }
                    is ImportRepository.ImportProgress.Error -> {
                        setProgressAsync(
                            Data.Builder().putString(KEY_ERROR, progress.message).build()
                        )
                    }
                }
            }
            Result.success(
                Data.Builder().putInt(KEY_DONE, processed).build()
            )
        } catch (e: Exception) {
            Result.failure(Data.Builder().putString(KEY_ERROR, e.message).build())
        }
    }
}
