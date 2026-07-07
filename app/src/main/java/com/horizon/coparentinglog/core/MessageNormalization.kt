package com.horizon.coparentinglog.core

import android.telephony.PhoneNumberUtils

object MessageNormalization {
    fun normalize(raw: String?, defaultRegionIso: String = "US"): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val normalizedFromPlatform = PhoneNumberUtils.normalizeNumber(trimmed)
        val digits = normalizedFromPlatform.filter(Char::isDigit)

        return when {
            trimmed.startsWith("+") && digits.length >= 10 -> "+$digits"
            digits.length == 11 && digits.startsWith("1") && defaultRegionIso.equals("US", ignoreCase = true) ->
                "+1${digits.drop(1)}"
            digits.length == 10 && defaultRegionIso.equals("US", ignoreCase = true) ->
                "+1$digits"
            normalizedFromPlatform.isNotBlank() -> normalizedFromPlatform
            else -> digits.ifBlank { trimmed }
        }
    }

    fun areSameNumber(lhs: String?, rhs: String?, defaultRegionIso: String = "US"): Boolean {
        if (lhs.isNullOrBlank() || rhs.isNullOrBlank()) return false
        return try {
            PhoneNumberUtils.areSamePhoneNumber(lhs, rhs, defaultRegionIso)
        } catch (_: Throwable) {
            normalize(lhs, defaultRegionIso) == normalize(rhs, defaultRegionIso)
        }
    }

    fun canonicalThreadKey(address: String?, threadId: Long, defaultRegionIso: String = "US"): String {
        val normalized = normalize(address, defaultRegionIso)
        return normalized ?: "thread:$threadId"
    }
}

