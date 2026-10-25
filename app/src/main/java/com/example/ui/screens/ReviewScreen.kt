package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.model.ParsedTaskCandidate
import com.example.data.model.ScreenshotRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    record: ScreenshotRecord?,
    candidates: List<ParsedTaskCandidate>,
    ocrText: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onToggleCandidateSelected: (Int) -> Unit,
    onUpdateCandidateTitle: (Int, String) -> Unit,
    onUpdateCandidateDue: (Int, Long?) -> Unit,
    onDeleteCandidate: (Int) -> Unit,
    onUpdateOcrText: (String) -> Unit,
    onSaveSelected: () -> Unit,
    onIgnoreScreenshot: () -> Unit
) {
    val context = LocalContext.current
    var showZoomDialog by remember { mutableStateOf(false) }
    var isOcrExpanded by remember { mutableStateOf(false) }

    val selectedCount = candidates.count { it.isSelected }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("review_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Review Screenshot") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("review_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onIgnoreScreenshot,
                        modifier = Modifier.testTag("ignore_screenshot_top_button")
                    ) {
                        Text("Ignore", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onIgnoreScreenshot,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ignore_screenshot_button")
                    ) {
                        Text("Ignore")
                    }

                    Button(
                        onClick = onSaveSelected,
                        enabled = selectedCount > 0,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("save_selected_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedCount == 1) "Save 1 Task" else "Save $selectedCount Tasks")
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoading || record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Screenshot Preview Banner (tap to zoom)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showZoomDialog = true }
                            .testTag("screenshot_preview_card"),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(Uri.parse(record.uri))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Screenshot Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Tap to zoom pill
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
                                    Icon(
                                        Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tap to zoom", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Extracted OCR Text (Collapsible & Editable)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isOcrExpanded = !isOcrExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Extracted OCR Text",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Icon(
                                    imageVector = if (isOcrExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }

                            AnimatedVisibility(visible = isOcrExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    OutlinedTextField(
                                        value = ocrText,
                                        onValueChange = onUpdateOcrText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 100.dp, max = 220.dp)
                                            .testTag("ocr_text_editor"),
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Tip: Editing text automatically updates suggested tasks.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Candidate Tasks Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detected Tasks (${candidates.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$selectedCount selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 4. Candidate Task Cards
                itemsIndexed(candidates) { index, candidate ->
                    ReviewCandidateCard(
                        index = index,
                        candidate = candidate,
                        onToggleSelected = { onToggleCandidateSelected(index) },
                        onTitleChange = { onUpdateCandidateTitle(index, it) },
                        onDueChange = { onUpdateCandidateDue(index, it) },
                        onDelete = { onDeleteCandidate(index) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Zoom Dialog
    if (showZoomDialog && record != null) {
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
                            .data(Uri.parse(record.uri))
                            .build(),
                        contentDescription = "Full Screenshot",
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

@Composable
fun ReviewCandidateCard(
    index: Int,
    candidate: ParsedTaskCandidate,
    onToggleSelected: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDueChange: (Long?) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("candidate_card_$index"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.isSelected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (candidate.isSelected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = candidate.isSelected,
                    onCheckedChange = { onToggleSelected() },
                    modifier = Modifier.testTag("candidate_checkbox_$index")
                )

                OutlinedTextField(
                    value = candidate.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .testTag("candidate_title_input_$index"),
                    singleLine = false,
                    maxLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("candidate_delete_$index")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete candidate",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Due Date Chip & Notes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dueLabel = if (candidate.dueAt != null) {
                    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(candidate.dueAt), ZoneId.systemDefault())
                    dt.format(DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a"))
                } else {
                    "Set due date"
                }

                AssistChip(
                    onClick = {
                        val now = LocalDateTime.now()
                        val initialDate = candidate.dueAt?.let {
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                        } ?: now

                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val picked = LocalDateTime.of(year, month + 1, day, hour, minute)
                                        val millis = picked.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        onDueChange(millis)
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
                    },
                    label = { Text(dueLabel, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (candidate.dueAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingIcon = if (candidate.dueAt != null) {
                        {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear date",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onDueChange(null) }
                            )
                        }
                    } else null,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (candidate.dueAt != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
                    )
                )
            }

            if (candidate.notes.isNotBlank()) {
                Text(
                    text = candidate.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                )
            }
        }
    }
}
