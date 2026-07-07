package com.horizon.coparentinglog.data

import android.content.Context
import com.horizon.coparentinglog.core.ArchiveSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ArchiveRepository(context: Context) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val file = File(context.filesDir, "archive.json")

    suspend fun read(): ArchiveSnapshot = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ArchiveSnapshot()
        runCatching { json.decodeFromString<ArchiveSnapshot>(file.readText()) }.getOrElse { ArchiveSnapshot() }
    }

    suspend fun write(snapshot: ArchiveSnapshot) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(snapshot))
    }
}

