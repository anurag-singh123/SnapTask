package com.example

import android.app.Application
import android.util.Log
import com.example.data.local.AppPreferences
import com.example.reminder.ReminderManager
import com.example.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SnapTaskApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            // Create notification channels for reminders and tasks found
            ReminderManager.createNotificationChannels(this)

            // Schedule screenshot monitoring if enabled
            val prefs = AppPreferences(this)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (prefs.isMonitoringEnabled.first()) {
                        WorkScheduler.scheduleScreenshotMonitoring(this@SnapTaskApplication)
                    }
                } catch (e: Exception) {
                    Log.e("SnapTaskApp", "Failed to schedule monitoring work", e)
                }
            }
        } catch (e: Exception) {
            Log.e("SnapTaskApp", "Error during Application initialization", e)
        }
    }
}
