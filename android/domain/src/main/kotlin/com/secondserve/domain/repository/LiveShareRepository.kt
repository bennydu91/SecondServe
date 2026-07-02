package com.secondserve.domain.repository

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.model.MatchScore

interface LiveShareRepository {
    suspend fun getOrCreateShare(sessionId: Long): AppResult<LiveShareInfo>
    suspend fun getCachedShare(sessionId: Long): LiveShareInfo?
    suspend fun pushScore(sessionId: Long, score: MatchScore, context: LiveShareContext)
}
