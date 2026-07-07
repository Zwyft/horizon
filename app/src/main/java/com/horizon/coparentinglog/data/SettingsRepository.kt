package com.horizon.coparentinglog.data

import android.content.Context
import android.content.SharedPreferences
import com.horizon.coparentinglog.core.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state

    fun read(): AppSettings = AppSettings(
        onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false),
        defaultSmsGranted = prefs.getBoolean(KEY_DEFAULT_SMS_GRANTED, false),
        messagesPermissionGranted = prefs.getBoolean(KEY_MESSAGES_PERMISSION_GRANTED, false),
        contactsPermissionGranted = prefs.getBoolean(KEY_CONTACTS_PERMISSION_GRANTED, false),
        openRouterApiKey = prefs.getString(KEY_OPENROUTER_API_KEY, "") ?: "",
        analysisModel = prefs.getString(KEY_ANALYSIS_MODEL, "openai/gpt-5.5") ?: "openai/gpt-5.5",
        journalModel = prefs.getString(KEY_JOURNAL_MODEL, "arcee-ai/virtuoso-large") ?: "arcee-ai/virtuoso-large",
        defaultRegionIso = prefs.getString(KEY_DEFAULT_REGION, "US") ?: "US",
        managedRcsEnabled = prefs.getBoolean(KEY_MANAGED_RCS_ENABLED, false),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, next.onboardingComplete)
            .putBoolean(KEY_DEFAULT_SMS_GRANTED, next.defaultSmsGranted)
            .putBoolean(KEY_MESSAGES_PERMISSION_GRANTED, next.messagesPermissionGranted)
            .putBoolean(KEY_CONTACTS_PERMISSION_GRANTED, next.contactsPermissionGranted)
            .putString(KEY_OPENROUTER_API_KEY, next.openRouterApiKey)
            .putString(KEY_ANALYSIS_MODEL, next.analysisModel)
            .putString(KEY_JOURNAL_MODEL, next.journalModel)
            .putString(KEY_DEFAULT_REGION, next.defaultRegionIso)
            .putBoolean(KEY_MANAGED_RCS_ENABLED, next.managedRcsEnabled)
            .apply()
        _state.value = next
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_DEFAULT_SMS_GRANTED = "default_sms_granted"
        private const val KEY_MESSAGES_PERMISSION_GRANTED = "messages_permission_granted"
        private const val KEY_CONTACTS_PERMISSION_GRANTED = "contacts_permission_granted"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_ANALYSIS_MODEL = "analysis_model"
        private const val KEY_JOURNAL_MODEL = "journal_model"
        private const val KEY_DEFAULT_REGION = "default_region"
        private const val KEY_MANAGED_RCS_ENABLED = "managed_rcs_enabled"
    }
}

