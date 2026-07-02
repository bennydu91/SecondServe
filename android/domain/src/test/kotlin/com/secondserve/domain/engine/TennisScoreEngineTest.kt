package com.secondserve.domain.engine

import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.ThirdSetRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TennisScoreEngineTest {

    private val bestOf1Format = SessionFormat(MatchFormat.BEST_OF_1)
    private val bestOf3Format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE)
    private val superTbFormat = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.SUPER_TIE_BREAK_10)
    private val shortSetFormat = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.SHORT_DECISIVE_SET)

    private fun TennisScoreEngine.winPoints(scorer: Player, count: Int) {
        repeat(count) { recordPoint(scorer) }
    }

    private fun TennisScoreEngine.winGame(scorer: Player): EngineEvent {
        repeat(3) { recordPoint(scorer) }
        return recordPoint(scorer)
    }

    private fun TennisScoreEngine.winGames(scorer: Player, count: Int) {
        repeat(count) { winGame(scorer) }
    }

    private fun TennisScoreEngine.winSet6_0(scorer: Player): EngineEvent {
        var event: EngineEvent = EngineEvent.PointScored(currentScore)
        repeat(6) { event = winGame(scorer) }
        return event
    }

    // Reaches 6-6 tie-break in the current set by alternating game wins (A: 1-6, B: 1-6)
    private fun TennisScoreEngine.reachSixSixTieBreak() {
        repeat(5) {
            winGame(Player.A)
            winGame(Player.B)
        }
        winGame(Player.A)  // 6-5
        winGame(Player.B)  // 6-6 → tie-break triggered
    }

    @Nested
    inner class RegularGameRules {

        @Test
        fun `points progressed correctly from 0 to game`() {
            val engine = TennisScoreEngine(bestOf1Format)
            assertEquals(GamePoint.ZERO, engine.currentScore.currentGamePointsA)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.FIFTEEN, engine.currentScore.currentGamePointsA)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.THIRTY, engine.currentScore.currentGamePointsA)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.FORTY, engine.currentScore.currentGamePointsA)
        }

        @Test
        fun `winning game at 40-0 returns GameWon event`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winPoints(Player.A, 3)
            val event = engine.recordPoint(Player.A)
            assertTrue(event is EngineEvent.GameWon)
            assertEquals(Player.A, (event as EngineEvent.GameWon).winner)
            assertEquals(1, event.score.currentSetGamesA)
            assertEquals(0, event.score.currentSetGamesB)
        }

        @Test
        fun `deuce and advantage cycle`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winPoints(Player.A, 3)
            engine.winPoints(Player.B, 3)
            assertTrue(engine.currentScore.isDeuce)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.ADVANTAGE, engine.currentScore.currentGamePointsA)
            assertEquals(GamePoint.FORTY, engine.currentScore.currentGamePointsB)
            engine.recordPoint(Player.B)
            assertTrue(engine.currentScore.isDeuce)
            engine.recordPoint(Player.B)
            assertEquals(GamePoint.ADVANTAGE, engine.currentScore.currentGamePointsB)
            val event = engine.recordPoint(Player.B)
            assertTrue(event is EngineEvent.GameWon)
            assertEquals(Player.B, (event as EngineEvent.GameWon).winner)
        }

        @Test
        fun `multiple deuce-advantage cycles in one game`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winPoints(Player.A, 3)
            engine.winPoints(Player.B, 3)
            // Cycle 1: A gets advantage, B takes it back to deuce
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.ADVANTAGE, engine.currentScore.currentGamePointsA)
            engine.recordPoint(Player.B)
            assertTrue(engine.currentScore.isDeuce)
            // Cycle 2: B gets advantage, A takes it back to deuce
            engine.recordPoint(Player.B)
            assertEquals(GamePoint.ADVANTAGE, engine.currentScore.currentGamePointsB)
            engine.recordPoint(Player.A)
            assertTrue(engine.currentScore.isDeuce)
            // Cycle 3: A wins
            engine.recordPoint(Player.A)
            val event = engine.recordPoint(Player.A)
            assertTrue(event is EngineEvent.GameWon)
            assertEquals(Player.A, (event as EngineEvent.GameWon).winner)
        }
    }

    @Nested
    inner class ChangeoversDetection {

        @Test
        fun `changeover when total games is odd`() {
            val engine = TennisScoreEngine(bestOf3Format)
            val event = engine.winGame(Player.A)
            assertTrue(event is EngineEvent.GameWon)
            assertTrue((event as EngineEvent.GameWon).changeover)
        }

        @Test
        fun `no changeover when total games is even`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGame(Player.A)
            val event = engine.winGame(Player.B)
            assertTrue(event is EngineEvent.GameWon)
            assertFalse((event as EngineEvent.GameWon).changeover)
        }

        @Test
        fun `SetWon changeover false when set total is even (6-0)`() {
            val engine = TennisScoreEngine(bestOf3Format)
            val event = engine.winSet6_0(Player.A)  // 6-0 → total=6 (pair)
            assertTrue(event is EngineEvent.SetWon)
            assertFalse((event as EngineEvent.SetWon).changeover)
        }

        @Test
        fun `changeover always true after tie-break (13 total games)`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.reachSixSixTieBreak()
            assertTrue(engine.currentScore.isTieBreak)
            // Win tie-break 7-0 (6 points then the winning point)
            engine.winPoints(Player.A, 6)
            val event = engine.recordPoint(Player.A)  // 7-0 → SetWon
            assertTrue(event is EngineEvent.SetWon)
            assertTrue((event as EngineEvent.SetWon).changeover)
            val score = engine.currentScore
            assertFalse(score.isTieBreak)
            assertEquals(1, score.completedSets.size)
            assertEquals(7, score.completedSets[0].gamesA)
            assertEquals(6, score.completedSets[0].gamesB)
        }
    }

    @Nested
    inner class SetsAndGames {

        @Test
        fun `win set 6-0`() {
            val engine = TennisScoreEngine(bestOf3Format)
            val event = engine.winSet6_0(Player.A)
            assertTrue(event is EngineEvent.SetWon)
            assertEquals(Player.A, (event as EngineEvent.SetWon).winner)
            assertEquals(1, engine.currentScore.completedSets.size)
            assertEquals(6, engine.currentScore.completedSets[0].gamesA)
            assertEquals(0, engine.currentScore.completedSets[0].gamesB)
        }

        @Test
        fun `win set 6-3`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGames(Player.A, 3)  // 3-0
            engine.winGames(Player.B, 3)  // 3-3
            engine.winGames(Player.A, 2)  // 5-3
            val event = engine.winGame(Player.A)  // 6-3 → SetWon
            assertTrue(event is EngineEvent.SetWon)
            assertEquals(Player.A, (event as EngineEvent.SetWon).winner)
            assertEquals(6, engine.currentScore.completedSets[0].gamesA)
            assertEquals(3, engine.currentScore.completedSets[0].gamesB)
        }

        @Test
        fun `win set 6-4`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winGames(Player.A, 4)  // 4-0
            engine.winGames(Player.B, 4)  // 4-4
            engine.winGames(Player.A, 1)  // 5-4
            val event = engine.winGame(Player.A)  // 6-4 → SetWon
            assertTrue(event is EngineEvent.SetWon)
            assertEquals(Player.A, (event as EngineEvent.SetWon).winner)
            assertEquals(6, engine.currentScore.completedSets[0].gamesA)
            assertEquals(4, engine.currentScore.completedSets[0].gamesB)
        }
    }

    @Nested
    inner class TieBreak {

        @Test
        fun `tie-break triggered at 6-6`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.reachSixSixTieBreak()
            assertTrue(engine.currentScore.isTieBreak)
        }

        @Test
        fun `tie-break won at 7 with 2-point lead`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.reachSixSixTieBreak()
            engine.winPoints(Player.A, 7)  // 7-0 → set won
            val score = engine.currentScore
            assertFalse(score.isTieBreak)
            assertEquals(1, score.completedSets.size)
            assertEquals(7, score.completedSets[0].gamesA)
            assertEquals(6, score.completedSets[0].gamesB)
        }

        @Test
        fun `tie-break requires 2-point lead (7-6 not enough, 8-6 enough)`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.reachSixSixTieBreak()
            engine.winPoints(Player.A, 6)
            engine.winPoints(Player.B, 6)  // 6-6 in tie-break
            engine.winPoints(Player.A, 1)  // A: 7, B: 6 — not enough
            assertTrue(engine.currentScore.isTieBreak)
            engine.winPoints(Player.A, 1)  // A: 8, B: 6 — won
            assertFalse(engine.currentScore.isTieBreak)
        }
    }

    @Nested
    inner class SuperTieBreak {

        @Test
        fun `super tie-break triggered at 1-1 sets in SUPER_TIE_BREAK_10 format`() {
            val engine = TennisScoreEngine(superTbFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            assertTrue(engine.currentScore.isSuperTieBreak)
            assertFalse(engine.currentScore.isTieBreak)
        }

        @Test
        fun `super tie-break won at 10 with 2-point lead`() {
            val engine = TennisScoreEngine(superTbFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winPoints(Player.A, 10)
            val score = engine.currentScore
            assertTrue(score.isMatchOver)
            assertEquals(Player.A, score.matchWinner)
        }

        @Test
        fun `super tie-break requires 2-point lead (10-9 not enough)`() {
            val engine = TennisScoreEngine(superTbFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winPoints(Player.A, 9)
            engine.winPoints(Player.B, 9)
            engine.winPoints(Player.A, 1)  // 10-9, not enough
            assertTrue(engine.currentScore.isSuperTieBreak)
            assertFalse(engine.currentScore.isMatchOver)
            engine.winPoints(Player.A, 1)  // 11-9, won
            assertTrue(engine.currentScore.isMatchOver)
        }

        @Test
        fun `super tie-break result appears in completedSets`() {
            val engine = TennisScoreEngine(superTbFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winPoints(Player.A, 10)
            val score = engine.currentScore
            assertTrue(score.isMatchOver)
            assertEquals(3, score.completedSets.size)
            assertEquals(10, score.completedSets[2].gamesA)
            assertEquals(0, score.completedSets[2].gamesB)
        }
    }

    @Nested
    inner class UndoTests {

        @Test
        fun `undo restores previous state`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.recordPoint(Player.A)
            assertEquals(GamePoint.FIFTEEN, engine.currentScore.currentGamePointsA)
            val result = engine.undo()
            assertTrue(result)
            assertEquals(GamePoint.ZERO, engine.currentScore.currentGamePointsA)
        }

        @Test
        fun `undo returns false when history empty`() {
            val engine = TennisScoreEngine(bestOf1Format)
            assertFalse(engine.undo())
        }

        @Test
        fun `undo works across game boundary`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winPoints(Player.A, 3)
            engine.recordPoint(Player.A)
            assertEquals(1, engine.currentScore.currentSetGamesA)
            engine.undo()
            assertEquals(0, engine.currentScore.currentSetGamesA)
            assertEquals(GamePoint.FORTY, engine.currentScore.currentGamePointsA)
        }

        @Test
        fun `undo after match over is possible`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winSet6_0(Player.A)
            assertTrue(engine.currentScore.isMatchOver)
            engine.undo()
            assertFalse(engine.currentScore.isMatchOver)
        }

        @Test
        fun `undo works across set boundary`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            assertEquals(1, engine.currentScore.completedSets.size)
            engine.undo()
            // Restored to state just before winning point of 6th game: 5-0 games, A at FORTY
            assertEquals(0, engine.currentScore.completedSets.size)
            assertEquals(5, engine.currentScore.currentSetGamesA)
            assertEquals(GamePoint.FORTY, engine.currentScore.currentGamePointsA)
        }

        @Test
        fun `undo supports multiple levels`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.recordPoint(Player.A)
            engine.recordPoint(Player.A)
            engine.recordPoint(Player.B)
            engine.undo()
            assertEquals(GamePoint.ZERO, engine.currentScore.currentGamePointsB)
            assertEquals(GamePoint.THIRTY, engine.currentScore.currentGamePointsA)
            engine.undo()
            assertEquals(GamePoint.FIFTEEN, engine.currentScore.currentGamePointsA)
            engine.undo()
            assertEquals(GamePoint.ZERO, engine.currentScore.currentGamePointsA)
            assertFalse(engine.undo())
        }
    }

    @Nested
    inner class MatchFormats {

        @Test
        fun `BEST_OF_1 match over after one set`() {
            val engine = TennisScoreEngine(bestOf1Format)
            val event = engine.winSet6_0(Player.A)
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.A, (event as EngineEvent.MatchOver).winner)
        }

        @Test
        fun `BEST_OF_3 requires 2 sets to win`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            assertFalse(engine.currentScore.isMatchOver)
            engine.winSet6_0(Player.A)
            assertTrue(engine.currentScore.isMatchOver)
        }

        @Test
        fun `BEST_OF_3 can reach 3 sets`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            assertFalse(engine.currentScore.isMatchOver)
            assertEquals(2, engine.currentScore.completedSets.size)
        }

        @Test
        fun `cannot record point after match over`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winSet6_0(Player.A)
            assertThrows<IllegalStateException> { engine.recordPoint(Player.A) }
        }

        @Test
        fun `BEST_OF_3 FULL_ADVANTAGE win in 2 sets`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            val event = engine.winSet6_0(Player.A)
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.A, (event as EngineEvent.MatchOver).winner)
            assertEquals(2, engine.currentScore.completedSets.size)
        }

        @Test
        fun `BEST_OF_3 Player B wins in 2 sets`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.B)
            assertFalse(engine.currentScore.isMatchOver)
            val event = engine.winSet6_0(Player.B)
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.B, (event as EngineEvent.MatchOver).winner)
            assertEquals(2, engine.currentScore.completedSets.size)
        }

        @Test
        fun `BEST_OF_3 FULL_ADVANTAGE win in 3 sets`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            val event = engine.winSet6_0(Player.A)
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.A, (event as EngineEvent.MatchOver).winner)
        }
    }

    @Nested
    inner class ShortDecisiveSet {

        @Test
        fun `SHORT_DECISIVE_SET win at 4-0 in third set`() {
            val engine = TennisScoreEngine(shortSetFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winGames(Player.A, 3)  // 3-0
            val event = engine.winGame(Player.A)  // 4-0 → MatchOver
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.A, (event as EngineEvent.MatchOver).winner)
        }

        @Test
        fun `SHORT_DECISIVE_SET win at 4-2 in third set`() {
            val engine = TennisScoreEngine(shortSetFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winGames(Player.A, 2)  // 2-0
            engine.winGames(Player.B, 2)  // 2-2
            engine.winGames(Player.A, 1)  // 3-2
            val event = engine.winGame(Player.A)  // 4-2 → MatchOver
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.A, (event as EngineEvent.MatchOver).winner)
        }

        @Test
        fun `SHORT_DECISIVE_SET tie-break at 3-3`() {
            val engine = TennisScoreEngine(shortSetFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winGames(Player.A, 3)  // 3-0
            engine.winGames(Player.B, 3)  // 3-3 → tie-break
            assertTrue(engine.currentScore.isTieBreak)
        }

        @Test
        fun `SHORT_DECISIVE_SET tie-break at 3-3 then win match`() {
            val engine = TennisScoreEngine(shortSetFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winGames(Player.A, 3)  // 3-0
            engine.winGames(Player.B, 3)  // 3-3 → tie-break
            assertTrue(engine.currentScore.isTieBreak)
            engine.winPoints(Player.A, 7)  // 7-0 → tie-break won → set won → match over
            assertTrue(engine.currentScore.isMatchOver)
            assertEquals(Player.A, engine.currentScore.matchWinner)
        }

        @Test
        fun `SHORT_DECISIVE_SET Player B wins third set 4-0`() {
            val engine = TennisScoreEngine(shortSetFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winGames(Player.B, 3)  // 0-3
            val event = engine.winGame(Player.B)  // 0-4 → MatchOver
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.B, (event as EngineEvent.MatchOver).winner)
        }

        @Test
        fun `SHORT_DECISIVE_SET Player B wins third set 4-2`() {
            val engine = TennisScoreEngine(shortSetFormat)
            engine.winSet6_0(Player.A)
            engine.winSet6_0(Player.B)
            engine.winGames(Player.A, 2)  // 2-0
            engine.winGames(Player.B, 2)  // 2-2
            engine.winGames(Player.B, 1)  // 2-3
            val event = engine.winGame(Player.B)  // 2-4 → MatchOver
            assertTrue(event is EngineEvent.MatchOver)
            assertEquals(Player.B, (event as EngineEvent.MatchOver).winner)
        }
    }

    @Nested
    inner class IllegalStateChecks {

        @Test
        fun `check throws after match is over`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winSet6_0(Player.A)
            assertThrows<IllegalStateException> { engine.recordPoint(Player.A) }
        }

        @Test
        fun `check throws with correct message`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.winSet6_0(Player.A)
            val ex = assertThrows<IllegalStateException> { engine.recordPoint(Player.B) }
            assertEquals("Cannot record point: match is over", ex.message)
        }
    }

    @Nested
    inner class PointLogTracking {

        @Test
        fun `recordPoint appends scorer to currentSetPointLog`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.recordPoint(Player.A)
            engine.recordPoint(Player.B)
            engine.recordPoint(Player.A)
            assertEquals(listOf(Player.A, Player.B, Player.A), engine.currentScore.currentSetPointLog)
        }

        @Test
        fun `currentSetPointLog resets when the set is won`() {
            val engine = TennisScoreEngine(bestOf3Format)
            engine.winSet6_0(Player.A)
            assertTrue(engine.currentScore.currentSetPointLog.isEmpty())
        }

        @Test
        fun `currentSetPointLog is restored by undo`() {
            val engine = TennisScoreEngine(bestOf1Format)
            engine.recordPoint(Player.A)
            engine.recordPoint(Player.B)
            engine.undo()
            assertEquals(listOf(Player.A), engine.currentScore.currentSetPointLog)
        }
    }
}
