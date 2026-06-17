package com.secondserve.domain.model

enum class Player { A, B }

enum class GamePoint { ZERO, FIFTEEN, THIRTY, FORTY, ADVANTAGE }

data class SetResult(val gamesA: Int, val gamesB: Int)

data class MatchScore(
    val completedSets: List<SetResult> = emptyList(),
    val currentSetGamesA: Int = 0,
    val currentSetGamesB: Int = 0,
    val currentGamePointsA: GamePoint = GamePoint.ZERO,
    val currentGamePointsB: GamePoint = GamePoint.ZERO,
    val tieBreakPointsA: Int = 0,
    val tieBreakPointsB: Int = 0,
    val isTieBreak: Boolean = false,
    val isSuperTieBreak: Boolean = false,
    val isMatchOver: Boolean = false,
    val matchWinner: Player? = null
) {
    val isDeuce: Boolean
        get() = !isTieBreak && !isSuperTieBreak &&
                currentGamePointsA == GamePoint.FORTY &&
                currentGamePointsB == GamePoint.FORTY

    val currentSetTotalGames: Int get() = currentSetGamesA + currentSetGamesB
}
