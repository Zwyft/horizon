package com.zwyft.horizon.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.zwyft.horizon.MainActivity
import com.zwyft.horizon.R
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.dao.BatchProgressDao
import com.zwyft.horizon.data.dao.SettingDao
import com.zwyft.horizon.data.entity.BatchProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Long-running WorkManager worker that generates one journal entry per day
 * for a calendar range.
 *
 * Runs as a foreground service so it survives process death, app switching,
 * and screen lock. Progress + cancel signal are persisted to Room so the
 * UI can reattach cleanly.
 */
class BatchJournalWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_BATCH_ID = "batch_id"
        const val KEY_START    = "start_date"
        const val KEY_END      = "end_date"

        const val CHANNEL_ID   = "horizon_batch_journal"
        const val NOTIF_ID_BASE = 4100

        /**
         * Single fixed unique-work name. Using CANCEL_AND_REENQUEUE means a
         * fresh "Generate" tap cancels the prior in-flight work and starts
         * a clean one — the UI can never get stuck on a stale banner.
         */
        const val UNIQUE_NAME = "batch_journal_active"

        fun enqueue(context: Context, batchId: String, start: Date, end: Date): UUID {
            ensureChannel(context)
            val data = Data.Builder()
                .putString(KEY_BATCH_ID, batchId)
                .putLong(KEY_START, start.time)
                .putLong(KEY_END, end.time)
                .build()

            val request = OneTimeWorkRequestBuilder<BatchJournalWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, java.time.Duration.ofSeconds(10))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            return request.id
        }

        /**
         * Cancel an in-flight batch from outside the worker (UI banner or
         * notification action). Persists the cancel flag so the worker sees
         * it on its next iteration, and also cancels the WorkManager work
         * so isStopped flips to true.
         */
        fun cancelBatch(context: Context, batchId: String) {
            runBlocking(Dispatchers.IO) {
                HorizonDatabase.getInstance(context).batchProgressDao().requestCancel(batchId)
            }
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /**
         * Resolve the AI provider based on model and saved provider name.
         */
        fun resolveProvider(
            model: String,
            savedProviderName: String?,
            nousKey: String?,
            openrouterKey: String?
        ): AiProvider {
            return when {
                model.startsWith("openai/") || model.startsWith("google/") ||
                    model.startsWith("anthropic/") || model.startsWith("meta-llama/") ->
                    AiProvider.OPENROUTER
                savedProviderName?.uppercase() == "NOUS" && nousKey != null -> AiProvider.NOUS
                savedProviderName?.uppercase() == "LOCAL" -> AiProvider.LOCAL
                openrouterKey != null -> AiProvider.OPENROUTER
                nousKey != null -> AiProvider.NOUS
                else -> AiProvider.OPENROUTER
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(NotificationManager::class.java)
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Background journal generation",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows progress while generating journal entries in the background"
                        setShowBadge(false)
                    }
                    mgr.createNotificationChannel(channel)
                }
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return@withContext Result.failure()
        val startMillis = inputData.getLong(KEY_START, 0L)
        val endMillis   = inputData.getLong(KEY_END, 0L)
        if (startMillis == 0L || endMillis == 0L) return@withContext Result.failure()

        // Top-level safety net: any throwable is logged + persisted as a
        // failed batch instead of crashing the process.
        try {
            doWorkInternal(batchId, startMillis, endMillis)
        } catch (e: Throwable) {
            val errMsg = "FATAL: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            android.util.Log.e("BatchJournalWorker", errMsg, e)
            try {
                val dao = HorizonDatabase.getInstance(applicationContext).batchProgressDao()
                val existing = dao.get(batchId)
                val savedTotal = existing?.total ?: 0
                val savedCompleted = existing?.completed ?: 0
                val savedFailed = existing?.failed ?: 0
                dao.upsert(
                    BatchProgressEntity(
                        batchId = batchId,
                        total = savedTotal, completed = savedCompleted,
                        failed = savedFailed,
                        currentDate = "err:$errMsg",
                        status = "failed", startedAt = existing?.startedAt ?: System.currentTimeMillis(),
                        finishedAt = System.currentTimeMillis(),
                        startMillis = startMillis, endMillis = endMillis
                    )
                )
            } catch (_: Throwable) { }
            Result.failure()
        }
    }

    private suspend fun doWorkInternal(batchId: String, startMillis: Long, endMillis: Long): Result {
        val start = Date(startMillis)
        val end   = Date(endMillis)
        val db = HorizonDatabase.getInstance(applicationContext)
        val batchDao: BatchProgressDao = db.batchProgressDao()
        val settingDao: SettingDao = db.settingDao()

        // 1. Resolve model first, then provider (model dictates the route, not the ai_provider setting)
        val prefs = applicationContext.getSharedPreferences("horizon_settings", Context.MODE_PRIVATE)
        val model = settingDao.getValue("nous_model")
            ?: prefs.getString("nous_model", null)
            ?: ModelRegistry.OPENROUTER_DEFAULT_MODEL
        val savedProviderName = settingDao.getValue("ai_provider")
            ?: prefs.getString("ai_provider", null)
        val nousKey = settingDao.getValue("nous_api_key")
            ?: prefs.getString("nous_api_key", null)
        val openrouterKey = settingDao.getValue("openrouter_api_key")
            ?: prefs.getString("openrouter_api_key", null)
        val provider = resolveProvider(model, savedProviderName, nousKey, openrouterKey)
        val apiKey = when (provider) {
            AiProvider.NOUS -> nousKey
            AiProvider.OPENROUTER -> openrouterKey
            // Local inference doesn't need a remote key. We pass a
            // placeholder so the rest of the resolve code path
            // (which assumes non-null) doesn't NPE. The local server
            // ignores the Authorization header entirely.
            AiProvider.LOCAL -> "local-no-key"
        }
        if (apiKey.isNullOrBlank()) {
            android.util.Log.e("BatchJournalWorker", "API key missing for provider $provider (model=$model) batch $batchId")
            batchDao.upsert(
                BatchProgressEntity(
                    batchId = batchId,
                    total = 0, completed = 0, failed = 0,
                    currentDate = "err:${provider.displayName} API key not set — check Settings",
                    status = "failed", startedAt = System.currentTimeMillis(),
                    finishedAt = System.currentTimeMillis(),
                    startMillis = startMillis, endMillis = endMillis
                )
            )
            return Result.failure()
        }

        // 2. Compute the calendar span of the requested range
        val msgDao = db.messageDao()
        val dayFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dayCal = Calendar.getInstance().apply {
            time = start
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            time = end
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        val totalCalendarDays = ((endCal.timeInMillis - dayCal.timeInMillis) / (1000L * 60 * 60 * 24)).toInt() + 1

        // Chunking strategy: ALWAYS one entry per day.
        // The diary-style entry the user wants is fundamentally a daily format
        // ("Today is Tuesday, MMM d. Here are today's messages...") and the
        // user explicitly wants every calendar day to get its own entry — even
        // when "All messages" is selected across many months.
        //
        // Context budget: a 1M-context free model (Gemini 2.0 Flash) easily
        // holds a full week of messages. A typical heavy day is ~3K tokens,
        // so a full week is ~15-20K tokens — well within 128K let alone 1M.
        // We send only the target day's messages to the model (not the whole
        // week) so each prompt stays small and fast.

        // 3. Build chunks, skipping any that have no monitored messages
        //    AND any that already have a journal entry (duplicate prevention).
        //    Overlap rule: an entry blocks a chunk only if the entry fully
        //    contains the chunk's full range (not just the start day). This
        //    means a 2-day entry (Jun 3-4) blocks daily chunks for both days,
        //    while a 7-day entry doesn't block individual days.
        //
        //    We also track days that had ZERO monitored messages captured —
        //    these are surfaced to the user so they can tell the difference
        //    between "we already wrote an entry for that day" vs "no messages
        //    were captured on that day, so we couldn't write an entry."
        val journalDao = db.journalEntryDao()
        val chunks = mutableListOf<Pair<Date, Date>>()
        var skippedExisting = 0
        var daysWithNoMsgs = 0
        val noMsgsDates = mutableListOf<String>()  // up to 5 for the UI hint
        val probe = dayCal.clone() as Calendar
        while (!probe.before(dayCal) && !probe.after(endCal)) {
            val chunkStart = probe.time
            // For daily chunks: end of the same day (23:59:59.999).
            val chunkEnd = (probe.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.time
            // Skip: no monitored messages captured on this day
            if (msgDao.getMonitoredInRange(chunkStart, chunkEnd).isEmpty()) {
                daysWithNoMsgs++
                if (noMsgsDates.size < 5) noMsgsDates.add(dayFmt.format(chunkStart))
                probe.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }
            // Skip: an existing entry already covers this day (dateStart ≤ day
            // start AND dateEnd ≥ day end). A narrower daily entry (or any
            // entry that fully contains the day) blocks a new entry.
            val blockedByOverlap = journalDao.getOverlapping(chunkStart, chunkEnd).any { entry ->
                val entryStartCal = Calendar.getInstance().apply { time = entry.dateStart }
                val entryEndCal = Calendar.getInstance().apply { time = entry.dateEnd }
                !entryStartCal.after(chunkStart) && !entryEndCal.before(chunkEnd)
            }
            if (blockedByOverlap) {
                skippedExisting++
                probe.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }
            chunks.add(chunkStart to chunkEnd)
            probe.add(Calendar.DAY_OF_MONTH, 1)
        }

        val total = chunks.size
        if (total == 0) {
            // Build a useful reason that tells the user what to do.
            // If there ARE monitored messages but they're outside the
            // selected range, include the actual range in the error so the
            // user can adjust the dialog.
            val actualRange = msgDao.getMonitoredDateRange()
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val rangeHint: String? = when {
                actualRange?.minDate != null && actualRange.maxDate != null ->
                    "${sdf.format(actualRange.minDate)} → ${sdf.format(actualRange.maxDate)}"
                actualRange?.minDate != null ->
                    "from ${sdf.format(actualRange.minDate)}"
                else -> null
            }
            // Format: "all_already_generated|skipped|daysNoMsgs|totalDays|rangeHint|noMsgsDateList"
            //   skipped        — days that had messages AND an existing entry (skipped)
            //   daysNoMsgs     — days in the range that had 0 messages captured
            //   totalDays      — total calendar days in the selected range
            //   rangeHint      — actual range of monitored messages, if any (e.g. "Jun 3 → Jun 11")
            //   noMsgsDateList — ";" joined list of up to 5 day labels with no messages
            //                    (e.g. "Jun 5, 2026;Jun 6, 2026;Jun 7, 2026") for the UI hint.
            //                    Uses ";" instead of "|" so it doesn't conflict with the
            //                    outer "|" field separator even if a future date format
            //                    ever contains a "|".
            //
            // The "all already generated" message therefore tells the user
            // exactly which days already had entries AND which days had no
            // messages captured, so they can tell the difference between
            // "we already wrote it" and "we couldn't write it because the
            // notification listener missed those days."
            val noMsgsList = noMsgsDates.joinToString(";")
            val reason = when {
                skippedExisting > 0 ->
                    "all_already_generated|$skippedExisting|$daysWithNoMsgs|$totalCalendarDays|${rangeHint ?: "?"}|$noMsgsList"
                rangeHint == null -> "no_monitored_messages"
                else -> "no_monitored_in_range:$rangeHint"
            }
            android.util.Log.w("BatchJournalWorker", "No chunks to process: $reason (batch $batchId, skippedExisting=$skippedExisting, actualRange=$rangeHint)")
            batchDao.upsert(
                BatchProgressEntity(
                    batchId = batchId,
                    total = 0, completed = 0, failed = 0,
                    currentDate = reason, // signal to UI why there are 0 chunks
                    status = "completed", startedAt = System.currentTimeMillis(),
                    finishedAt = System.currentTimeMillis(),
                    startMillis = startMillis, endMillis = endMillis
                )
            )
            return Result.success()
        }

        // 4. Persist initial state + show initial foreground notification
        val firstLabel = dayFmt.format(chunks.first().first)
        batchDao.upsert(
            BatchProgressEntity(
                batchId = batchId,
                total = total, completed = 0, failed = 0,
                currentDate = firstLabel,
                status = "running",
                startedAt = System.currentTimeMillis(),
                startMillis = startMillis, endMillis = endMillis
            )
        )
        setForegroundSafe(buildNotification(0, total, firstLabel, batchId), "initial")
        android.util.Log.i("BatchJournalWorker", "Starting batch $batchId: $total daily chunks")

        val repo = JournalRepository(db, apiKey, provider, model)

        // 5. Loop per chunk
        var completed = 0
        var failed = 0
        // Collect per-chunk error details so the UI can show them
        val failedChunks = mutableListOf<String>()
        for ((chunkStart, chunkEnd) in chunks) {
            // Honor cancel from UI (check both DB flag and WorkManager isStopped)
            val state = batchDao.get(batchId)
            if (state?.cancelled == true || isStopped) {
                if (state != null) {
                    batchDao.upsert(
                        state.copy(
                            status = "cancelled",
                            finishedAt = System.currentTimeMillis(),
                            cancelled = true
                        )
                    )
                }
                return Result.success()
            }

            val label = dayFmt.format(chunkStart)
            batchDao.updateProgress(batchId, completed, label, failed)
            setForegroundSafe(buildNotification(completed, total, label, batchId), "loop-$completed")

            try {
                android.util.Log.i("BatchJournalWorker", "Day $label: generating (model=$model)")
                val entry = repo.generateJournalEntry(chunkStart, chunkEnd)
                if (entry != null) {
                    completed++
                    android.util.Log.i("BatchJournalWorker", "Day $label: ok (id=${entry.id})")
                } else {
                    failed++
                    val reason = "no monitored messages or empty API response"
                    failedChunks.add("$label: $reason")
                    android.util.Log.w("BatchJournalWorker", "Day $label: $reason")
                }
            } catch (e: Exception) {
                val errMsg = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
                failedChunks.add("$label: $errMsg")
                android.util.Log.e("BatchJournalWorker", "Day $label: $errMsg", e)
                failed++
            }

            // Check cancel AFTER the API call — the user may have tapped
            // Cancel while this chunk was in flight (especially the last chunk).
            val postState = batchDao.get(batchId)
            if (postState?.cancelled == true || isStopped) {
                if (postState != null) {
                    batchDao.upsert(
                        postState.copy(
                            status = "cancelled",
                            finishedAt = System.currentTimeMillis(),
                            cancelled = true
                        )
                    )
                }
                return Result.success()
            }

            batchDao.updateProgress(batchId, completed, label, failed)
            setForegroundSafe(buildNotification(completed, total, label, batchId), "loop-$completed")
        }

        // 6. Done — forward error details to UI via currentDate
        val finalStatus = if (failed == total && total > 0) "failed" else "completed"
        val errorDetail = when {
            failedChunks.isNotEmpty() -> "err:" + failedChunks.take(3).joinToString(" | ")
            completed > 0 -> "Done"
            else -> null
        }
        android.util.Log.i("BatchJournalWorker", "Batch $batchId done: $completed ok, $failed failed, status=$finalStatus errors=${failedChunks.take(3)}")
        batchDao.upsert(
            BatchProgressEntity(
                batchId = batchId,
                total = total, completed = completed, failed = failed,
                currentDate = errorDetail,
                status = finalStatus,
                startedAt = System.currentTimeMillis(),
                finishedAt = System.currentTimeMillis(),
                startMillis = startMillis, endMillis = endMillis
            )
        )
        setForegroundSafe(buildNotification(completed, total, "Done", batchId, done = true), "done")
        return Result.success()
    }

    private suspend fun setForegroundSafe(info: ForegroundInfo, tag: String) {
        try {
            setForeground(info)
        } catch (e: Throwable) {
            android.util.Log.w("BatchJournalWorker", "setForeground failed at $tag (continuing): ${e.message}")
        }
    }

    private fun buildNotification(
        progress: Int,
        total: Int,
        dateLabel: String,
        batchId: String,
        done: Boolean = false
    ): ForegroundInfo {
        // Use a broadcast PendingIntent so we can both set the DB cancel flag
        // AND cancel the WorkManager work in one tap. createCancelPendingIntent
        // only cancels the work, which doesn't stop the in-flight coroutine.
        val cancelIntent = PendingIntent.getBroadcast(
            applicationContext,
            batchId.hashCode().rem(10000),
            Intent(applicationContext, BatchCancelReceiver::class.java).apply {
                putExtra("batchId", batchId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            applicationContext, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (done) "Journal batch complete" else "Generating journal entries"
        val text = if (done) "$progress of $total entries created"
                   else "Day ${progress + 1} of $total — $dateLabel"

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(!done)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, progress, total == 0)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (!done) {
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                "Cancel",
                cancelIntent
            )
        }

        // On Android 14+ (SDK 34), the type MUST be passed in the ForegroundInfo
        // constructor — manifest-only is not enough. Otherwise the OS throws
        // InvalidForegroundServiceTypeException and the process dies.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(
            NOTIF_ID_BASE + batchId.hashCode().rem(1000),
            builder.build(),
            type
        )
    }
}

/**
 * BroadcastReceiver that handles the notification "Cancel" action.
 *
 * A single tap sets the DB cancel flag (so the worker's next loop iteration
 * sees it) AND cancels the WorkManager work (so isStopped flips to true).
 * This guarantees the worker stops — not just the work, but the in-flight
 * coroutine as well.
 */
class BatchCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val batchId = intent.getStringExtra("batchId") ?: return
        // Persist cancel flag (on IO dispatcher to avoid blocking main thread)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            HorizonDatabase.getInstance(context).batchProgressDao().requestCancel(batchId)
        }
        // Cancel the WorkManager work — this makes the worker's isStopped = true
        WorkManager.getInstance(context).cancelUniqueWork(BatchJournalWorker.UNIQUE_NAME)
    }
}
