package com.secondserve.domain.engine

import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SetResult
import com.secondserve.domain.model.ThirdSetRule

sealed class EngineEvent {
    abstract val score: MatchScore

    data class PointScored(override val score: MatchScore) : EngineEvent()

    data class GameWon(
        override val score: MatchScore,
        val winner: Player,
        val changeover: Boolean
    ) : EngineEvent()

    data class SetWon(
        override val score: MatchScore,
        val winner: Player,
        val changeover: Boolean
    ) : EngineEvent()

    data class MatchOver(
        override val score: MatchScore,
        val winner: Player
    ) : EngineEvent()
}

class TennisScoreEngine(val format: SessionFormat) {

    private var state: MatchScore = MatchScore()
    private val history: ArrayDeque<MatchScore> = ArrayDeque()

    val currentScore: MatchScore get() = state

    fun recordPoint(scorer: Player): EngineEvent {
        check(!state.isMatchOver) { "Cannot record point: match is over" }
        history.addLast(state.copy())
        return when {
            state.isSuperTieBreak -> processSuperTieBreakPoint(scorer)
            state.isTieBreak -> processTieBreakPoint(scorer)
            else -> processRegularPoint(scorer)
        }
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        state = history.removeLast()
        return true
    }

    // ─── Regular game ──────────────────────────────────────────────────────────

    private fun processRegularPoint(scorer: Player): EngineEvent {
        val pA = state.currentGamePointsA
        val pB = state.currentGamePointsB

        if (pA == GamePoint.ADVANTAGE || pB == GamePoint.ADVANTAGE) {
            return if ((scorer == Player.A && pA == GamePoint.ADVANTAGE) ||
                       (scorer == Player.B && pB == GamePoint.ADVANTAGE)) {
                awardGame(scorer)
            } else {
                state = state.copy(
                    currentGamePointsA = GamePoint.FORTY,
                    currentGamePointsB = GamePoint.FORTY
                )
                EngineEvent.PointScored(state)
            }
        }

        if (state.isDeuce) {
            state = if (scorer == Player.A) {
                state.copy(currentGamePointsA = GamePoint.ADVANTAGE)
            } else {
                state.copy(currentGamePointsB = GamePoint.ADVANTAGE)
            }
            return EngineEvent.PointScored(state)
        }

        val currentPoints = if (scorer == Player.A) pA else pB

        val nextPoints = when (currentPoints) {
            GamePoint.ZERO -> GamePoint.FIFTEEN
            GamePoint.FIFTEEN -> GamePoint.THIRTY
            GamePoint.THIRTY -> GamePoint.FORTY
            GamePoint.FORTY -> return awardGame(scorer)
            GamePoint.ADVANTAGE -> return awardGame(scorer)
        }

        state = if (scorer == Player.A) {
            state.copy(currentGamePointsA = nextPoints)
        } else {
            state.copy(currentGamePointsB = nextPoints)
        }

        return EngineEvent.PointScored(state)
    }

    // ─── Tie-break ─────────────────────────────────────────────────────────────

    private fun processTieBreakPoint(scorer: Player): EngineEvent {
        val newA = state.tieBreakPointsA + (if (scorer == Player.A) 1 else 0)
        val newB = state.tieBreakPointsB + (if (scorer == Player.B) 1 else 0)
        state = state.copy(tieBreakPointsA = newA, tieBreakPointsB = newB)

        val winner = when {
            newA >= 7 && newA - newB >= 2 -> Player.A
            newB >= 7 && newB - newA >= 2 -> Player.B
            else -> null
        }
        return if (winner != null) awardTieBreakGame(winner) else EngineEvent.PointScored(state)
    }

    // ─── Super tie-break ───────────────────────────────────────────────────────

    private fun processSuperTieBreakPoint(scorer: Player): EngineEvent {
        val newA = state.tieBreakPointsA + (if (scorer == Player.A) 1 else 0)
        val newB = state.tieBreakPointsB + (if (scorer == Player.B) 1 else 0)
        state = state.copy(tieBreakPointsA = newA, tieBreakPointsB = newB)

        val winner = when {
            newA >= 10 && newA - newB >= 2 -> Player.A
            newB >= 10 && newB - newA >= 2 -> Player.B
            else -> null
        }
        if (winner != null) {
            val superTbResult = SetResult(newA, newB)
            state = state.copy(
                completedSets = state.completedSets + superTbResult,
                isMatchOver = true,
                matchWinner = winner,
                isSuperTieBreak = false
            )
            return EngineEvent.MatchOver(state, winner)
        }
        return EngineEvent.PointScored(state)
    }

    // ─── Game / Set helpers ────────────────────────────────────────────────────

