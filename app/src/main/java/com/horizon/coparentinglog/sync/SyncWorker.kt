package com.horizon.coparentinglog.sync

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.horizon.coparentinglog.sync.SyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            SyncCoordinator(applicationContext).syncArchive()
        }
        return Result.success()
    }
}
