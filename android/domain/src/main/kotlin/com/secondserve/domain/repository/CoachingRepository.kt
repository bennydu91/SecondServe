package com.secondserve.domain.repository

import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern

interface CoachingRepository {
    suspend fun getCachedAdvice(matchId: Long, pattern: MatchPattern): CoachingCacheEntry?
    suspend fun saveAdvice(matchId: Long, pattern: MatchPattern, content: String)
    suspend fun markMatchEntriesStale(matchId: Long)
}
