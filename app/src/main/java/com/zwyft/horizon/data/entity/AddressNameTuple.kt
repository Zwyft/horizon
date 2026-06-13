package com.zwyft.horizon.data.entity

import androidx.room.ColumnInfo
import java.util.Date

/**
 * Tuple returned by [MessageDao.getMonitoredContacts].
 * Room maps columns by name — property names must match the SELECT aliases.
 */
data class AddressNameTuple(
    @ColumnInfo(name = "address")
    val address: String,
    @ColumnInfo(name = "contactName")
    val contactName: String?
)

/** Tuple returned by [com.zwyft.horizon.data.dao.MessageDao.getTopAddresses]. */
data class AddressCount(
    val address: String,
    val cnt: Long
)

/**
 * Tuple returned by [com.zwyft.horizon.data.dao.MessageDao.getMonitoredDateRange].
 * Provides the earliest and latest dates of monitored messages — used to
 * pre-populate the Generate dialog with the actual message range rather
 * than a hardcoded 7-day window.
 */
data class MonitoredDateRange(
    @ColumnInfo(name = "minDate")
    val minDate: Date?,
    @ColumnInfo(name = "maxDate")
    val maxDate: Date?
)
