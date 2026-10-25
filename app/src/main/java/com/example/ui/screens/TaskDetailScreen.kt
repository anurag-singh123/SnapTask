package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ScreenshotRecord
import com.example.data.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: Task,
    sourceRecord: ScreenshotRecord?,
    onBack: () -> Unit,
    onSaveTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onResearchWithAi: ((Task) -> Unit)? = null
) {
    val context = LocalContext.current

    var title by remember(task) { mutableStateOf(task.title) }
    var notes by remember(task) { mutableStateOf(task.notes) }
    var dueAt by remember(task) { mutableStateOf(task.dueAt) }
    var remindAt by remember(task) { mutableStateOf(task.remindAt) }
    var isPinned by remember(task) { mutableStateOf(task.isPinned) }
    var isDone by remember(task) { mutableStateOf(task.isDone) }

    var showZoomDialog by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("task_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Task Details") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isPinned = !isPinned },
                        modifier = Modifier.testTag("detail_pin_button")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = {
                            onDeleteTask(task)
                            onBack()
                        },
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isDone = !isDone
                            onSaveTask(
                                task.copy(
                                    title = title,
                                    notes = notes,
                                    dueAt = dueAt,
                                    remindAt = remindAt,
                                    isPinned = isPinned,
                                    isDone = isDone
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isDone) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDone) "Reopen" else "Mark Done")
                    }

                    Button(
                        onClick = {
                            onSaveTask(
                                task.copy(
                                    title = title,
                                    notes = notes,
                                    dueAt = dueAt,
                                    remindAt = remindAt,
                                    isPinned = isPinned,
                                    isDone = isDone
                                )
                            )
                            onBack()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_save_button")
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Task Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_title_input"),
                textStyle = MaterialTheme.typography.titleMedium
            )

            // Due Date & Time Picker Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Schedule & Reminder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dueLabel = if (dueAt != null) {
                            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueAt!!), zone)
                            dt.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm a"))
                        } else {
                            "No due date set"
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (dueAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dueLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (dueAt != null) FontWeight.Medium else FontWeight.Normal
                            )
                        }

                        Row {
                            TextButton(
                                onClick = {
                                    val now = LocalDateTime.now()
                                    val initialDate = dueAt?.let {
                                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone)
                                    } ?: now

                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            TimePickerDialog(
                                                context,
                                                { _, hour, minute ->
                                                    val picked = LocalDateTime.of(year, month + 1, day, hour, minute)
                                                    val millis = picked.atZone(zone).toInstant().toEpochMilli()
                                                    dueAt = millis
                                                    remindAt = millis
                                                },
                                                initialDate.hour,
                                                initialDate.minute,
                                                false
                                            ).show()
                                        },
                                        initialDate.year,
                                        initialDate.monthValue - 1,
                                        initialDate.dayOfMonth
                                    ).show()
                                }
                            ) {
                                Text(if (dueAt != null) "Change" else "Set")
                            }

                            if (dueAt != null) {
                                TextButton(onClick = { dueAt = null; remindAt = null }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Snooze quick actions
                    if (dueAt != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    val newTime = System.currentTimeMillis() + 10 * 60 * 1000L
                                    remindAt = newTime
                                },
                                label = { Text("+10 min") },
                                leadingIcon = { Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                            AssistChip(
                                onClick = {
                                    val newTime = System.currentTimeMillis() + 60 * 60 * 1000L
                                    remindAt = newTime
                                },
                                label = { Text("+1 hour") }
                            )
                            AssistChip(
                                onClick = {
                                    val tomorrow9am = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0))
                                    remindAt = tomorrow9am.atZone(zone).toInstant().toEpochMilli()
                                },
                                label = { Text("Tomorrow 9am") }
                            )
                        }
                    }
                }
            }

            // Task Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes & Links") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .testTag("detail_notes_input"),
                maxLines = 8
            )

            if (onResearchWithAi != null) {
                OutlinedButton(
                    onClick = {
                        onResearchWithAi(
                            task.copy(title = title, notes = notes)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_research_with_ai_button")
                ) {
                    Text("Research Task with Google Search & Gemini")
                }
            }

            // Extracted Link Actions
            val extractedLinks = extractTappableLinks(notes)
            if (extractedLinks.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Tappable Links & Contacts",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        extractedLinks.forEach { link ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openLinkIntent(context, link) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = link,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                )
                            }
                        }
                    }
                }
            }

            // Source Screenshot Preview Card
            if (sourceRecord != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showZoomDialog = true }
                        .testTag("detail_source_screenshot_card"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            coil.compose.SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(Uri.parse(sourceRecord.uri))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Source Screenshot",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Source screenshot was deleted from device",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            )

                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View Screenshot", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    if (showZoomDialog && sourceRecord != null) {
        Dialog(onDismissRequest = { showZoomDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = Color.Black
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { showZoomDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(sourceRecord.uri))
                            .build(),
                        contentDescription = "Full Source Screenshot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

private fun extractTappableLinks(text: String): List<String> {
    val results = mutableListOf<String>()
    val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
    val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    val phonePattern = Regex("(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")

    urlPattern.findAll(text).forEach { results.add(it.value) }
    emailPattern.findAll(text).forEach { results.add(it.value) }
    phonePattern.findAll(text).forEach { if (it.value.trim().length >= 7) results.add(it.value.trim()) }

    return results.distinct()
}

private fun openLinkIntent(context: android.content.Context, link: String) {
    try {
        val intent = when {
            link.contains("@") -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$link"))
            link.matches(Regex(".*\\d{7,}.*")) -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$link"))
            else -> {
                val url = if (link.startsWith("http")) link else "https://$link"
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}
