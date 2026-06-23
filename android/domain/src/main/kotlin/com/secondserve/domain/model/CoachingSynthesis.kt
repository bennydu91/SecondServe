package com.secondserve.domain.model

data class CoachingSynthesis(
    val id: Long = 0L,
    val content: String,
    val sessionCount: Int,
    val generatedAt: Long
)
