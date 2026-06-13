package com.zwyft.horizon.service.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zwyft.horizon.MainActivity
import com.zwyft.horizon.R
import kotlinx.coroutines.*

/**
 * Foreground service that hosts the on-device LLM runtime lifecycle:
 *
 *  - Loads the selected `.task` model into [LocalLlmEngine]
 *  - Starts/stops [LocalLlmServer] (Ktor on 127.0.0.1:8088)
 *  - Shows a persistent low-priority notification so Android doesn't
 *    kill the process (foreground service with `dataSync` type).
 *
 * Start/stop life cycle:
 *  - [onStartCommand] receives ACTION_START / ACTION_STOP via Intent.
 *  - On start: load model, start server, show notification.
 *  - On stop: stop server, close engine, remove notification, stop self.
 *
 * The engine loading can take 5–30 seconds (GPU shader compilation);
 * the service transitions to foreground *before* loading so the
 * notification is visible during the load.
 */
class LocalLlmServerService : Service() {

    companion object {
        private const val TAG = "LocalLlmServerService"
        private const val CHANNEL_ID = "local_llm"
        private const val NOTIFICATION_ID = 42

        private const val ACTION_START = "com.zwyft.horizon.action.LOCAL_LLM_START"
        private const val ACTION_STOP  = "com.zwyft.horizon.action.LOCAL_LLM_STOP"
        private const val EXTRA_MODEL_ID = "model_id"

        /** Start the local LLM service. The model file must already be downloaded. */
        fun start(context: Context, modelId: String) {
            val intent = Intent(context, LocalLlmServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stop the local LLM service. */
        fun stop(context: Context) {
            val intent = Intent(context, LocalLlmServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** Check if the service is currently running (best-effort via static flag). */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var llmEngine: LocalLlmEngine? = null
    private var llmServer: LocalLlmServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                    ?: "local:gemma-3-1b-it-int4"
                startForeground(NOTIFICATION_ID, buildNotification("Starting local AI…"))
                scope.launch { loadAndStart(modelId) }
            }
            ACTION_STOP -> {
                scope.launch { shutdown() }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Lifecycle helpers ─────────────────────────────────────

    private suspend fun loadAndStart(modelId: String) {
        // 1. Load the model (may take 5–30 s)
        val modelPath = LocalModelManager.getModelPath(this, modelId)
        if (modelPath == null) {
            Log.e(TAG, "Unknown model ID: $modelId")
            stopSelf()
            return
        }
        val loaded = runCatching {
            LocalLlmEngine.create(this, modelPath)
        }.onFailure { e ->
            Log.e(TAG, "Failed to load model", e)
            stopSelf()
            return
        }.getOrThrow()

        llmEngine = loaded

        // 2. Update notification
        val notification = buildNotification("Local AI ready — 127.0.0.1:8088")
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        // 3. Start the Ktor server
        val server = LocalLlmServer(loaded)
        server.start(scope)
        llmServer = server

        isRunning = true
        Log.i(TAG, "Local LLM ready on ${LocalLlmServer.HOST}:${LocalLlmServer.PORT}")
    }

    private suspend fun shutdown() {
        llmServer?.stop()
        llmServer = null
        llmEngine?.close()
        llmEngine = null

        isRunning = false
        Log.i(TAG, "Service shutting down")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification ──────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local AI Server",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Persistent notification while the on-device LLM server is running"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Horizon Local AI")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
