package com.secondserve.domain.engine

import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.MatchStateSnapshot
import kotlin.math.abs

object CoachingPatternDetector {

    fun detect(snapshot: MatchStateSnapshot): MatchPattern {
        val score = snapshot.score

        if (score.isSuperTieBreak) return MatchPattern.SUPER_TIEBREAK_ACTIVE
        if (score.isTieBreak) return MatchPattern.TIEBREAK_ACTIVE

        val myGames = score.currentSetGamesA
        val oppGames = score.currentSetGamesB
        val totalGames = myGames + oppGames
        val lastSet = score.completedSets.lastOrNull()

        if (lastSet != null && totalGames == 0) {
            val mySetGames = lastSet.gamesA
            val oppSetGames = lastSet.gamesB
            val diff = abs(mySetGames - oppSetGames)

            val setsWon = score.completedSets.count { it.gamesA > it.gamesB }
            if (setsWon >= 2) return MatchPattern.MATCH_POINT_APPROACHING

            return when {
                mySetGames > oppSetGames && diff >= 3 -> MatchPattern.SET_WON_DOMINANT
                mySetGames > oppSetGames             -> MatchPattern.SET_WON_CLOSE
                diff >= 3                            -> MatchPattern.SET_LOST_DOMINANT
                else                                 -> MatchPattern.SET_LOST_CLOSE
            }
        }

        if (myGames >= 5 && oppGames >= 5) return MatchPattern.TIEBREAK_APPROACHING

        if (totalGames == 1) {
            return if (myGames == 1) MatchPattern.FIRST_GAME_WON else MatchPattern.FIRST_GAME_LOST
        }

        if (myGames - oppGames >= 3) return MatchPattern.DOMINANT_LEAD
        if (oppGames - myGames >= 3) return MatchPattern.DOUBLE_BREAK_ADVANTAGE

        if (myGames == oppGames && myGames in 2..4) return MatchPattern.EQUAL_MIDSET

        val previousSetLost = score.completedSets.any { it.gamesA < it.gamesB }
        val currentlyLeading = myGames > oppGames
        if (previousSetLost && currentlyLeading) return MatchPattern.COMEBACK_IN_PROGRESS

        return MatchPattern.NEUTRAL_TRANSITION
    }
}
