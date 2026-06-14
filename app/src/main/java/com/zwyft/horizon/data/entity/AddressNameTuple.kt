package com.zwyft.horizon.data.entity

import java.util.Date

/**
 * Tuple returned by [MessageDao.getMonitoredContacts].
 * Room maps columns by name — property names must match the SELECT aliases.
 */
data class AddressNameTuple(
    val address: String,
    val contactName: String?
)

/** Tuple returned by [com.zwyft.horizon.data.dao.MessageDao.getTopAddresses]. */
data class AddressNameCount(
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
    val minDate: Date?,
    val maxDate: Date?
)
