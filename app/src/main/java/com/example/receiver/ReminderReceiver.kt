package com.example.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.reminder.ReminderManager

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("EXTRA_TASK_ID", -1L)
        val title = intent.getStringExtra("EXTRA_TASK_TITLE") ?: "Task Reminder"
        val notes = intent.getStringExtra("EXTRA_TASK_NOTES") ?: ""

        if (taskId == -1L) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Content intent (Open app to Task)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TASK_ID", taskId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark Done
        val markDoneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val markDonePending = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 1).toInt(),
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 10 min
        val snooze10Intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_10M
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val snooze10Pending = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 2).toInt(),
            snooze10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 1 hour
        val snooze1hIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_1H
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val snooze1hPending = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 3).toInt(),
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Tomorrow morning
        val snoozeTomorrowIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE_TOMORROW
            putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val snoozeTomorrowPending = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 4).toInt(),
            snoozeTomorrowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(notes.ifEmpty { "Due right now" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (notes.isNotEmpty()) "$title\n$notes" else title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Mark done", markDonePending)
            .addAction(0, "10 min", snooze10Pending)
            .addAction(0, "1 hour", snooze1hPending)
            .addAction(0, "Tomorrow", snoozeTomorrowPending)
            .build()

        notificationManager.notify(taskId.toInt(), notification)
    }
}
