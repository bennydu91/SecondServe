package com.secondserve.domain.repository

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingAnalysis
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern
import kotlinx.coroutines.flow.Flow

interface CoachingRepository {
    suspend fun getCachedAdvice(matchId: Long, pattern: MatchPattern): CoachingCacheEntry?
    suspend fun saveAdvice(matchId: Long, pattern: MatchPattern, content: String)
    suspend fun markMatchEntriesStale(matchId: Long)
    suspend fun getAdvicesForSession(sessionId: Long): List<CoachingCacheEntry>
    suspend fun saveAnalysis(sessionId: Long, content: String): AppResult<CoachingAnalysis>
    suspend fun getAnalysisForSession(sessionId: Long): CoachingAnalysis?
    fun observeAnalysisForSession(sessionId: Long): Flow<CoachingAnalysis?>
}
