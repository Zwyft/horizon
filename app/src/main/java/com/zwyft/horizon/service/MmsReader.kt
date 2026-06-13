package com.zwyft.horizon.service

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.zwyft.horizon.data.entity.MessageAttachmentEntity
import com.zwyft.horizon.data.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * Reads MMS messages from the system [Telephony] provider.
 *
 * The MMS API is more involved than SMS:
 *  1. `content://mms`              \u2014 metadata for each MMS
 *  2. `content://mms/{id}/addr`    \u2014 the from/to addresses (MMS stores
 *                                   sender/recipient in a separate table
 *                                   because an MMS can have multiple
 *                                   recipients; we take the first)
 *  3. `content://mms/{id}/part`    \u2014 the attachments (text, image,
 *                                   audio, video, SMIL). Each part row
 *                                   has a `ct` (content type) and either
 *                                   a `text` column (text/plain parts) or
 *                                   a binary payload readable via
 *                                   `ContentResolver.openInputStream()`.
 *
 * Modern Android (14/15/16) still exposes all three URIs through the
 * Telephony provider; only `READ_SMS` permission is required. We do NOT
 * need `READ_MEDIA_IMAGES` or any storage permission because we read
 * directly through the resolver, not via the filesystem.
 *
 * All binaries are copied to `filesDir/attachments/{messageId}/{partId}.{ext}`
 * so the URI grant can't expire before the user reopens the message.
 * Files in `filesDir` are deleted when the user clears app data, which
 * is the expected lifecycle for an opt-in diary.
 */
class MmsReader(private val context: Context) {

    companion object {
        private const val TAG = "MmsReader"
        // Soft cap on total attachments per message to keep one
        // malicious MMS from filling the device's storage.
        private const val MAX_PARTS_PER_MESSAGE = 20
        // Skip any single attachment larger than this. Anything over
        // 25MB is unusable in the in-app image viewer anyway; if the
        // user wants the original, they can grab it from the source
        // MMS app. Keeps the app's filesDir from getting bloated.
        private const val MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024
    }

    /** True if the app currently holds READ_SMS permission. */
    fun hasPermission(): Boolean =
        MmsPermissionHelper.hasReadSms(context)

    /**
     * Read all MMS messages (inbox + sent) from the system provider.
     *
     * @param sinceMillis If non-null, only return messages newer than this timestamp.
     * @param batchTag    Tag written to MessageEntity.importedFrom for resume support.
     * @param limit       Maximum number of messages to return (per box).
     * @return List of (MessageEntity, List<MessageAttachmentEntity>) pairs.
     *         The MessageEntity has its body populated from the text parts and
     *         its `protocol=1` flag set. The attachment list contains all binary
     *         parts (image/audio/video) with their local file paths populated.
     */
    suspend fun readAll(
        sinceMillis: Long? = null,
        batchTag: String = "mms_sync",
        limit: Int = 1000
    ): List<MmsReadResult> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val results = mutableListOf<MmsReadResult>()

        results += readBox(
            uri = Telephony.Mms.Inbox.CONTENT_URI,
            type = 1,
            sinceMillis = sinceMillis,
            batchTag = batchTag,
            limit = limit
        )
        val remaining = (limit - results.size).coerceAtLeast(0)
        if (remaining > 0) {
            results += readBox(
                uri = Telephony.Mms.Sent.CONTENT_URI,
                type = 2,
                sinceMillis = sinceMillis,
                batchTag = batchTag,
                limit = remaining
            )
        }

