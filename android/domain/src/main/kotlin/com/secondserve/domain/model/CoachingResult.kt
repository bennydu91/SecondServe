package com.secondserve.domain.model

enum class CoachingSource { GEMINI, VPS_MISTRAL, CACHE, STATIC }

data class CoachingResult(
    val text: String,
    val source: CoachingSource
)
