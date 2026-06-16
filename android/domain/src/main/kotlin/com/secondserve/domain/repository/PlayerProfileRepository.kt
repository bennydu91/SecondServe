package com.secondserve.domain.repository

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry
import kotlinx.coroutines.flow.Flow

interface PlayerProfileRepository {
    suspend fun getProfile(): AppResult<PlayerProfile?>
    suspend fun saveRanking(series: String, points: Int): AppResult<Unit>
    fun getRankingHistory(): Flow<List<RankingEntry>>
    suspend fun buildMatchContextProfile(): MatchContextProfile
    suspend fun saveProfileDetails(
        playStyle: String?,
        preferredSurfaces: List<String>,
        coachInstruction1: String?,
        coachInstruction2: String?,
        coachInstruction3: String?
    ): AppResult<Unit>
    fun observeMatchSessionCount(): Flow<Int>
}
