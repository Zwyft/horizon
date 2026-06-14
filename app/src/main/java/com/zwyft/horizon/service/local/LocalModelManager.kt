package com.zwyft.horizon.service.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages downloading and tracking `.task` model files for the on-device
 * LLM. Models are stored in `filesDir/models/` and are NOT bundled in
 * the APK (a Gemma 3 1B int4 model is ~600 MB).
 *
 * Download progress is exposed as a [StateFlow] so the Settings UI can
 * show a progress bar. Downloads use OkHttp and run on [Dispatchers.IO].
 *
 * Registry of known model files with their download URLs:
 * (These come from Google's official MediaPipe model zoo at
 *  https://ai.google.dev/gemma — and mirror what the AI Edge Gallery
 *  sample app downloads.)
 */
object LocalModelManager {

    private const val TAG = "LocalModelManager"

    /** Known model files and their download URLs (Google's storage bucket). */
    data class ModelInfo(
        val fileName: String,
        val label: String,
        val downloadUrl: String,
        val sizeBytes: Long
    )

    val KNOWN_MODELS: Map<String, ModelInfo> = mapOf(
        "local:gemma-3-1b-it-int4" to ModelInfo(
            fileName = "gemma3-1b-it-int4.task",
            label = "Gemma 3 1B IT (int4) — 555 MB",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            sizeBytes = 555_000_000L
        ),
        "local:gemma-3-2b-it-int4" to ModelInfo(
            fileName = "gemma3-2b-it-int4.task",
            label = "Gemma 3 2B IT (int4) — 1.5 GB",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-2B-IT/resolve/main/gemma3-2b-it-int4.task",
            sizeBytes = 1_500_000_000L
        ),
        "local:phi-4-mini-int4" to ModelInfo(
            fileName = "phi-4-mini-int4.task",
            label = "Phi-4 Mini (int4) — 2.4 GB",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-int4/resolve/main/phi-4-mini-int4.task",
            sizeBytes = 2_400_000_000L
        )
    )

    // ── Download progress ──────────────────────────────────────

    data class DownloadState(
        val downloading: Boolean = false,
        val fileName: String? = null,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0,
        val error: String? = null
    )

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    // ── File management ────────────────────────────────────────

    /** Root directory for model files. */
    private fun modelsDir(context: Context): File {
        val dir = File(context.filesDir, LocalLlmEngine.MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Absolute path for a model file by model ID (e.g. `local:gemma-3-1b-it-int4`).
     * Returns null if the model ID is unknown.
     */
    fun getModelPath(context: Context, modelId: String): String? {
        val info = KNOWN_MODELS[modelId] ?: return null
        return File(modelsDir(context), info.fileName).absolutePath
    }

    /**
     * Check if a model file exists on disk (fully downloaded).
     */
    fun isModelDownloaded(context: Context, modelId: String): Boolean {
        val path = getModelPath(context, modelId) ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    /**
     * Get the file size on disk for a downloaded model. 0 if not downloaded.
     */
    fun getModelSizeBytes(context: Context, modelId: String): Long {
        val path = getModelPath(context, modelId) ?: return 0L
        return File(path).length()
    }

    /**
     * Delete a downloaded model file. Returns true if the file was deleted.
     */
    fun deleteModel(context: Context, modelId: String): Boolean {
        val path = getModelPath(context, modelId) ?: return false
        return File(path).delete()
    }

    // ── Download ───────────────────────────────────────────────

    /**
     * Download a model file. Emits progress via [downloadState]. Runs on
     * [Dispatchers.IO].
     *
     * Models are hosted on HuggingFace (litert-community). Public models
     * download directly without any API key. Gated models (Gemma, Phi)
     * require accepting the license on huggingface.co first — no API key
     * needed, just visit the model page and click "Accept License".
     */
    suspend fun downloadModel(context: Context, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        val info = KNOWN_MODELS[modelId]
            ?: return@withContext Result.failure(IllegalStateException("Unknown model: $modelId"))

        val dir = modelsDir(context)
        val file = File(dir, info.fileName)

        // Skip if already downloaded (quick check)
        if (file.exists() && file.length() > 0) {
            _downloadState.value = DownloadState()
            return@withContext Result.success(file.absolutePath)
        }

        _downloadState.value = DownloadState(
            downloading = true,
            fileName = info.fileName,
            bytesDownloaded = 0,
            totalBytes = info.sizeBytes
        )

        try {
            val request = Request.Builder()
                .url(info.downloadUrl)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                val hint = if (code == 401 || code == 403) {
                    "\n\nThis model requires accepting the license on HuggingFace first." +
                    "\n1. Open: huggingface.co/litert-community/${info.fileName.removeSuffix(".task")}" +
                    "\n2. Tap 'Accept License' on the model page" +
                    "\n3. Come back and tap Download again" +
                    "\n\nNo API key or HuggingFace token is needed."
                } else ""
                val err = "Download failed (HTTP $code)$hint"
                _downloadState.value = DownloadState(error = err)
                return@withContext Result.failure(IOException(err))
            }

            val body = response.body ?: run {
                _downloadState.value = DownloadState(error = "Empty response body")
                return@withContext Result.failure(IOException("Empty response body"))
            }

            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) contentLength else info.sizeBytes

            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                body.byteStream().use { input ->
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(buffer, 0, n)
                        bytesRead += n
                        _downloadState.value = _downloadState.value.copy(
                            bytesDownloaded = bytesRead,
                            totalBytes = totalBytes
                        )
                    }
                }
            }

            _downloadState.value = DownloadState()
            Log.i(TAG, "Downloaded ${info.fileName} to ${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            val msg = if (e is java.net.UnknownHostException || e.message?.contains("Unable to resolve host") == true) {
                "Network error — check your internet connection and try again."
            } else {
                e.message ?: "Unknown error"
            }
            _downloadState.value = DownloadState(error = msg)
            // Clean up partial file
            if (file.exists()) file.delete()
            Result.failure(e)
        }
    }

    /**
     * Cancel any in-progress download by resetting the state.
     * The actual OkHttp call will keep running but the result will be ignored.
     */
    fun cancelDownload() {
        _downloadState.value = DownloadState()
    }
}

private class IOException(message: String) : java.io.IOException(message)
