package com.secondserve.domain.model

data class CoachingAnalysis(
    val id: Long = 0L,
    val sessionId: Long,
    val content: String,
    val generatedAt: Long
)