        return@withContext results
    }

    private fun readBox(
        uri: Uri,
        type: Int,
        sinceMillis: Long?,
        batchTag: String,
        limit: Int
    ): List<MmsReadResult> {
        if (limit <= 0) return emptyList()

        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX, // 1=inbox, 2=sent
            Telephony.Mms.READ,
            Telephony.Mms.SUBJECT,
            Telephony.Mms.THREAD_ID
        )
        val selection = if (sinceMillis != null) "${Telephony.Mms.DATE} > ?" else null
        val selectionArgs = if (sinceMillis != null) arrayOf(sinceMillis.toString()) else null
        val sortOrder = "${Telephony.Mms.DATE} DESC LIMIT $limit"

        val out = mutableListOf<MmsReadResult>()
        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { c ->
            val idIdx = c.getColumnIndex(Telephony.Mms._ID)
            val dateIdx = c.getColumnIndex(Telephony.Mms.DATE)
            val readIdx = c.getColumnIndexOrNull(Telephony.Mms.READ)
            val subjIdx = c.getColumnIndexOrNull(Telephony.Mms.SUBJECT)
            val threadIdx = c.getColumnIndexOrNull(Telephony.Mms.THREAD_ID)

            while (c.moveToNext()) {
                val msgId = if (idIdx >= 0) c.getLong(idIdx) else continue
                val dateSeconds = if (dateIdx >= 0) c.getLong(dateIdx) else continue
                val dateMillis = dateSeconds * 1000L
                val read = if (readIdx != null && readIdx >= 0) c.getInt(readIdx) else 0
                val subject = if (subjIdx != null && subjIdx >= 0) c.getString(subjIdx) else null
                val threadId = if (threadIdx != null && threadIdx >= 0) c.getLong(threadIdx) else 0L

                val address = readAddress(msgId)
                val parts = readParts(msgId, limit = MAX_PARTS_PER_MESSAGE)
                val (body, attachments) = splitParts(parts)

                val entity = MessageEntity(
                    messageId = makeMessageId(msgId, type),
                    threadId = threadId,
                    address = address,
                    contactName = null,
                    body = body.ifBlank { subject ?: "" },
                    date = Date(dateMillis),
                    dateSent = null,
                    type = type,
                    read = read,
                    seen = read,
                    protocol = 1, // MMS
                    subject = subject,
                    mmsContentType = "mms",
                    mmsData = null,
                    attachedFilePath = attachments.firstOrNull()?.localPath,
                    rcs = false,
                    monitored = false,
                    importedFrom = batchTag,
                    journalProcessed = false
                )
                out += MmsReadResult(entity, attachments)
            }
        }
        return out
    }

    private fun readAddress(mmsId: Long): String {
        val addrUri = Uri.parse("${Telephony.Mms.CONTENT_URI}/$mmsId/addr")
        val projection = arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.CHARSET)
        return try {
            context.contentResolver.query(addrUri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val addrIdx = c.getColumnIndex(Telephony.Mms.Addr.ADDRESS)
                    if (addrIdx >= 0) c.getString(addrIdx) ?: "unknown" else "unknown"
                } else "unknown"
            } ?: "unknown"
        } catch (e: Exception) {
            Log.w(TAG, "readAddress failed for mmsId=$mmsId", e)
            "unknown"
        }
    }

    private fun readParts(mmsId: Long, limit: Int): List<MmsPart> {
        val partsUri = Uri.parse("${Telephony.Mms.CONTENT_URI}/$mmsId/part")
        val projection = arrayOf(
            Telephony.Mms.Part._ID,
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part.TEXT,
            Telephony.Mms.Part.NAME,
            Telephony.Mms.Part.SEQ
        )
        val out = mutableListOf<MmsPart>()
        try {
            context.contentResolver.query(partsUri, projection, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Mms.Part._ID)
                val ctIdx = c.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE)
                val textIdx = c.getColumnIndexOrNull(Telephony.Mms.Part.TEXT)
                val nameIdx = c.getColumnIndexOrNull(Telephony.Mms.Part.NAME)
                val seqIdx = c.getColumnIndexOrNull(Telephony.Mms.Part.SEQ)
                while (c.moveToNext() && out.size < limit) {
                    val partId = if (idIdx >= 0) c.getLong(idIdx) else continue
                    val ct = if (ctIdx >= 0) c.getString(ctIdx) ?: "" else ""
                    val text = if (textIdx != null && textIdx >= 0) c.getString(textIdx) else null
                    val name = if (nameIdx != null && nameIdx >= 0) c.getString(nameIdx) else null
                    val seq = if (seqIdx != null && seqIdx >= 0) c.getInt(seqIdx) else 0
                    out += MmsPart(partId = partId, ct = ct, text = text, name = name, seq = seq)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "readParts failed for mmsId=$mmsId", e)
        }
        return out
    }

    /**
     * Split the raw parts into a body string (text/plain parts joined)
     * and a list of attachments (binary parts, with their bytes copied
     * to filesDir). Text parts use the `text` column directly; binary
     * parts are read via `ContentResolver.openInputStream()`.
     */
    private fun splitParts(parts: List<MmsPart>): Pair<String, List<MessageAttachmentEntity>> {
        val bodyParts = mutableListOf<String>()
        val attachments = mutableListOf<MessageAttachmentEntity>()
        // Sort by sequence so the body reads in the order the sender wrote it.
        val sorted = parts.sortedBy { it.seq }
        for (p in sorted) {
            when {
                p.ct == "text/plain" -> {
                    p.text?.takeIf { it.isNotBlank() }?.let { bodyParts += it }
                }
                p.ct.startsWith("image/") || p.ct.startsWith("video/") ||
                p.ct.startsWith("audio/") || p.ct == "application/octet-stream" -> {
                    val saved = saveBinaryPart(p)
                    if (saved != null) attachments += saved
                }
                // text/html, application/smil, etc. \u2014 ignored for now.
            }
        }
        return bodyParts.joinToString("\n\n") to attachments
    }

    /**
     * Copy a binary part to filesDir/attachments/{mmsId}/{partId}.{ext}
     * Returns a [MessageAttachmentEntity] pointing at the saved file,
     * or null if the copy failed (file too big, IO error, etc).
     */
    private fun saveBinaryPart(part: MmsPart): MessageAttachmentEntity? {
        val ext = mimeToExtension(part.ct)
        val dir = File(context.filesDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, "mms_part_${System.currentTimeMillis()}_${part.partId}.$ext")

        val partUri = Uri.parse("${Telephony.Mms.CONTENT_URI}/part/${part.partId}")
        return try {
            context.contentResolver.openInputStream(partUri)?.use { input ->
                var total = 0L
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_ATTACHMENT_BYTES) {
                            output.close()
                            outFile.delete()
                            Log.w(TAG, "Attachment exceeds ${MAX_ATTACHMENT_BYTES}B, skipping")
                            return null
                        }
                        output.write(buf, 0, n)
                    }
                }
                MessageAttachmentEntity(
                    messageId = 0L, // caller fills in after the message row is created
                    mimeType = part.ct,
                    localPath = outFile.absolutePath,
                    originalName = part.name,
                    sizeBytes = total,
                    sortOrder = part.seq
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save MMS part ${part.partId} (${part.ct})", e)
            outFile.delete()
            null
        }
    }

    private fun mimeToExtension(mime: String): String = when {
        mime == "image/jpeg" || mime == "image/jpg" -> "jpg"
        mime == "image/png" -> "png"
        mime == "image/gif" -> "gif"
        mime == "image/webp" -> "webp"
        mime == "image/heic" -> "heic"
        mime == "video/mp4" -> "mp4"
        mime == "video/3gpp" -> "3gp"
        mime == "video/quicktime" -> "mov"
        mime == "audio/amr" -> "amr"
        mime == "audio/mp3" || mime == "audio/mpeg" -> "mp3"
        mime == "audio/aac" -> "aac"
        mime == "audio/m4a" || mime == "audio/mp4" -> "m4a"
        mime == "audio/ogg" -> "ogg"
        mime == "audio/wav" || mime == "audio/x-wav" -> "wav"
        else -> "bin"
    }

    private fun makeMessageId(mmsId: Long, type: Int): Long {
        // Combine mmsId + box (inbox/sent) to avoid the unique index
        // collision between a received and a sent MMS that happen to
        // share a row id (rare but possible on some providers).
        return mmsId * 10L + type
    }

    private fun android.database.Cursor.getColumnIndexOrNull(name: String): Int? {
        val idx = getColumnIndex(name)
        return if (idx < 0) null else idx
    }
}

/** Internal record for one part row. */
private data class MmsPart(
    val partId: Long,
    val ct: String,
    val text: String?,
    val name: String?,
    val seq: Int
)

/** A message + its binary attachments, returned by [MmsReader.readAll]. */
data class MmsReadResult(
    val message: MessageEntity,
    val attachments: List<MessageAttachmentEntity>
)

/**
 * Tiny indirection so both SmsMessageReader and MmsReader use the same
 * permission check without importing each other.
 */
internal object MmsPermissionHelper {
    fun hasReadSms(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
