package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.reminder.ReminderManager
import com.example.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val reminderManager = ReminderManager(context)
                    val now = System.currentTimeMillis()
                    val upcomingTasks = db.taskDao().getUpcomingReminders(now)
                    for (task in upcomingTasks) {
                        reminderManager.scheduleReminder(task)
                    }

                    // Re-enqueue WorkManager trigger work if monitoring is enabled
                    val prefs = AppPreferences(context)
                    if (prefs.isMonitoringEnabled.first()) {
                        WorkScheduler.scheduleScreenshotMonitoring(context)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
