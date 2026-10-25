package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "snaptask_preferences")

class AppPreferences(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        val KEY_MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val KEY_AUTO_SAVE_MODE = booleanPreferencesKey("auto_save_mode")
        val KEY_DEFAULT_REMINDER_HOUR = intPreferencesKey("default_reminder_hour")
        val KEY_DEFAULT_REMINDER_MINUTE = intPreferencesKey("default_reminder_minute")
        val KEY_SENSITIVE_FILTER_ENABLED = booleanPreferencesKey("sensitive_filter_enabled")
        val KEY_AUTO_DELETE_OCR_DAYS = intPreferencesKey("auto_delete_ocr_days") // 0 = off, 7, 30
        val KEY_BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_LAST_PROCESSED_TIMESTAMP = longPreferencesKey("last_processed_timestamp")
    }

    val isMonitoringEnabled: Flow<Boolean> = appContext.dataStore.data.map {
        it[KEY_MONITORING_ENABLED] ?: true
    }

    val isAutoSaveMode: Flow<Boolean> = appContext.dataStore.data.map {
        it[KEY_AUTO_SAVE_MODE] ?: false
    }

    val defaultReminderHour: Flow<Int> = appContext.dataStore.data.map {
        it[KEY_DEFAULT_REMINDER_HOUR] ?: 9
    }

    val defaultReminderMinute: Flow<Int> = appContext.dataStore.data.map {
        it[KEY_DEFAULT_REMINDER_MINUTE] ?: 0
    }

    val isSensitiveFilterEnabled: Flow<Boolean> = appContext.dataStore.data.map {
        it[KEY_SENSITIVE_FILTER_ENABLED] ?: true
    }

    val autoDeleteOcrDays: Flow<Int> = appContext.dataStore.data.map {
        it[KEY_AUTO_DELETE_OCR_DAYS] ?: 0 // 0 means off
    }

    val isBiometricLockEnabled: Flow<Boolean> = appContext.dataStore.data.map {
        it[KEY_BIOMETRIC_LOCK_ENABLED] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = appContext.dataStore.data.map {
        it[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val lastProcessedTimestamp: Flow<Long> = appContext.dataStore.data.map {
        it[KEY_LAST_PROCESSED_TIMESTAMP] ?: (System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_MONITORING_ENABLED] = enabled }
    }

    suspend fun setAutoSaveMode(autoSave: Boolean) {
        appContext.dataStore.edit { it[KEY_AUTO_SAVE_MODE] = autoSave }
    }

    suspend fun setDefaultReminderTime(hour: Int, minute: Int) {
        appContext.dataStore.edit {
            it[KEY_DEFAULT_REMINDER_HOUR] = hour
            it[KEY_DEFAULT_REMINDER_MINUTE] = minute
        }
    }

    suspend fun setSensitiveFilterEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_SENSITIVE_FILTER_ENABLED] = enabled }
    }

    suspend fun setAutoDeleteOcrDays(days: Int) {
        appContext.dataStore.edit { it[KEY_AUTO_DELETE_OCR_DAYS] = days }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        appContext.dataStore.edit { it[KEY_BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        appContext.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setLastProcessedTimestamp(timestamp: Long) {
        appContext.dataStore.edit { it[KEY_LAST_PROCESSED_TIMESTAMP] = timestamp }
    }
}
