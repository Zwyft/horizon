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
        val OPEN_ROUTER_API_KEY = stringPreferencesKey("open_router_api_key")
        val AI_PROVIDER = stringPreferencesKey("ai_provider") // "claude" | "openrouter"
        val SELECTED_OPEN_ROUTER_MODEL = stringPreferencesKey("selected_open_router_model")
        val LAST_SYNC_TIME_MILLIS = longPreferencesKey("last_sync_time_millis")
    }

    val coParentName: Flow<String> = context.dataStore.data.map { it[COPARENT_NAME] ?: "" }
    val coParentPhone: Flow<String> = context.dataStore.data.map { it[COPARENT_PHONE] ?: "" }
    val claudeApiKey: Flow<String> = context.dataStore.data.map { it[CLAUDE_API_KEY] ?: "" }
    val showSmsInTimeline: Flow<Boolean> = context.dataStore.data.map { it[SHOW_SMS_IN_TIMELINE] ?: true }
    val openRouterApiKey: Flow<String> = context.dataStore.data.map { it[OPEN_ROUTER_API_KEY] ?: "" }
    val aiProvider: Flow<String> = context.dataStore.data.map { it[AI_PROVIDER] ?: "claude" }
    val selectedOpenRouterModel: Flow<String> = context.dataStore.data.map {
        it[SELECTED_OPEN_ROUTER_MODEL] ?: "meta-llama/llama-3.3-70b-instruct:free"
    }
    val lastSyncTimeMillis: Flow<Long> = context.dataStore.data.map { it[LAST_SYNC_TIME_MILLIS] ?: 0L }

    suspend fun setCoParentName(v: String) = context.dataStore.edit { it[COPARENT_NAME] = v }
    suspend fun setCoParentPhone(v: String) = context.dataStore.edit { it[COPARENT_PHONE] = v }
    suspend fun setClaudeApiKey(v: String) = context.dataStore.edit { it[CLAUDE_API_KEY] = v }
    suspend fun setShowSmsInTimeline(v: Boolean) = context.dataStore.edit { it[SHOW_SMS_IN_TIMELINE] = v }
    suspend fun setOpenRouterApiKey(v: String) = context.dataStore.edit { it[OPEN_ROUTER_API_KEY] = v }
    suspend fun setAiProvider(v: String) = context.dataStore.edit { it[AI_PROVIDER] = v }
    suspend fun setSelectedOpenRouterModel(v: String) = context.dataStore.edit { it[SELECTED_OPEN_ROUTER_MODEL] = v }
    suspend fun setLastSyncTimeMillis(v: Long) = context.dataStore.edit { it[LAST_SYNC_TIME_MILLIS] = v }
}
