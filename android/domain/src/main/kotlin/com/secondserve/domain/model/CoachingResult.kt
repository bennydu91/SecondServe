package com.secondserve.domain.model

enum class CoachingSource { GEMINI, CACHE, STATIC }

data class CoachingResult(
    val text: String,
    val source: CoachingSource
)
