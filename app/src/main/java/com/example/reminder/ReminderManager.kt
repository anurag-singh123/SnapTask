package com.example.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.R
import com.example.data.model.Task
import com.example.receiver.ReminderReceiver

class ReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL_REMINDERS_ID = "channel_reminders"
        const val CHANNEL_TASKS_FOUND_ID = "channel_tasks_found"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val remindersChannel = NotificationChannel(
                    CHANNEL_REMINDERS_ID,
                    context.getString(R.string.channel_reminders_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_reminders_description)
                    enableVibration(true)
                }

                val tasksFoundChannel = NotificationChannel(
                    CHANNEL_TASKS_FOUND_ID,
                    context.getString(R.string.channel_tasks_found_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.channel_tasks_found_description)
                }

                notificationManager.createNotificationChannel(remindersChannel)
                notificationManager.createNotificationChannel(tasksFoundChannel)
            }
        }
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleReminder(task: Task) {
        val triggerTime = task.remindAt ?: return
        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("EXTRA_TASK_ID", task.id)
            putExtra("EXTRA_TASK_TITLE", task.title)
            putExtra("EXTRA_TASK_NOTES", task.notes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w("ReminderManager", "Exact alarm permission denied, fallback to set()", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelReminder(taskId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
