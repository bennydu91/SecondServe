package com.secondserve.feature.history

import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType

internal fun computeStats(sessions: List<Session>): AggregatedStats {
    val allMatch = sessions.filter { it.sessionType == SessionType.MATCH }
    val allTraining = sessions.filter { it.sessionType == SessionType.TRAINING }

    val scored = allMatch.filter {
        it.status == SessionStatus.COMPLETED && it.result in listOf("VICTORY", "DEFEAT")
    }
    val victories = scored.count { it.result == "VICTORY" }
    val defeats = scored.count { it.result == "DEFEAT" }

    val winRateGlobal = if (scored.isEmpty()) null
                        else victories.toFloat() / scored.size

    val bySurface = scored
        .groupBy { it.surface }
        .map { (surface, list) ->
            val v = list.count { it.result == "VICTORY" }
            SurfaceWinRate(
                surface = surface,
                matchCount = list.size,
                victories = v,
                winRate = if (list.size >= 3) v.toFloat() / list.size else null
            )
        }
        .sortedByDescending { it.matchCount }

    val sortedScored = scored.sortedByDescending { it.createdAt }
    val streak = computeStreak(sortedScored)

    return AggregatedStats(
        totalMatchSessions = allMatch.size,
        totalTrainingSessions = allTraining.size,
        completedMatchSessions = scored.size,
        victories = victories,
        defeats = defeats,
        winRateGlobal = winRateGlobal,
        winRateBySurface = bySurface,
        activeStreak = streak
    )
}

private fun computeStreak(sortedSessions: List<Session>): ActiveStreak? {
    if (sortedSessions.isEmpty()) return null
    val firstResult = sortedSessions.first().result ?: return null
    val count = sortedSessions.takeWhile { it.result == firstResult }.size
    return when (firstResult) {
        "VICTORY" -> ActiveStreak.Victories(count)
        "DEFEAT" -> ActiveStreak.Defeats(count)
        else -> null
    }
}
