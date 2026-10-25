package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.model.ParsedTaskCandidate
import com.example.data.model.ScreenshotRecord
import com.example.data.model.ScreenshotStatus
import com.example.data.model.Task
import com.example.data.repository.TaskRepository
import com.example.parser.TaskParser
import com.example.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TaskRepository(application)
    private val preferences = AppPreferences(application)

    // Unlocked state for biometric lock
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun unlockApp() {
        _isUnlocked.value = true
    }

    // Preferences Flows
    val isMonitoringEnabled = preferences.isMonitoringEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val isAutoSaveMode = preferences.isAutoSaveMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val defaultReminderHour = preferences.defaultReminderHour.stateIn(
        viewModelScope, SharingStarted.Eagerly, 9
    )
    val defaultReminderMinute = preferences.defaultReminderMinute.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )
    val isSensitiveFilterEnabled = preferences.isSensitiveFilterEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val autoDeleteOcrDays = preferences.autoDeleteOcrDays.stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )
    val isBiometricLockEnabled = preferences.isBiometricLockEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val isOnboardingCompleted = preferences.isOnboardingCompleted.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    // Navigation and screen state
    val allTasks = repository.allTasks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val inboxScreenshots = repository.inboxScreenshots.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _selectedTab = MutableStateFlow(0) // 0: Today, 1: Upcoming, 2: Someday, 3: Done
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filtered tasks
    val filteredTasks: StateFlow<List<Task>> = combine(allTasks, _selectedTab, _searchQuery) { tasks, tab, query ->
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val matchingSearch = if (query.isBlank()) {
            tasks
        } else {
            tasks.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.notes.contains(query, ignoreCase = true)
            }
        }

        when (tab) {
            0 -> { // Today
                matchingSearch.filter { !it.isDone && isTaskToday(it, today, zone) }
            }
            1 -> { // Upcoming
                matchingSearch.filter { !it.isDone && isTaskUpcoming(it, today, zone) }
            }
            2 -> { // Someday
                matchingSearch.filter { !it.isDone && it.dueAt == null }
            }
            3 -> { // Done
                matchingSearch.filter { it.isDone }
            }
            else -> matchingSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun isTaskToday(task: Task, today: LocalDate, zone: ZoneId): Boolean {
        if (task.dueAt == null) return false
        val taskDate = Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalDate()
        return taskDate.isEqual(today) || taskDate.isBefore(today) // Include overdue in today
    }

    private fun isTaskUpcoming(task: Task, today: LocalDate, zone: ZoneId): Boolean {
        if (task.dueAt == null) return false
        val taskDate = Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalDate()
        return taskDate.isAfter(today)
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Last deleted task for undo
    private var lastDeletedTask: Task? = null

    fun deleteTask(task: Task) {
        lastDeletedTask = task
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun undoDelete() {
        val taskToRestore = lastDeletedTask ?: return
        viewModelScope.launch {
            repository.saveTask(taskToRestore)
            lastDeletedTask = null
        }
    }

    fun toggleTaskDone(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskDone(task)
        }
    }

    fun toggleTaskPinned(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskPinned(task)
        }
    }

    fun saveNewTask(title: String, notes: String, dueAt: Long?) {
        viewModelScope.launch {
            val task = Task(
                title = title.trim(),
                notes = notes.trim(),
                dueAt = dueAt,
                remindAt = dueAt
            )
            repository.saveTask(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    // Review Screen State
    private val _currentReviewRecord = MutableStateFlow<ScreenshotRecord?>(null)
    val currentReviewRecord: StateFlow<ScreenshotRecord?> = _currentReviewRecord.asStateFlow()

    private val _reviewCandidates = MutableStateFlow<List<ParsedTaskCandidate>>(emptyList())
    val reviewCandidates: StateFlow<List<ParsedTaskCandidate>> = _reviewCandidates.asStateFlow()

    private val _reviewOcrText = MutableStateFlow("")
    val reviewOcrText: StateFlow<String> = _reviewOcrText.asStateFlow()

    private val _isReviewLoading = MutableStateFlow(false)
    val isReviewLoading: StateFlow<Boolean> = _isReviewLoading.asStateFlow()

    fun loadReview(screenshotId: Long) {
        viewModelScope.launch {
            _isReviewLoading.value = true
            val record = repository.getScreenshotById(screenshotId)
            _currentReviewRecord.value = record
            val text = record?.ocrText ?: ""
            _reviewOcrText.value = text

            val defaultHour = preferences.defaultReminderHour.first()
            val defaultMin = preferences.defaultReminderMinute.first()
            val parser = TaskParser(defaultHour, defaultMin)
            _reviewCandidates.value = parser.parseTasks(text)
            _isReviewLoading.value = false
        }
    }

    fun updateReviewOcrText(newText: String) {
        _reviewOcrText.value = newText
        viewModelScope.launch {
            val defaultHour = preferences.defaultReminderHour.first()
            val defaultMin = preferences.defaultReminderMinute.first()
            val parser = TaskParser(defaultHour, defaultMin)
            _reviewCandidates.value = parser.parseTasks(newText)
        }
    }

    fun toggleCandidateSelected(index: Int) {
        val current = _reviewCandidates.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(isSelected = !current[index].isSelected)
            _reviewCandidates.value = current
        }
    }

    fun updateCandidateTitle(index: Int, newTitle: String) {
        val current = _reviewCandidates.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(title = newTitle)
            _reviewCandidates.value = current
        }
    }

    fun updateCandidateDue(index: Int, dueMillis: Long?) {
        val current = _reviewCandidates.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(dueAt = dueMillis, remindAt = dueMillis)
            _reviewCandidates.value = current
        }
    }

    fun deleteCandidate(index: Int) {
        val current = _reviewCandidates.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _reviewCandidates.value = current
        }
    }

    fun saveSelectedReviewCandidates(onSaved: () -> Unit) {
        val record = _currentReviewRecord.value ?: return
        val selected = _reviewCandidates.value.filter { it.isSelected }
        viewModelScope.launch {
            if (selected.isNotEmpty()) {
                repository.saveCandidatesForScreenshot(record.id, selected)
            } else {
                repository.updateScreenshotStatus(record.id, ScreenshotStatus.REVIEWED)
            }
            onSaved()
        }
    }

    fun ignoreReviewScreenshot(onIgnored: () -> Unit) {
        val record = _currentReviewRecord.value ?: return
        viewModelScope.launch {
            repository.updateScreenshotStatus(record.id, ScreenshotStatus.IGNORED)
            onIgnored()
        }
    }

    // Manual Import (via photo picker or ACTION_SEND)
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    fun clearImportMessage() {
        _importMessage.value = null
    }

    fun importImage(uri: Uri, onReadyForReview: (Long) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = repository.processImageUri(uri)
            _isImporting.value = false

            when {
                result.isSensitive -> {
                    _importMessage.value = "Sensitive content detected. Screenshot skipped for privacy."
                }
                result.isFailed -> {
                    _importMessage.value = "Could not extract text from this image."
                }
                else -> {
                    onReadyForReview(result.screenshotRecord.id)
                }
            }
        }
    }

    // Settings actions
    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMonitoringEnabled(enabled)
            if (enabled) {
                WorkScheduler.scheduleScreenshotMonitoring(getApplication())
            } else {
                WorkScheduler.stopMonitoring(getApplication())
            }
        }
    }

    fun setAutoSaveMode(autoSave: Boolean) {
        viewModelScope.launch {
            preferences.setAutoSaveMode(autoSave)
        }
    }

    fun setDefaultReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferences.setDefaultReminderTime(hour, minute)
        }
    }

    fun setSensitiveFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSensitiveFilterEnabled(enabled)
        }
    }

    fun setAutoDeleteOcrDays(days: Int) {
        viewModelScope.launch {
            preferences.setAutoDeleteOcrDays(days)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBiometricLockEnabled(enabled)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(completed)
        }
    }

    fun getPlainTextExport(): String {
        return repository.generatePlainTextExport(allTasks.value)
    }

    fun getIcsExport(): String {
        return repository.generateIcsExport(allTasks.value)
    }
}
