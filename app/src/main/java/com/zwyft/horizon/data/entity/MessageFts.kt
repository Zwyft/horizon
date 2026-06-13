package com.zwyft.horizon.data.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table for full-text search on message body, contact name, and address.
 * Room auto-creates triggers to keep this in sync with the messages table.
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFts(
    val body: String?,
    val contactName: String?,
    val address: String
)
