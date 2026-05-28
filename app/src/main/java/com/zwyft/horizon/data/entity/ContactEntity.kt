package com.zwyft.horizon.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a monitored contact (mom, her parents, grandparents, babysitter, etc.)
 *
 * @param id Auto-generated primary key
 * @param name Contact display name
 * @param phoneNumber Primary phone number
 * @param normalizedPhoneNumber Normalized (digits only) for matching
 * @param relationship Relationship label (mom, dad, babysitter, etc.)
 * @param monitored true if messages from this contact should be imported/processed
 * @param colorArgb Color for UI tagging (optional)
 * @param notes Free-text notes about this contact
 */
@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index(value = ["normalizedPhoneNumber"]),
        Index(value = ["monitored"])
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val phoneNumber: String,
    val normalizedPhoneNumber: String,
    val relationship: String? = null,   // "mom", "babysitter", etc.
    val monitored: Boolean = true,
    val colorArgb: Int? = null,
    val notes: String? = null
)
