package com.zwyft.horizon.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks a single batch journal-generation run.
 *
 * Persisted to the DB so the UI can show progress even after the worker
 * (or app process) dies and is restarted. Also serves as the cancel
 * signal: setting [cancelled] = true tells the worker to stop early.
 */
@Entity(tableName = "batch_progress")
data class BatchProgressEntity(
    @PrimaryKey
    val batchId: String,

    val total: Int,
    val completed: Int,
    val failed: Int = 0,
    val currentDate: String?,

    /** running | completed | cancelled | failed */
    val status: String,

    val startedAt: Long,
    val finishedAt: Long? = null,

    val startMillis: Long,
    val endMillis: Long,

    /** Set to true to ask the worker to stop early on its next iteration. */
    val cancelled: Boolean = false
)
