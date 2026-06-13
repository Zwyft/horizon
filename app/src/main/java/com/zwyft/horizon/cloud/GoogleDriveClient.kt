package com.zwyft.horizon.cloud

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

/**
 * Client for Google Drive API operations.
 *
 * Handles:
 * - OAuth sign-in (using GoogleSignIn API)
 * - Upload backup to Drive
 * - Download backup from Drive
 * - List available backups
 * - Delete old backups
 */
class GoogleDriveClient(private val context: Context) {

    companion object {
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val BACKUP_MIME = "application/octet-stream"
        private const val BACKUP_PREFIX = "horizon_backup_"
    }

    private val gso: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Drive.SCOPE_FILE, Drive.SCOPE_APP_DATA_FOLDER)
            .build()
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Get sign-in intent (launch from activity).
     */
    fun getSignInIntent() = googleSignInClient.signInIntent

    /**
     * Handle sign-in result.
     */
    suspend fun handleSignInResult(data: android.content.Intent?): Boolean {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.await()
            // account.account == Google account
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sign out.
     */
    suspend fun signOut() {
        googleSignInClient.signOut().await()
    }

    /**
     * Check if signed in.
     */
    fun isSignedIn(): Boolean =
        GoogleSignIn.getLastSignedInAccount(context) != null

    /**
     * Upload a backup file to Drive (App Data Folder, hidden from user).
     *
     * @param localFile The Room DB file (horizon.db)
     * @return Drive file ID (for future download/delete)
     */
    suspend fun uploadBackup(localFile: java.io.File): String = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: throw IllegalStateException("Not signed in")

        val drive = Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(),
            com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_APPDATA)
            ).setSelectedAccount(account.account)
        ).setApplicationName("Horizon").build()

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss").format(Date())
        val metadata = com.google.api.services.drive.model.File().apply {
            name = "${BACKUP_PREFIX}${timestamp}.db"
            parents = listOf(APP_DATA_FOLDER)
            mimeType = BACKUP_MIME
        }

        val content = com.google.api.client.http.FileContent(BACKUP_MIME, localFile)
        val result = drive.files().create(metadata, content).setFields("id").execute()
        result.id
    }

    /**
     * List available backups (newest first).
     */
    suspend fun listBackups(): List<com.google.api.services.drive.model.File> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return emptyList()

        val drive = Drive.Builder(/* ... same as above ... */)
            .build()

        val result = drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setFields("files(id, name, createdTime, size)")
            .execute()

        result.files?.sortedByDescending { it.createdTime } ?: emptyList()
    }

    /**
     * Download a backup by Drive file ID.
     *
     * @return Local file (in cache dir)
     */
    suspend fun downloadBackup(fileId: String): java.io.File = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: throw IllegalStateException("Not signed in")
        val drive = Drive.Builder(/* ... */).build()

        val outputFile = java.io.File(context.cacheDir, "horizon_restored.db")
        drive.files().get(fileId).executeMediaAndDownloadTo(FileOutputStream(outputFile))
        outputFile
    }

    /**
     * Delete a backup from Drive.
     */
    suspend fun deleteBackup(fileId: String) = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return
        val drive = Drive.Builder(/* ... */).build()
        drive.files().delete(fileId).execute()
    }
}
