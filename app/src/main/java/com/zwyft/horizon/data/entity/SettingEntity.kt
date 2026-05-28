package com.zwyft.horizon.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key-value settings store.
 * Using Room for settings so all data lives in one DB.
 * For encrypted values we store only a flag; the encrypted blob lives in EncryptedSharedPreferences.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,

    /** Plain-string value. For booleans use "true"/"false". */
    val value: String,

    /** Human-readable label shown in settings UI */
    val label: String? = null,

    /** Value type hint for UI: "string" | "boolean" | "int" | "enum" | "secret" */
    val type: String = "string",

    /** For type="enum": comma-separated options */
    val enumOptions: String? = null,

    /** If true, this setting is advanced and hidden behind "Advanced" */
    val advanced: Boolean = false
)
