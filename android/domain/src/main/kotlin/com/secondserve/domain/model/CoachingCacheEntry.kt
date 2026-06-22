package com.secondserve.domain.model

data class CoachingCacheEntry(
    val id: Long = 0L,
    val matchId: Long,
    val pattern: MatchPattern,
    val content: String,
    val generatedAt: Long,
    val isStale: Boolean = false
)
