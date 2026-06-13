package com.zwyft.horizon.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Represents an AI-generated journal/diary entry.
 *
 * @param id Auto-generated primary key
 * @param title Short title for the entry
 * @param body Full journal text (AI-generated, "reads between the lines")
 * @param summary Short summary (1-2 sentences)
 * @param dateStart Start of the time window this entry covers
 * @param dateEnd End of the time window this entry covers
 * @param generatedAt When this entry was generated
 * @param modelUsed Which AI model generated it (e.g. "nousresearch/hermes-3")
 * @param promptUsed The prompt sent to the AI (for reproducibility)
 * @param relatedMessageIds Comma-separated list of message IDs that informed this entry
 * @param tags Comma-separated tags (e.g. "pickup,conflict,health")
 * @param sentimentOverall Overall sentiment: -1.0 (negative) to 1.0 (positive)
 * @param bookmarked true if user bookmarked this entry
 */
@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["generatedAt"]),
        Index(value = ["dateStart"]),
        Index(value = ["dateEnd"]),
        Index(value = ["bookmarked"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val body: String,
    val summary: String? = null,
    val dateStart: Date,
    val dateEnd: Date,
    val generatedAt: Date = Date(),
    val modelUsed: String? = null,
    val promptUsed: String? = null,
    val relatedMessageIds: String? = null,  // comma-separated message IDs
    val tags: String? = null,               // comma-separated tags
    val sentimentOverall: Float? = null,    // -1.0 to 1.0
    val bookmarked: Boolean = false,

    /** User's own annotation notes (null means no notes yet). */
    val userNotes: String? = null,

    /** Whether the user has added/edited their own notes. */
    val userEdited: Boolean = false
)
