package com.secondserve.domain.usecase.match

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.repository.SessionRepository
import javax.inject.Inject

class CloseMatchUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(
        sessionId: Long,
        finalScore: MatchScore,
        feelingRating: Int?,
        feelingComment: String?
    ): AppResult<Unit> {
        val result = finalScore.calculateResult()
        return sessionRepository.closeSession(sessionId, result, feelingRating, feelingComment)
    }
}

fun MatchScore.calculateResult(): String {
    if (completedSets.isEmpty()) return "ABANDONED"
    val setsA = completedSets.count { it.gamesA > it.gamesB }
    val setsB = completedSets.count { it.gamesB > it.gamesA }
    return when {
        setsA > setsB -> "VICTORY"
        setsB > setsA -> "DEFEAT"
        else -> "DRAW"
    }
}
