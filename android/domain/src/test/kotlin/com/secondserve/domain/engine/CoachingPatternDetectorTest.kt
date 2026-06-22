package com.secondserve.domain.engine

import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.MatchStateSnapshot
import com.secondserve.domain.model.SetResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoachingPatternDetectorTest {

    private fun snap(score: MatchScore) = MatchStateSnapshot(score)

    @Test
    fun `detect SUPER_TIEBREAK_ACTIVE when isSuperTieBreak is true`() {
        val score = MatchScore(isSuperTieBreak = true)
        assertEquals(MatchPattern.SUPER_TIEBREAK_ACTIVE, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect TIEBREAK_ACTIVE when isTieBreak is true`() {
        val score = MatchScore(isTieBreak = true)
        assertEquals(MatchPattern.TIEBREAK_ACTIVE, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect SET_WON_DOMINANT when last set won 6-2`() {
        val score = MatchScore(
            completedSets = listOf(SetResult(6, 2)),
            currentSetGamesA = 0,
            currentSetGamesB = 0
        )
        assertEquals(MatchPattern.SET_WON_DOMINANT, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect SET_WON_CLOSE when last set won 7-5`() {
        val score = MatchScore(
            completedSets = listOf(SetResult(7, 5)),
            currentSetGamesA = 0,
            currentSetGamesB = 0
        )
        assertEquals(MatchPattern.SET_WON_CLOSE, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect SET_LOST_DOMINANT when last set lost 2-6`() {
        val score = MatchScore(
            completedSets = listOf(SetResult(2, 6)),
            currentSetGamesA = 0,
            currentSetGamesB = 0
        )
        assertEquals(MatchPattern.SET_LOST_DOMINANT, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect SET_LOST_CLOSE when last set lost 5-7`() {
        val score = MatchScore(
            completedSets = listOf(SetResult(5, 7)),
            currentSetGamesA = 0,
            currentSetGamesB = 0
        )
        assertEquals(MatchPattern.SET_LOST_CLOSE, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect SET_WON_DOMINANT after winning second set 6-2`() {
        val score = MatchScore(
            completedSets = listOf(SetResult(6, 3), SetResult(6, 2)),
            currentSetGamesA = 0,
            currentSetGamesB = 0
        )
        assertEquals(MatchPattern.SET_WON_DOMINANT, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect MATCH_POINT_APPROACHING when 1 set won and leading by 2 games`() {
        val score = MatchScore(
            completedSets = listOf(SetResult(6, 3)),
            currentSetGamesA = 4,
            currentSetGamesB = 2
        )
        assertEquals(MatchPattern.MATCH_POINT_APPROACHING, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect TIEBREAK_APPROACHING when games are 5-5`() {
        val score = MatchScore(currentSetGamesA = 5, currentSetGamesB = 5)
        assertEquals(MatchPattern.TIEBREAK_APPROACHING, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect FIRST_GAME_WON when totalGames is 1 and myGames is 1`() {
        val score = MatchScore(currentSetGamesA = 1, currentSetGamesB = 0)
        assertEquals(MatchPattern.FIRST_GAME_WON, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect FIRST_GAME_LOST when totalGames is 1 and myGames is 0`() {
        val score = MatchScore(currentSetGamesA = 0, currentSetGamesB = 1)
        assertEquals(MatchPattern.FIRST_GAME_LOST, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect DOMINANT_LEAD when ahead by 3 games`() {
        val score = MatchScore(currentSetGamesA = 4, currentSetGamesB = 1)
        assertEquals(MatchPattern.DOMINANT_LEAD, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect DOUBLE_BREAK_ADVANTAGE when player leads by exactly 2 games`() {
        val score = MatchScore(currentSetGamesA = 3, currentSetGamesB = 1)
        assertEquals(MatchPattern.DOUBLE_BREAK_ADVANTAGE, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect EQUAL_MIDSET when tied at 2-2`() {
        val score = MatchScore(currentSetGamesA = 2, currentSetGamesB = 2)
        assertEquals(MatchPattern.EQUAL_MIDSET, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect EQUAL_MIDSET when tied at 3-3`() {
        val score = MatchScore(currentSetGamesA = 3, currentSetGamesB = 3)
        assertEquals(MatchPattern.EQUAL_MIDSET, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect COMEBACK_IN_PROGRESS when lost previous set and now leading`() {
        val score = MatchScore(
            completedSets = listOf(SetResult(3, 6)),
            currentSetGamesA = 3,
            currentSetGamesB = 1
        )
        assertEquals(MatchPattern.COMEBACK_IN_PROGRESS, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect NEUTRAL_TRANSITION for balanced mid-set state`() {
        val score = MatchScore(currentSetGamesA = 2, currentSetGamesB = 3)
        assertEquals(MatchPattern.NEUTRAL_TRANSITION, CoachingPatternDetector.detect(snap(score)))
    }

    @Test
    fun `detect same pattern for same state - determinism check`() {
        val score = MatchScore(currentSetGamesA = 3, currentSetGamesB = 3)
        val snapshot = snap(score)
        val result1 = CoachingPatternDetector.detect(snapshot)
        val result2 = CoachingPatternDetector.detect(snapshot)
        assertEquals(result1, result2)
    }

    @Test
    fun `detect NEUTRAL_TRANSITION when opponent leads by 3 games`() {
        val score = MatchScore(currentSetGamesA = 1, currentSetGamesB = 4)
        assertEquals(MatchPattern.NEUTRAL_TRANSITION, CoachingPatternDetector.detect(snap(score)))
    }
}
