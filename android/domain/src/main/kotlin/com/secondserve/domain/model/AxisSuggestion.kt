package com.secondserve.domain.model

data class AxisSuggestion(
    val id: Long = 0L,
    val title: String,
    val status: AxisSuggestionStatus = AxisSuggestionStatus.PENDING,
    val generatedAt: Long
)
