package com.zwyft.horizon.cloud

import android.content.Context
import androidx.work.*
import com.zwyft.horizon.data.HorizonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Repository for Google Drive backup/restore operations.
 */
class DriveRepository(
    private val context: Context,
    private val db: HorizonDatabase
) {
    companion object {
        const val  BACKUP_INTERVAL_HOURS = 24L
    }

    private val driveClient = GoogleDriveClient(context)

    /**
     * Check if signed in to Google Drive.
     */
    fun isSignedIn(): Boolean = driveClient.isSignedIn()

    /**
     * Get sign-in intent (launch from activity).
     */
    fun getSignInIntent() = driveClient.getSignInIntent()

    /**
     * Handle sign-in result.
     */
    suspend fun handleSignInResult(data: android.content.Intent?): Boolean =
        driveClient.handleSignInResult(data)

    /**
     * Sign out.
     */
    suspend fun signOut() = driveClient.signOut()

    /**
     * Upload current Room DB to Drive.
     *
     * @return Drive file ID
     */
    suspend fun uploadBackup(): String = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("horizon.db")
        if (!dbFile.exists()) throw IllegalStateException("DB file not found")
        driveClient.uploadBackup(dbFile)
    }

    /**
     * Download and restore a backup from Drive.
     *
     * @param fileId Drive file ID
     * @return true if restore succeeded
     */
    suspend fun restoreBackup(fileId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val restoredFile = driveClient.downloadBackup(fileId)
            // Close current DB
            db.close()
            // Replace current DB with restored file
            val dbFile = context.getDatabasePath("horizon.db")
            restoredFile.copyTo(dbFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * List available backups.
     */
    suspend fun listBackups(): List<com.google.api.services.drive.model.File> =
        driveClient.listBackups()

    /**
     * Delete a backup from Drive.
     */
    suspend fun deleteBackup(fileId: String) =
        driveClient.deleteBackup(fileId)

    /**
     * Schedule automatic daily backup via WorkManager.
     */
    fun scheduleAutoBackup(context: Context) {
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            BACKUP_INTERVAL_HOURS, java.util.concurrent.TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "drive_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

/**
 * WorkManager worker for automatic Drive backup.
 */
class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = DriveRepository(applicationContext, HorizonDatabase.getInstance(applicationContext))
            if (!repo.isSignedIn()) return Result.failure()
            repo.uploadBackup()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
