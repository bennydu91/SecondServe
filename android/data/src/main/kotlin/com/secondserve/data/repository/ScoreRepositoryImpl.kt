package com.secondserve.data.repository

import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.repository.ScoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoreRepositoryImpl @Inject constructor() : ScoreRepository {

    private val _latestScore = MutableStateFlow<MatchScore?>(null)
    override val latestScore: StateFlow<MatchScore?> = _latestScore.asStateFlow()

    override suspend fun updateScore(score: MatchScore) {
        _latestScore.value = score
    }
}
