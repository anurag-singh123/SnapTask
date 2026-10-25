package com.example.data.model

data class ParsedTaskCandidate(
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val extractedLinks: List<String> = emptyList(),
    val isSelected: Boolean = true
)
