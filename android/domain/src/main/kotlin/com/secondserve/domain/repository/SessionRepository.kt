package com.secondserve.domain.repository

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun createSession(session: Session): AppResult<Session>
    suspend fun createCompletedSession(session: Session): AppResult<Session>
    fun getAllSessions(): Flow<List<Session>>
    suspend fun getSessionById(id: Long): Session?
    suspend fun closeSession(
        sessionId: Long,
        result: String,
        scoreText: String?,
        feelingRating: Int?,
        feelingComment: String?
    ): AppResult<Unit>
    suspend fun updateMatchStats(
        sessionId: Long,
        firstServePercentSelf: Int?,
        firstServePercentOpponent: Int?,
        winnersSelf: Int?,
        winnersOpponent: Int?
    ): AppResult<Unit>
    suspend fun getPointSummaryForSession(sessionId: Long): Pair<Int, Int>
    suspend fun countCompletedSince(afterMs: Long): Int
    suspend fun getCompletedSince(afterMs: Long): List<Session>
    suspend fun deleteSession(sessionId: Long): AppResult<Unit>
}
