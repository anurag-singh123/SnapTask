package com.example.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.reminder.ReminderManager
import java.io.File
import java.util.Locale

@Composable
fun SettingsScreen(
    isMonitoringEnabled: Boolean,
    isAutoSaveMode: Boolean,
    defaultReminderHour: Int,
    defaultReminderMinute: Int,
    isSensitiveFilterEnabled: Boolean,
    autoDeleteOcrDays: Int,
    isBiometricLockEnabled: Boolean,
    onSetMonitoringEnabled: (Boolean) -> Unit,
    onSetAutoSaveMode: (Boolean) -> Unit,
    onSetDefaultReminderTime: (Int, Int) -> Unit,
    onSetSensitiveFilterEnabled: (Boolean) -> Unit,
    onSetAutoDeleteOcrDays: (Int) -> Unit,
    onSetBiometricLockEnabled: (Boolean) -> Unit,
    onExportPlainText: () -> String,
    onExportIcs: () -> String
) {
    val context = LocalContext.current
    val reminderManager = remember { ReminderManager(context) }
    val canScheduleExact = remember { reminderManager.canScheduleExact() }

    var showAutoDeleteMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Settings & Preferences",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Section 1: Screenshot Detection & Monitoring
            Text(
                "Detection & Automation",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Monitoring toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Screenshot Detection", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Watch for screenshots in background and extract tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isMonitoringEnabled,
                            onCheckedChange = onSetMonitoringEnabled,
                            modifier = Modifier.testTag("switch_monitoring")
                        )
                    }

                    // Mode: Ask vs Auto-save
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Extraction Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetAutoSaveMode(false) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isAutoSaveMode,
                                onClick = { onSetAutoSaveMode(false) },
                                modifier = Modifier.testTag("radio_mode_ask")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Ask before saving (Recommended)", fontWeight = FontWeight.Medium)
                                Text(
                                    "Shows notification to review & edit tasks before saving",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetAutoSaveMode(true) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isAutoSaveMode,
                                onClick = { onSetAutoSaveMode(true) },
                                modifier = Modifier.testTag("radio_mode_auto")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Auto-save directly", fontWeight = FontWeight.Medium)
                                Text(
                                    "Saves detected tasks immediately to your task list",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Privacy & Filters
            Text(
                "Privacy & Security",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Sensitive Filter Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Sensitive Content Filter", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Silently ignores OTPs, card numbers (Luhn check), and passwords",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isSensitiveFilterEnabled,
                            onCheckedChange = onSetSensitiveFilterEnabled,
                            modifier = Modifier.testTag("switch_sensitive_filter")
                        )
                    }

                    // Auto-delete stored OCR text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Auto-Delete OCR Text", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Cleans stored OCR text from history after N days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Box {
                            TextButton(onClick = { showAutoDeleteMenu = true }) {
                                Text(if (autoDeleteOcrDays == 0) "Off" else "$autoDeleteOcrDays days")
                            }
                            DropdownMenu(
                                expanded = showAutoDeleteMenu,
                                onDismissRequest = { showAutoDeleteMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Off (Keep text)") },
                                    onClick = { onSetAutoDeleteOcrDays(0); showAutoDeleteMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("7 days") },
                                    onClick = { onSetAutoDeleteOcrDays(7); showAutoDeleteMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("30 days") },
                                    onClick = { onSetAutoDeleteOcrDays(30); showAutoDeleteMenu = false }
                                )
                            }
                        }
                    }

                    // Biometric Lock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Biometric App Lock", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Require fingerprint / face unlock to open SnapTask",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isBiometricLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val bm = BiometricManager.from(context)
                                    val canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                                        onSetBiometricLockEnabled(true)
                                    } else {
                                        // Prompt user or enable credential
                                        onSetBiometricLockEnabled(true)
                                    }
                                } else {
                                    onSetBiometricLockEnabled(false)
                                }
                            },
                            modifier = Modifier.testTag("switch_biometric_lock")
                        )
                    }
                }
            }

            // Section 3: Reminders & Alarms
            Text(
                "Reminders & System",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Default reminder time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Default Reminder Time", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Used when a date is found without a specific hour",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        onSetDefaultReminderTime(hour, minute)
                                    },
                                    defaultReminderHour,
                                    defaultReminderMinute,
                                    false
                                ).show()
                            }
                        ) {
                            val timeStr = formatTime(defaultReminderHour, defaultReminderMinute)
                            Text(timeStr, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Exact Alarm status notice
                    if (!canScheduleExact) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Exact Alarms Not Granted", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "Reminders may be slightly delayed by system battery management.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    TextButton(onClick = {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        context.startActivity(intent)
                                    }) {
                                        Text("Grant")
                                    }
                                }
                            }
                        }
                    }

                    // Battery optimization whitelist shortcut
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Battery Optimization", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Exclude SnapTask from background killing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val fallback = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(fallback)
                            }
                        }) {
                            Text("Manage")
                        }
                    }
                }
            }

            // Section 4: Export Tasks
            Text(
                "Export",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Export Plain Text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val text = onExportPlainText()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "SnapTask Export")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Tasks"))
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share as Plain Text", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Share to-do list via Notes, WhatsApp, or Email",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Export Calendar .ics
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val icsContent = onExportIcs()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/calendar"
                                    putExtra(Intent.EXTRA_SUBJECT, "SnapTask Calendar Events")
                                    putExtra(Intent.EXTRA_TEXT, icsContent)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Calendar Events"))
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export to Calendar (.ics)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Export tasks with due dates to Google Calendar or Outlook",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // About Privacy Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("SnapTask v1.0 • Privacy First", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Zero network permissions requested. Everything stays on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val ampm = if (hour >= 12) "PM" else "AM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.ROOT, "%d:%02d %s", displayHour, minute, ampm)
}
