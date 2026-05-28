package com.zwyft.horizon.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Represents a single SMS/MMS/RCS message imported or captured.
 *
 * @param id Auto-generated primary key
 * @param messageId The original message ID from the SMS provider or import source
 * @param threadId Conversation thread ID
 * @param address Phone number or contact identifier
 * @param contactName Resolved contact name (if in contacts)
 * @param body Message body text
 * @param date Timestamp of the message (millis)
 * @param dateSent Timestamp when message was sent (millis)
 * @param type 1=received, 2=sent, 3=draft, 4=outbox, 5=failed, 6=queued
 * @param read 0=unread, 1=read
 * @param seen 0=unseen, 1=seen
 * @param protocol 0=SMS, 1=MMS
 * @param subject MMS subject (null for SMS)
 * @param mmsContentType MMS content type (null for SMS)
 * @param mmsData MMS binary data or text (null for SMS)
 * @param attachedFilePath Path to downloaded MMS attachment (null for SMS)
 * @param rcs RCS message flag
 * @param monitored Whether this message is from a monitored contact
 * @param importedFrom Which import batch this came from (for resume support)
 * @param journalProcessed Whether this message has been processed for journal generation
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["threadId"]),
        Index(value = ["address"]),
        Index(value = ["date"]),
        Index(value = ["monitored"]),
        Index(value = ["journalProcessed"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val messageId: Long,                // original SMS/MMS _ID
    val threadId: Long,
    val address: String,                // phone number
    val contactName: String? = null,
    val body: String?,
    val date: Date,
    val dateSent: Date? = null,
    val type: Int,                      // 1=inbox, 2=sent, etc.
    val read: Int = 0,
    val seen: Int = 0,
    val protocol: Int = 0,             // 0=SMS, 1=MMS
    val subject: String? = null,
    val mmsContentType: String? = null,
    val mmsData: String? = null,
    val attachedFilePath: String? = null,
    val rcs: Boolean = false,
    val monitored: Boolean = false,
    val importedFrom: String? = null,  // import batch tag
    val journalProcessed: Boolean = false
)
