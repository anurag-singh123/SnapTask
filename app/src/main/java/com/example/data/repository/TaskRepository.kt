package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.model.ParsedTaskCandidate
import com.example.data.model.ScreenshotRecord
import com.example.data.model.ScreenshotStatus
import com.example.data.model.Task
import com.example.ocr.OcrProcessor
import com.example.parser.TaskParser
import com.example.reminder.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context.applicationContext),
    private val preferences: AppPreferences = AppPreferences(context.applicationContext)
) {

    private val taskDao = database.taskDao()
    private val screenshotDao = database.screenshotDao()
    private val ocrProcessor by lazy { OcrProcessor(context.applicationContext) }
    private val reminderManager by lazy { ReminderManager(context.applicationContext) }

    val allTasks: Flow<List<Task>> = taskDao.getAllTasksFlow()
    val inboxScreenshots: Flow<List<ScreenshotRecord>> = screenshotDao.getInboxRecordsFlow()
    val allScreenshots: Flow<List<ScreenshotRecord>> = screenshotDao.getAllFlow()

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    suspend fun getScreenshotById(id: Long): ScreenshotRecord? = screenshotDao.getById(id)

    suspend fun saveTask(task: Task): Long {
        val id = taskDao.insert(task)
        val saved = task.copy(id = id)
        if (saved.remindAt != null && saved.remindAt > System.currentTimeMillis() && !saved.isDone) {
            reminderManager.scheduleReminder(saved)
        } else {
            reminderManager.cancelReminder(id)
        }
        return id
    }

    suspend fun updateTask(task: Task) {
        taskDao.update(task)
        if (task.remindAt != null && task.remindAt > System.currentTimeMillis() && !task.isDone) {
            reminderManager.scheduleReminder(task)
        } else {
            reminderManager.cancelReminder(task.id)
        }
    }

    suspend fun deleteTask(task: Task) {
        taskDao.delete(task)
        reminderManager.cancelReminder(task.id)
    }

    suspend fun toggleTaskDone(task: Task) {
        val updated = task.copy(isDone = !task.isDone)
        taskDao.update(updated)
        if (updated.isDone) {
            reminderManager.cancelReminder(task.id)
        } else if (updated.remindAt != null && updated.remindAt > System.currentTimeMillis()) {
            reminderManager.scheduleReminder(updated)
        }
    }

    suspend fun toggleTaskPinned(task: Task) {
        taskDao.update(task.copy(isPinned = !task.isPinned))
    }

    suspend fun updateScreenshotStatus(id: Long, status: ScreenshotStatus) {
        screenshotDao.updateStatus(id, status)
    }

    suspend fun deleteScreenshot(record: ScreenshotRecord) {
        screenshotDao.delete(record)
    }

    /**
     * Imports an image URI manually (via photo picker or share target).
     * Runs OCR and returns parsed candidates and record ID.
     */
    data class ManualImportResult(
        val screenshotRecord: ScreenshotRecord,
        val candidates: List<ParsedTaskCandidate>,
        val isSensitive: Boolean,
        val isFailed: Boolean
    )

    suspend fun processImageUri(uri: Uri): ManualImportResult = withContext(Dispatchers.IO) {
        val sensitiveFilter = preferences.isSensitiveFilterEnabled.first()
        val defaultHour = preferences.defaultReminderHour.first()
        val defaultMinute = preferences.defaultReminderMinute.first()
        val parser = TaskParser(defaultHour, defaultMinute)

        // Check if existing record with this uri exists
        var record = screenshotDao.getByUri(uri.toString())
        if (record == null) {
            val recordId = screenshotDao.insert(
                ScreenshotRecord(
                    mediaStoreId = null,
                    uri = uri.toString(),
                    status = ScreenshotStatus.NEW,
                    processedAt = System.currentTimeMillis()
                )
            )
            record = screenshotDao.getById(recordId) ?: ScreenshotRecord(id = recordId, uri = uri.toString())
        }

        // Run OCR
        val ocrResult = ocrProcessor.recognizeTextFromUri(uri)
        if (ocrResult.isFailure) {
            screenshotDao.update(record.copy(status = ScreenshotStatus.FAILED))
            return@withContext ManualImportResult(
                screenshotRecord = record.copy(status = ScreenshotStatus.FAILED),
                candidates = emptyList(),
                isSensitive = false,
                isFailed = true
            )
        }

        val text = ocrResult.getOrNull() ?: ""

        // Sensitive check
        if (sensitiveFilter && parser.isSensitive(text)) {
            val updated = record.copy(status = ScreenshotStatus.SENSITIVE, ocrText = null)
            screenshotDao.update(updated)
            return@withContext ManualImportResult(
                screenshotRecord = updated,
                candidates = emptyList(),
                isSensitive = true,
                isFailed = false
            )
        }

        val candidates = parser.parseTasks(text)
        val updated = record.copy(
            status = ScreenshotStatus.PROCESSED,
            ocrText = text,
            tasksCount = candidates.size
        )
        screenshotDao.update(updated)

        ManualImportResult(
            screenshotRecord = updated,
            candidates = candidates,
            isSensitive = false,
            isFailed = false
        )
    }

    suspend fun saveCandidatesForScreenshot(
        screenshotId: Long,
        selectedCandidates: List<ParsedTaskCandidate>
    ) {
        val tasks = selectedCandidates.map {
            Task(
                title = it.title,
                notes = it.notes,
                dueAt = it.dueAt,
                remindAt = it.remindAt,
                sourceScreenshotId = screenshotId
            )
        }
        val ids = taskDao.insertAll(tasks)
        screenshotDao.updateStatus(screenshotId, ScreenshotStatus.REVIEWED)

        ids.forEachIndexed { index, id ->
            val task = tasks[index].copy(id = id)
            if (task.remindAt != null && task.remindAt > System.currentTimeMillis()) {
                reminderManager.scheduleReminder(task)
            }
        }
    }

    fun generatePlainTextExport(tasks: List<Task>): String {
        val sb = StringBuilder("SnapTask To-Do List:\n\n")
        val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault())

        for (task in tasks) {
            val check = if (task.isDone) "[x]" else "[ ]"
            sb.append("$check ${task.title}\n")
            if (task.dueAt != null) {
                sb.append("   Due: ${dateFormatter.format(Instant.ofEpochMilli(task.dueAt))}\n")
            }
            if (task.notes.isNotBlank()) {
                sb.append("   Notes: ${task.notes}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun generateIcsExport(tasks: List<Task>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\n")
        sb.append("VERSION:2.0\n")
        sb.append("PRODID:-//SnapTask//NONSGML v1.0//EN\n")

        val utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))

        for (task in tasks) {
            if (task.dueAt != null && !task.isDone) {
                val dueInstant = Instant.ofEpochMilli(task.dueAt)
                val createdInstant = Instant.ofEpochMilli(task.createdAt)
                val endInstant = dueInstant.plusSeconds(3600) // 1 hour duration default

                sb.append("BEGIN:VEVENT\n")
                sb.append("UID:snaptask-${task.id}@local\n")
                sb.append("DTSTAMP:${utcFormatter.format(createdInstant)}\n")
                sb.append("DTSTART:${utcFormatter.format(dueInstant)}\n")
                sb.append("DTEND:${utcFormatter.format(endInstant)}\n")
                sb.append("SUMMARY:${escapeIcs(task.title)}\n")
                if (task.notes.isNotBlank()) {
                    sb.append("DESCRIPTION:${escapeIcs(task.notes)}\n")
                }
                sb.append("STATUS:CONFIRMED\n")
                sb.append("END:VEVENT\n")
            }
        }

        sb.append("END:VCALENDAR\n")
        return sb.toString()
    }

    private fun escapeIcs(value: String): String {
        return value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }
}