    private fun awardGame(winner: Player): EngineEvent {
        val newGamesA = state.currentSetGamesA + (if (winner == Player.A) 1 else 0)
        val newGamesB = state.currentSetGamesB + (if (winner == Player.B) 1 else 0)
        val totalGames = newGamesA + newGamesB
        val changeover = totalGames % 2 == 1

        state = state.copy(
            currentSetGamesA = newGamesA,
            currentSetGamesB = newGamesB,
            currentGamePointsA = GamePoint.ZERO,
            currentGamePointsB = GamePoint.ZERO,
            isTieBreak = false,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0
        )

        if (newGamesA == 6 && newGamesB == 6) {
            return startTieBreak(changeover, winner)
        }

        return checkSetWon(winner, changeover)
    }

    private fun awardTieBreakGame(winner: Player): EngineEvent {
        val newGamesA = state.currentSetGamesA + (if (winner == Player.A) 1 else 0)
        val newGamesB = state.currentSetGamesB + (if (winner == Player.B) 1 else 0)
        val totalGames = newGamesA + newGamesB
        val changeover = totalGames % 2 == 1

        state = state.copy(
            currentSetGamesA = newGamesA,
            currentSetGamesB = newGamesB,
            isTieBreak = false,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0
        )
        // Winning the tie-break always wins the set (7-6 or 4-3 in SHORT_DECISIVE_SET)
        return awardSet(winner)
    }

    private fun startTieBreak(changeover: Boolean, lastGameWinner: Player): EngineEvent {
        state = state.copy(
            isTieBreak = true,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            currentGamePointsA = GamePoint.ZERO,
            currentGamePointsB = GamePoint.ZERO
        )
        return EngineEvent.GameWon(state, lastGameWinner, changeover)
    }

    private fun checkSetWon(winner: Player, gameChangeover: Boolean): EngineEvent {
        val gA = state.currentSetGamesA
        val gB = state.currentSetGamesB

        if (format.thirdSetRule == ThirdSetRule.SHORT_DECISIVE_SET && isFinalSet() &&
            gA == 3 && gB == 3) {
            return startTieBreak(gameChangeover, winner)
        }

        val setWinner = when {
            gA >= 6 && gA - gB >= 2 -> Player.A
            gB >= 6 && gB - gA >= 2 -> Player.B
            format.thirdSetRule == ThirdSetRule.SHORT_DECISIVE_SET &&
                isFinalSet() && gA >= 4 && gA - gB >= 2 -> Player.A
            format.thirdSetRule == ThirdSetRule.SHORT_DECISIVE_SET &&
                isFinalSet() && gB >= 4 && gB - gA >= 2 -> Player.B
            else -> null
        }

        if (setWinner == null) {
            return EngineEvent.GameWon(state, winner, gameChangeover)
        }

        return awardSet(setWinner)
    }

    private fun awardSet(winner: Player): EngineEvent {
        val totalGamesInSet = state.currentSetGamesA + state.currentSetGamesB
        val setChangeover = totalGamesInSet % 2 == 1

        val completedSet = SetResult(state.currentSetGamesA, state.currentSetGamesB)
        val newCompletedSets = state.completedSets + completedSet

        val setsWonA = newCompletedSets.count { it.gamesA > it.gamesB }
        val setsWonB = newCompletedSets.count { it.gamesB > it.gamesA }
        val setsToWin = if (format.matchFormat == MatchFormat.BEST_OF_1) 1 else 2

        state = state.copy(
            completedSets = newCompletedSets,
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentGamePointsA = GamePoint.ZERO,
            currentGamePointsB = GamePoint.ZERO,
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false
        )

        if (setsWonA >= setsToWin || setsWonB >= setsToWin) {
            state = state.copy(isMatchOver = true, matchWinner = winner)
            return EngineEvent.MatchOver(state, winner)
        }

        if (format.matchFormat == MatchFormat.BEST_OF_3 &&
            format.thirdSetRule == ThirdSetRule.SUPER_TIE_BREAK_10 &&
            setsWonA == 1 && setsWonB == 1) {
            state = state.copy(isSuperTieBreak = true, tieBreakPointsA = 0, tieBreakPointsB = 0)
            return EngineEvent.SetWon(state, winner, setChangeover)
        }

        return EngineEvent.SetWon(state, winner, setChangeover)
    }

    private fun isFinalSet(): Boolean {
        if (format.matchFormat == MatchFormat.BEST_OF_1) return true
        val setsWonA = state.completedSets.count { it.gamesA > it.gamesB }
        val setsWonB = state.completedSets.count { it.gamesB > it.gamesA }
        return setsWonA == 1 && setsWonB == 1
    }
}
