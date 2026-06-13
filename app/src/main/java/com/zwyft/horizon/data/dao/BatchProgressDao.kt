package com.zwyft.horizon.data.dao

import androidx.room.*
import com.zwyft.horizon.data.entity.BatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BatchProgressEntity)

    @Query("SELECT * FROM batch_progress WHERE batchId = :id LIMIT 1")
    suspend fun get(id: String): BatchProgressEntity?

    /** Emits whenever any batch_progress row changes — used to drive the UI. */
    @Query("SELECT * FROM batch_progress ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<BatchProgressEntity>>

    @Query("SELECT * FROM batch_progress WHERE status = 'running' ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<BatchProgressEntity?>

    @Query("UPDATE batch_progress SET completed = :completed, currentDate = :currentDate, failed = :failed WHERE batchId = :id")
    suspend fun updateProgress(id: String, completed: Int, currentDate: String?, failed: Int = 0)

    @Query("UPDATE batch_progress SET status = :status, finishedAt = :finishedAt WHERE batchId = :id")
    suspend fun setStatus(id: String, status: String, finishedAt: Long?)

    @Query("UPDATE batch_progress SET cancelled = 1 WHERE batchId = :id")
    suspend fun requestCancel(id: String)

    @Query("DELETE FROM batch_progress")
    suspend fun deleteAll()

    @Query("DELETE FROM batch_progress WHERE status != 'running' OR startedAt < :olderThanMs")
    suspend fun cleanup(olderThanMs: Long = 0L)

    /**
     * Mark any currently-running batch as cancelled. Used before enqueueing
     * a new batch so the UI clears the old "day X of Y" banner.
     */
    @Query("UPDATE batch_progress SET status = 'cancelled', finishedAt = :now, cancelled = 1 WHERE status = 'running'")
    suspend fun cancelAllRunning(now: Long)
}
