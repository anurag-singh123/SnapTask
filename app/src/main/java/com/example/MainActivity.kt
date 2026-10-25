package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.configureAppCheck
import com.example.data.model.Task
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ReviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TaskDetailScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.SnapTaskTheme
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

sealed class AppScreen {
    object Tasks : AppScreen()
    object Inbox : AppScreen()
    data class AiStudio(val initialTab: Int = 0) : AppScreen()
    object Settings : AppScreen()
    data class Review(val screenshotId: Long) : AppScreen()
    data class TaskDetail(val taskId: Long) : AppScreen()
}

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val aiViewModel: AiAssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            SnapTaskTheme {
                MainContent(
                    viewModel = viewModel,
                    aiViewModel = aiViewModel,
                    onPromptBiometric = { promptBiometricAuth() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private var pendingImportUri: Uri? = null
    private var pendingReviewId: Long? = null
    private var pendingTaskId: Long? = null
    private var triggerPhotoPickerOnLaunch: Boolean = false

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Share Target (ACTION_SEND with image)
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.data

            if (uri != null) {
                pendingImportUri = uri
            }
        }

        // 2. Open Review from Notification
        val openScreenshotId = intent.getLongExtra("OPEN_SCREENSHOT_ID", -1L)
        if (openScreenshotId != -1L) {
            pendingReviewId = openScreenshotId
        }

        // 3. Open Task from Reminder Notification
        val openTaskId = intent.getLongExtra("OPEN_TASK_ID", -1L)
        if (openTaskId != -1L) {
            pendingTaskId = openTaskId
        }

        // 4. Quick Settings Tile
        if (intent.getBooleanExtra("OPEN_IMPORT_PICKER", false)) {
            triggerPhotoPickerOnLaunch = true
        }
    }

    private fun promptBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.unlockApp()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SnapTask")
            .setSubtitle("Authenticate to view your tasks")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }

    @Composable
    private fun MainContent(
        viewModel: MainViewModel,
        aiViewModel: AiAssistantViewModel,
        onPromptBiometric: () -> Unit
    ) {
        val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
        val isBiometricLockEnabled by viewModel.isBiometricLockEnabled.collectAsStateWithLifecycle()
        val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }

        var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Tasks) }

        // Photo Picker launcher for Quick Tile or manual import
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.importImage(uri) { recordId ->
                    viewModel.loadReview(recordId)
                    currentScreen = AppScreen.Review(recordId)
                }
            }
        }

        // Handle pending incoming navigation
        LaunchedEffect(pendingImportUri) {
            val uri = pendingImportUri
            if (uri != null) {
                pendingImportUri = null
                viewModel.importImage(uri) { recordId ->
                    viewModel.loadReview(recordId)
                    currentScreen = AppScreen.Review(recordId)
                }
            }
        }

        LaunchedEffect(pendingReviewId) {
            val id = pendingReviewId
            if (id != null) {
                pendingReviewId = null
                viewModel.loadReview(id)
                currentScreen = AppScreen.Review(id)
            }
        }

        LaunchedEffect(pendingTaskId) {
            val id = pendingTaskId
            if (id != null) {
                pendingTaskId = null
                currentScreen = AppScreen.TaskDetail(id)
            }
        }

        LaunchedEffect(triggerPhotoPickerOnLaunch) {
            if (triggerPhotoPickerOnLaunch) {
                triggerPhotoPickerOnLaunch = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }

        // Biometric prompt on launch if locked
        LaunchedEffect(isBiometricLockEnabled, isUnlocked) {
            if (isBiometricLockEnabled && !isUnlocked) {
                onPromptBiometric()
            }
        }

        // Show import messages
        val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
        LaunchedEffect(importMessage) {
            if (importMessage != null) {
                snackbarHostState.showSnackbar(importMessage!!)
                viewModel.clearImportMessage()
            }
        }

        // 1. Onboarding Screen Check
        if (!isOnboardingCompleted) {
            OnboardingScreen(
                onFinished = {
                    viewModel.setOnboardingCompleted(true)
                }
            )
            return
        }

        // 2. Biometric Lock Guard
        if (isBiometricLockEnabled && !isUnlocked) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "SnapTask is Locked",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Authenticate using your biometric credentials to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick = onPromptBiometric,
                        modifier = Modifier.testTag("biometric_unlock_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock SnapTask")
                    }
                }
            }
            return
        }

        // 3. Main App UI with Navigation
        val tasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
        val allTasksList by viewModel.allTasks.collectAsStateWithLifecycle()
        val inboxScreenshots by viewModel.inboxScreenshots.collectAsStateWithLifecycle()
        val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

        val isMonitoringEnabled by viewModel.isMonitoringEnabled.collectAsStateWithLifecycle()
        val isAutoSaveMode by viewModel.isAutoSaveMode.collectAsStateWithLifecycle()
        val defaultReminderHour by viewModel.defaultReminderHour.collectAsStateWithLifecycle()
        val defaultReminderMinute by viewModel.defaultReminderMinute.collectAsStateWithLifecycle()
        val isSensitiveFilterEnabled by viewModel.isSensitiveFilterEnabled.collectAsStateWithLifecycle()
        val autoDeleteOcrDays by viewModel.autoDeleteOcrDays.collectAsStateWithLifecycle()

        val currentReviewRecord by viewModel.currentReviewRecord.collectAsStateWithLifecycle()
        val reviewCandidates by viewModel.reviewCandidates.collectAsStateWithLifecycle()
        val reviewOcrText by viewModel.reviewOcrText.collectAsStateWithLifecycle()
        val isReviewLoading by viewModel.isReviewLoading.collectAsStateWithLifecycle()

        // Handle system back navigation for detail/review screens
        BackHandler(enabled = currentScreen !is AppScreen.Tasks) {
            when (currentScreen) {
                is AppScreen.Review -> currentScreen = AppScreen.Inbox
                is AppScreen.TaskDetail -> currentScreen = AppScreen.Tasks
                else -> currentScreen = AppScreen.Tasks
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Show bottom navigation on primary destinations
                if (
                    currentScreen is AppScreen.Tasks ||
                    currentScreen is AppScreen.Inbox ||
                    currentScreen is AppScreen.AiStudio ||
                    currentScreen is AppScreen.Settings
                ) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        // Tasks Tab
                        NavigationBarItem(
                            selected = currentScreen is AppScreen.Tasks,
                            onClick = { currentScreen = AppScreen.Tasks },
                            icon = {
                                val pendingCount = allTasksList.count { !it.isDone }
                                if (pendingCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                                        Icon(Icons.Default.TaskAlt, contentDescription = "Tasks")
                                    }
                                } else {
                                    Icon(Icons.Default.TaskAlt, contentDescription = "Tasks")
                                }
                            },
                            label = { Text("Tasks") },
                            modifier = Modifier.testTag("nav_item_tasks")
                        )

                        // Inbox Tab
                        NavigationBarItem(
                            selected = currentScreen is AppScreen.Inbox,
                            onClick = { currentScreen = AppScreen.Inbox },
                            icon = {
                                if (inboxScreenshots.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${inboxScreenshots.size}") } }) {
                                        Icon(Icons.Default.Inbox, contentDescription = "Inbox")
                                    }
                                } else {
                                    Icon(Icons.Default.Inbox, contentDescription = "Inbox")
                                }
                            },
                            label = { Text("Inbox") },
                            modifier = Modifier.testTag("nav_item_inbox")
                        )

                        // AI Studio Tab (Gemini Chat, Google Search Grounding & Image Generation)
                        NavigationBarItem(
                            selected = currentScreen is AppScreen.AiStudio,
                            onClick = { currentScreen = AppScreen.AiStudio() },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Studio") },
                            label = { Text("AI Studio") },
                            modifier = Modifier.testTag("nav_item_ai")
                        )

                        // Settings Tab
                        NavigationBarItem(
                            selected = currentScreen is AppScreen.Settings,
                            onClick = { currentScreen = AppScreen.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("nav_item_settings")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val screen = currentScreen) {
                    is AppScreen.Tasks -> {
                        TasksScreen(
                            tasks = tasks,
                            selectedTab = selectedTab,
                            searchQuery = searchQuery,
                            onTabSelected = { viewModel.selectTab(it) },
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onToggleDone = { viewModel.toggleTaskDone(it) },
                            onTogglePinned = { viewModel.toggleTaskPinned(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onUndoDelete = { viewModel.undoDelete() },
                            onTaskClick = { taskId -> currentScreen = AppScreen.TaskDetail(taskId) },
                            onAddNewTask = { title, notes, dueAt -> viewModel.saveNewTask(title, notes, dueAt) },
                            snackbarHostState = snackbarHostState
                        )
                    }

                    is AppScreen.Inbox -> {
                        InboxScreen(
                            inboxScreenshots = inboxScreenshots,
                            isImporting = isImporting,
                            onScreenshotClick = { screenshotId ->
                                viewModel.loadReview(screenshotId)
                                currentScreen = AppScreen.Review(screenshotId)
                            },
                            onImportUri = { uri ->
                                viewModel.importImage(uri) { recordId ->
                                    viewModel.loadReview(recordId)
                                    currentScreen = AppScreen.Review(recordId)
                                }
                            }
                        )
                    }

                    is AppScreen.AiStudio -> {
                        AiStudioScreen(
                            aiViewModel = aiViewModel,
                            activeTasks = allTasksList.filter { !it.isDone },
                            initialTab = screen.initialTab,
                            onSaveTaskFromAi = { title, notes ->
                                viewModel.saveNewTask(title, notes, null)
                            },
                            onSendImageUriToOcr = { uri ->
                                viewModel.importImage(uri) { recordId ->
                                    viewModel.loadReview(recordId)
                                    currentScreen = AppScreen.Review(recordId)
                                }
                            }
                        )
                    }

                    is AppScreen.Settings -> {
                        SettingsScreen(
                            isMonitoringEnabled = isMonitoringEnabled,
                            isAutoSaveMode = isAutoSaveMode,
                            defaultReminderHour = defaultReminderHour,
                            defaultReminderMinute = defaultReminderMinute,
                            isSensitiveFilterEnabled = isSensitiveFilterEnabled,
                            autoDeleteOcrDays = autoDeleteOcrDays,
                            isBiometricLockEnabled = isBiometricLockEnabled,
                            onSetMonitoringEnabled = { viewModel.setMonitoringEnabled(it) },
                            onSetAutoSaveMode = { viewModel.setAutoSaveMode(it) },
                            onSetDefaultReminderTime = { h, m -> viewModel.setDefaultReminderTime(h, m) },
                            onSetSensitiveFilterEnabled = { viewModel.setSensitiveFilterEnabled(it) },
                            onSetAutoDeleteOcrDays = { viewModel.setAutoDeleteOcrDays(it) },
                            onSetBiometricLockEnabled = { viewModel.setBiometricLockEnabled(it) },
                            onExportPlainText = { viewModel.getPlainTextExport() },
                            onExportIcs = { viewModel.getIcsExport() }
                        )
                    }

                    is AppScreen.Review -> {
                        ReviewScreen(
                            record = currentReviewRecord,
                            candidates = reviewCandidates,
                            ocrText = reviewOcrText,
                            isLoading = isReviewLoading,
                            onBack = { currentScreen = AppScreen.Inbox },
                            onToggleCandidateSelected = { viewModel.toggleCandidateSelected(it) },
                            onUpdateCandidateTitle = { idx, title -> viewModel.updateCandidateTitle(idx, title) },
                            onUpdateCandidateDue = { idx, due -> viewModel.updateCandidateDue(idx, due) },
                            onDeleteCandidate = { viewModel.deleteCandidate(it) },
                            onUpdateOcrText = { viewModel.updateReviewOcrText(it) },
                            onSaveSelected = {
                                viewModel.saveSelectedReviewCandidates {
                                    currentScreen = AppScreen.Tasks
                                }
                            },
                            onIgnoreScreenshot = {
                                viewModel.ignoreReviewScreenshot {
                                    currentScreen = AppScreen.Inbox
                                }
                            }
                        )
                    }

                    is AppScreen.TaskDetail -> {
                        val activeTask = allTasksList.firstOrNull { it.id == screen.taskId }
                        if (activeTask != null) {
                            val sourceRecord = remember(activeTask.sourceScreenshotId, inboxScreenshots) {
                                inboxScreenshots.firstOrNull { it.id == activeTask.sourceScreenshotId }
                            }
                            TaskDetailScreen(
                                task = activeTask,
                                sourceRecord = sourceRecord,
                                onBack = { currentScreen = AppScreen.Tasks },
                                onSaveTask = { viewModel.updateTask(it) },
                                onDeleteTask = { viewModel.deleteTask(it) },
                                onResearchWithAi = { taskToResearch ->
                                    val prompt = buildString {
                                        append("Help me research and plan actionable next steps for this task: \"${taskToResearch.title}\"")
                                        if (taskToResearch.notes.isNotBlank()) {
                                            append(". Additional details: ${taskToResearch.notes}")
                                        }
                                    }
                                    aiViewModel.setSearchGroundingEnabled(true)
                                    aiViewModel.sendChatMessage(prompt)
                                    currentScreen = AppScreen.AiStudio(initialTab = 0)
                                }
                            )
                        } else {
                            // If task was deleted, go back to tasks
                            LaunchedEffect(Unit) {
                                currentScreen = AppScreen.Tasks
                            }
                        }
                    }
                }
            }
        }
    }
}
