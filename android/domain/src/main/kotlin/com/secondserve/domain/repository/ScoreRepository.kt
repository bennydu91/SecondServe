package com.secondserve.domain.repository

import com.secondserve.domain.model.MatchScore
import kotlinx.coroutines.flow.StateFlow

interface ScoreRepository {
    val latestScore: StateFlow<MatchScore?>
    suspend fun updateScore(score: MatchScore)
}
