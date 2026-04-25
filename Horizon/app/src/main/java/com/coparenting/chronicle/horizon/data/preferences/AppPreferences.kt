package com.coparenting.chronicle.horizon.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "horizon_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val COPARENT_NAME = stringPreferencesKey("coparent_name")
        val COPARENT_PHONE = stringPreferencesKey("coparent_phone")
        val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        val SHOW_SMS_IN_TIMELINE = booleanPreferencesKey("show_sms_in_timeline")
    }

    val coParentName: Flow<String> = context.dataStore.data.map { it[COPARENT_NAME] ?: "" }
    val coParentPhone: Flow<String> = context.dataStore.data.map { it[COPARENT_PHONE] ?: "" }
    val claudeApiKey: Flow<String> = context.dataStore.data.map { it[CLAUDE_API_KEY] ?: "" }
    val showSmsInTimeline: Flow<Boolean> = context.dataStore.data.map { it[SHOW_SMS_IN_TIMELINE] ?: true }

    suspend fun setCoParentName(v: String) = context.dataStore.edit { it[COPARENT_NAME] = v }
    suspend fun setCoParentPhone(v: String) = context.dataStore.edit { it[COPARENT_PHONE] = v }
    suspend fun setClaudeApiKey(v: String) = context.dataStore.edit { it[CLAUDE_API_KEY] = v }
    suspend fun setShowSmsInTimeline(v: Boolean) = context.dataStore.edit { it[SHOW_SMS_IN_TIMELINE] = v }
}
