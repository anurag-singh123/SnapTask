package com.example.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.model.ScreenshotRecord
import com.example.data.model.ScreenshotStatus
import com.example.data.model.Task
import com.example.ocr.OcrProcessor
import com.example.parser.TaskParser
import com.example.receiver.NotificationActionReceiver
import com.example.reminder.ReminderManager
import kotlinx.coroutines.flow.first
import java.util.Locale

class ScreenshotDetectionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ScreenshotWorker"
    }

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(context)
        val isMonitoring = prefs.isMonitoringEnabled.first()
        if (!isMonitoring) {
            return Result.success()
        }

        // Check read media permission
        if (!hasStoragePermission()) {
            Log.d(TAG, "Storage permission not granted; skipping automated scan")
            return Result.success()
        }

        try {
            val db = AppDatabase.getInstance(context)
            val ocrProcessor = OcrProcessor(context)
            val lastProcessed = prefs.lastProcessedTimestamp.first()
            val autoSaveMode = prefs.isAutoSaveMode.first()
            val sensitiveFilter = prefs.isSensitiveFilterEnabled.first()
            val autoDeleteDays = prefs.autoDeleteOcrDays.first()
            val defaultHour = prefs.defaultReminderHour.first()
            val defaultMinute = prefs.defaultReminderMinute.first()
            val taskParser = TaskParser(defaultHour, defaultMinute)
            val reminderManager = ReminderManager(context)

            // Auto-clean old OCR text if configured
            if (autoDeleteDays > 0) {
                val threshold = System.currentTimeMillis() - (autoDeleteDays.toLong() * 24 * 60 * 60 * 1000L)
                db.screenshotDao().clearOcrTextOlderThan(threshold)
            }

            // Query MediaStore for screenshot images added since last scan
            val newScreenshots = queryNewScreenshots(lastProcessed)
            var maxAddedTimestamp = lastProcessed

            for (item in newScreenshots) {
                if (item.dateAddedMillis > maxAddedTimestamp) {
                    maxAddedTimestamp = item.dateAddedMillis
                }

                // Deduplicate by mediaStoreId
                val existing = db.screenshotDao().getByMediaStoreId(item.id)
                if (existing != null) {
                    continue
                }

                // Create initial record
                var record = ScreenshotRecord(
                    mediaStoreId = item.id,
                    uri = item.uri.toString(),
                    status = ScreenshotStatus.NEW,
                    processedAt = System.currentTimeMillis()
                )
                val recordId = db.screenshotDao().insert(record)
                record = record.copy(id = recordId)

                // Run OCR
                val ocrResult = ocrProcessor.recognizeTextFromUri(item.uri)
                if (ocrResult.isFailure) {
                    Log.w(TAG, "OCR failed for screenshot: ${item.uri}", ocrResult.exceptionOrNull())
                    db.screenshotDao().update(record.copy(status = ScreenshotStatus.FAILED))
                    continue
                }

                val text = ocrResult.getOrNull() ?: ""

                // Check sensitive content
                if (sensitiveFilter && taskParser.isSensitive(text)) {
                    Log.d(TAG, "Sensitive content detected; skipping task extraction")
                    db.screenshotDao().update(
                        record.copy(
                            status = ScreenshotStatus.SENSITIVE,
                            ocrText = null
                        )
                    )
                    continue
                }

                // Parse candidate tasks
                val candidates = taskParser.parseTasks(text)
                if (candidates.isEmpty()) {
                    db.screenshotDao().update(
                        record.copy(
                            status = ScreenshotStatus.PROCESSED,
                            ocrText = text,
                            tasksCount = 0
                        )
                    )
                    continue
                }

                if (autoSaveMode) {
                    // Directly save all tasks
                    val tasksToInsert = candidates.map {
                        Task(
                            title = it.title,
                            notes = it.notes,
                            dueAt = it.dueAt,
                            remindAt = it.remindAt,
                            sourceScreenshotId = record.id
                        )
                    }
                    val ids = db.taskDao().insertAll(tasksToInsert)
                    db.screenshotDao().update(
                        record.copy(
                            status = ScreenshotStatus.REVIEWED,
                            ocrText = text,
                            tasksCount = tasksToInsert.size
                        )
                    )
                    // Schedule reminders
                    ids.forEachIndexed { idx, id ->
                        val savedTask = tasksToInsert[idx].copy(id = id)
                        if (savedTask.remindAt != null && savedTask.remindAt > System.currentTimeMillis()) {
                            reminderManager.scheduleReminder(savedTask)
                        }
                    }
                } else {
                    // Update record to PROCESSED so it appears in Inbox and notify user
                    db.screenshotDao().update(
                        record.copy(
                            status = ScreenshotStatus.PROCESSED,
                            ocrText = text,
                            tasksCount = candidates.size
                        )
                    )
                    postTasksFoundNotification(record.id, candidates.size)
                }
            }

            // Update scan timestamp
            if (maxAddedTimestamp > lastProcessed) {
                prefs.setLastProcessedTimestamp(maxAddedTimestamp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ScreenshotDetectionWorker", e)
        } finally {
            // Re-enqueue the one-shot content URI trigger so next screenshot triggers immediately
            if (prefs.isMonitoringEnabled.first()) {
                WorkScheduler.scheduleScreenshotMonitoring(context)
            }
        }

        return Result.success()
    }

    private data class ScreenshotMediaItem(
        val id: Long,
        val uri: Uri,
        val dateAddedMillis: Long
    )

    private fun queryNewScreenshots(sinceMillis: Long): List<ScreenshotMediaItem> {
        val items = mutableListOf<ScreenshotMediaItem>()
        val sinceSeconds = sinceMillis / 1000L

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                }
                val bucketColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAddedSec = cursor.getLong(dateAddedColumn)
                    val dateAddedMs = dateAddedSec * 1000L

                    // Check if new
                    if (dateAddedMs <= sinceMillis) {
                        continue
                    }

                    val name = cursor.getString(nameColumn) ?: ""
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                    val bucket = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: "" else ""

                    // Check if screenshot by path/bucket/name
                    val isScreenshot = path.contains("screenshot", ignoreCase = true) ||
                            bucket.contains("screenshot", ignoreCase = true) ||
                            name.lowercase(Locale.ROOT).startsWith("screenshot")

                    if (isScreenshot) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        items.add(ScreenshotMediaItem(id, contentUri, dateAddedMs))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
        }

        return items
    }

    private fun postTasksFoundNotification(screenshotId: Long, count: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = (screenshotId % 10000).toInt() + 1000

        // Review Action (opens app to Review screen)
        val reviewIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREENSHOT_ID", screenshotId)
        }
        val reviewPending = PendingIntent.getActivity(
            context,
            notifId,
            reviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Save All Action
        val saveAllIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SAVE_ALL_TASKS
            putExtra(NotificationActionReceiver.EXTRA_SCREENSHOT_ID, screenshotId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val saveAllPending = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            saveAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss Action
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val taskWord = if (count == 1) "task" else "tasks"
        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_TASKS_FOUND_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$count $taskWord found in your screenshot")
            .setContentText("Tap Review to edit and confirm, or Save all right away.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(reviewPending)
            .addAction(0, "Review", reviewPending)
            .addAction(0, "Save all", saveAllPending)
            .addAction(0, "Dismiss", dismissPending)
            .build()

        notificationManager.notify(notifId, notification)
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
