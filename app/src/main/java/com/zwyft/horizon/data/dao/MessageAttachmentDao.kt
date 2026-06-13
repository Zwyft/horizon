package com.zwyft.horizon.data.dao

import androidx.room.*
import com.zwyft.horizon.data.entity.MessageAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageAttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: MessageAttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<MessageAttachmentEntity>): List<Long>

    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY sortOrder ASC")
    suspend fun getForMessage(messageId: Long): List<MessageAttachmentEntity>

    @Query("""
        SELECT * FROM message_attachments
        WHERE messageId IN (:messageIds)
        ORDER BY messageId ASC, sortOrder ASC
    """)
    suspend fun getForMessages(messageIds: List<Long>): List<MessageAttachmentEntity>

    /**
     * Flow version for UI binding. Emits whenever the underlying
     * attachments change so the Messages screen re-renders
     * thumbnails live as new MMS rows are inserted.
     */
    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY sortOrder ASC")
    fun observeForMessage(messageId: Long): Flow<List<MessageAttachmentEntity>>

    @Query("SELECT * FROM message_attachments ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<MessageAttachmentEntity>

    @Query("SELECT COUNT(*) FROM message_attachments")
    suspend fun count(): Int

    /**
     * Delete attachment rows whose binary file no longer exists on
     * disk (e.g. user cleared app data, or an interrupted sync left
     * a half-written file). Returns the number of rows deleted.
     */
    @Query("DELETE FROM message_attachments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int
}
