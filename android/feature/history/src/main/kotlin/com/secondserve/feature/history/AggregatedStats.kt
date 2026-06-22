package com.secondserve.feature.history

data class AggregatedStats(
    val totalMatchSessions: Int,
    val totalTrainingSessions: Int,
    val completedMatchSessions: Int,
    val victories: Int,
    val defeats: Int,
    val winRateGlobal: Float?,
    val winRateBySurface: List<SurfaceWinRate>,
    val activeStreak: ActiveStreak?
)

data class SurfaceWinRate(
    val surface: String,
    val matchCount: Int,
    val victories: Int,
    val winRate: Float?
)

sealed class ActiveStreak {
    data class Victories(val count: Int) : ActiveStreak()
    data class Defeats(val count: Int) : ActiveStreak()
}
