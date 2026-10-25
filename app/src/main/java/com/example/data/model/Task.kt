package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["dueAt"]),
        Index(value = ["remindAt"]),
        Index(value = ["isDone"]),
        Index(value = ["sourceScreenshotId"])
    ]
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceScreenshotId: Long? = null,
    val isPinned: Boolean = false
)
