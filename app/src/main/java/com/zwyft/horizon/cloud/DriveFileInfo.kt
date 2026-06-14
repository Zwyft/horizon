package com.zwyft.horizon.cloud

/**
 * Lightweight data class for UI-friendly Drive file info.
 */
data class DriveFileInfo(
    val id: String,
    val name: String,
    val modifiedTime: Long,
    val size: Long
)
