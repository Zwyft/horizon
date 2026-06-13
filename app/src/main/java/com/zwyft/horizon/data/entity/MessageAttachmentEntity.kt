package com.zwyft.horizon.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One binary attachment for a message (typically an MMS image/video/audio).
 *
 * Modeled as a separate table from [MessageEntity] because a single MMS
 * can carry multiple attachments (e.g. text + image + audio). The binary
 * data is stored in the app's private `filesDir/attachments/{msgId}/`
 * directory; this row holds just the path + metadata.
 *
 * Files in `filesDir` survive across process restarts and are not
 * visible to other apps (Scoped Storage / SELinux safe). They DO get
 * removed if the user clears the app's data, which is the expected
 * behavior for an opt-in diary app.
 *
 * The CASCADE foreign key means deleting a message also deletes its
 * attachment rows (and Room will additionally remove the binary files
 * via a `ContentObserver` set up in the repository — see
 * `MessageAttachmentRepository.cleanupOrphans`).
 */
@Entity(
    tableName = "message_attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MessageAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** FK to [MessageEntity.id]. */
    val messageId: Long,

    /**
     * MIME type of the binary, e.g. `image/jpeg`, `video/mp4`,
     * `audio/amr`, `text/plain`. Used by the UI to pick the right
     * renderer (Coil for images, MediaPlayer for audio, etc).
     */
    val mimeType: String,

    /**
     * Absolute path inside the app's private `filesDir/attachments/`.
     * The file is guaranteed to exist when this row exists, but may
     * be missing on disk if the user cleared the app data; the UI
     * must handle that gracefully (show a placeholder, not crash).
     */
    val localPath: String,

    /**
     * Best-effort original filename. May be null (some MMS providers
     * don't store one). Used as the default for "Save to gallery" /
     * "Share" actions in the attachment viewer.
     */
    val originalName: String? = null,

    /** Size of the binary in bytes. 0 if unknown. */
    val sizeBytes: Long = 0,

    /**
     * Order within a single message's attachments. For an MMS with
     * `[image, audio, text]`, the image gets sortOrder=0, audio=1,
     * text=2. The Messages UI renders attachments in this order.
     */
    val sortOrder: Int = 0
)
