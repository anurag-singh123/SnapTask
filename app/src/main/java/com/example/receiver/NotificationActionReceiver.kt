package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.model.ScreenshotStatus
import com.example.data.model.Task
import com.example.parser.TaskParser
import com.example.reminder.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "com.example.ACTION_MARK_DONE"
        const val ACTION_SNOOZE_10M = "com.example.ACTION_SNOOZE_10M"
        const val ACTION_SNOOZE_1H = "com.example.ACTION_SNOOZE_1H"
        const val ACTION_SNOOZE_TOMORROW = "com.example.ACTION_SNOOZE_TOMORROW"

        const val ACTION_SAVE_ALL_TASKS = "com.example.ACTION_SAVE_ALL_TASKS"
        const val ACTION_DISMISS_NOTIFICATION = "com.example.ACTION_DISMISS_NOTIFICATION"

        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_SCREENSHOT_ID = "EXTRA_SCREENSHOT_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val reminderManager = ReminderManager(context)

                when (action) {
                    ACTION_MARK_DONE -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                        if (taskId != -1L) {
                            val task = db.taskDao().getTaskById(taskId)
                            if (task != null) {
                                db.taskDao().update(task.copy(isDone = true))
                                reminderManager.cancelReminder(taskId)
                            }
                            notificationManager.cancel(taskId.toInt())
                        }
                    }

                    ACTION_SNOOZE_10M -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                        if (taskId != -1L) {
                            val task = db.taskDao().getTaskById(taskId)
                            if (task != null) {
                                val newRemindTime = System.currentTimeMillis() + 10 * 60 * 1000L
                                val updated = task.copy(remindAt = newRemindTime)
                                db.taskDao().update(updated)
                                reminderManager.scheduleReminder(updated)
                            }
                            notificationManager.cancel(taskId.toInt())
                        }
                    }

                    ACTION_SNOOZE_1H -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                        if (taskId != -1L) {
                            val task = db.taskDao().getTaskById(taskId)
                            if (task != null) {
                                val newRemindTime = System.currentTimeMillis() + 60 * 60 * 1000L
                                val updated = task.copy(remindAt = newRemindTime)
                                db.taskDao().update(updated)
                                reminderManager.scheduleReminder(updated)
                            }
                            notificationManager.cancel(taskId.toInt())
                        }
                    }

                    ACTION_SNOOZE_TOMORROW -> {
                        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                        if (taskId != -1L) {
                            val task = db.taskDao().getTaskById(taskId)
                            if (task != null) {
                                val tomorrow9am = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0))
                                val newRemindTime = tomorrow9am.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val updated = task.copy(remindAt = newRemindTime)
                                db.taskDao().update(updated)
                                reminderManager.scheduleReminder(updated)
                            }
                            notificationManager.cancel(taskId.toInt())
                        }
                    }

                    ACTION_SAVE_ALL_TASKS -> {
                        val screenshotId = intent.getLongExtra(EXTRA_SCREENSHOT_ID, -1L)
                        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                        if (screenshotId != -1L) {
                            val record = db.screenshotDao().getById(screenshotId)
                            if (record != null && !record.ocrText.isNullOrBlank()) {
                                val parser = TaskParser()
                                val candidates = parser.parseTasks(record.ocrText)
                                val tasksToInsert = candidates.map {
                                    Task(
                                        title = it.title,
                                        notes = it.notes,
                                        dueAt = it.dueAt,
                                        remindAt = it.remindAt,
                                        sourceScreenshotId = record.id
                                    )
                                }
                                val insertedIds = db.taskDao().insertAll(tasksToInsert)
                                db.screenshotDao().update(
                                    record.copy(
                                        status = ScreenshotStatus.REVIEWED,
                                        tasksCount = tasksToInsert.size
                                    )
                                )
                                // Schedule reminders for saved tasks
                                insertedIds.forEachIndexed { index, newId ->
                                    val task = tasksToInsert[index].copy(id = newId)
                                    if (task.remindAt != null && task.remindAt > System.currentTimeMillis()) {
                                        reminderManager.scheduleReminder(task)
                                    }
                                }
                            }
                        }
                        if (notifId != 0) {
                            notificationManager.cancel(notifId)
                        }
                    }

                    ACTION_DISMISS_NOTIFICATION -> {
                        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                        if (notifId != 0) {
                            notificationManager.cancel(notifId)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
