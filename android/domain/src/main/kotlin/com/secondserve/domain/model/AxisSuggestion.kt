package com.secondserve.domain.model

data class AxisSuggestion(
    val id: Long = 0L,
    val title: String,
    val status: String = "PENDING",
    val generatedAt: Long
)
