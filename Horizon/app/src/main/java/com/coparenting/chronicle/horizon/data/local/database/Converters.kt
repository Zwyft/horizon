package com.coparenting.chronicle.horizon.data.local.database

import androidx.room.TypeConverter
import com.coparenting.chronicle.horizon.domain.model.EmotionalTone
import com.coparenting.chronicle.horizon.domain.model.MessageType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // LocalDateTime
    @TypeConverter fun fromDateTime(v: LocalDateTime?): String? = v?.format(fmt)
    @TypeConverter fun toDateTime(v: String?): LocalDateTime? = v?.let { LocalDateTime.parse(it, fmt) }

    // List<String> using || as delimiter (safe for normal text)
    @TypeConverter fun fromStringList(v: List<String>?): String? = v?.joinToString("||")
    @TypeConverter fun toStringList(v: String?): List<String> =
        if (v.isNullOrEmpty()) emptyList() else v.split("||").filter { it.isNotEmpty() }

    // Map<String, Int>
    @TypeConverter
    fun fromStringIntMap(v: Map<String, Int>?): String? =
        v?.entries?.joinToString(";;") { "${it.key}:${it.value}" }

    @TypeConverter
    fun toStringIntMap(v: String?): Map<String, Int> {
        if (v.isNullOrEmpty()) return emptyMap()
        return v.split(";;").mapNotNull { entry ->
            val idx = entry.lastIndexOf(':')
            if (idx < 0) null else entry.substring(0, idx) to (entry.substring(idx + 1).toIntOrNull() ?: 0)
        }.toMap()
    }

    // EmotionalTone enum
    @TypeConverter fun fromEmotionalTone(v: EmotionalTone?): String? = v?.name
    @TypeConverter fun toEmotionalTone(v: String?): EmotionalTone? =
        v?.let { runCatching { EmotionalTone.valueOf(it) }.getOrNull() }

    // MessageType enum
    @TypeConverter fun fromMessageType(v: MessageType?): String? = v?.name
    @TypeConverter fun toMessageType(v: String?): MessageType? =
        v?.let { runCatching { MessageType.valueOf(it) }.getOrNull() }
}
