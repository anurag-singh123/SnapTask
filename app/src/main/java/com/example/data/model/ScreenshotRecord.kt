package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screenshot_records",
    indices = [
        Index(value = ["mediaStoreId"], unique = true),
        Index(value = ["uri"]),
        Index(value = ["status"])
    ]
)
data class ScreenshotRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaStoreId: Long? = null,
    val uri: String,
    val ocrText: String? = null,
    val processedAt: Long = System.currentTimeMillis(),
    val status: ScreenshotStatus = ScreenshotStatus.NEW,
    val tasksCount: Int = 0
)
