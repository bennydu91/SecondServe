package com.secondserve.feature.history

import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.model.ThirdSetRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatsComputerTest {

    private fun fakeSession(
        id: Long,
        sessionType: SessionType = SessionType.MATCH,
        status: SessionStatus = SessionStatus.COMPLETED,
        result: String? = "VICTORY",
        surface: String = "Clay",
        createdAt: Long = System.currentTimeMillis()
    ) = Session(
        id = id,
        surface = surface,
        format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
        sessionType = sessionType,
        status = status,
        result = result,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun `empty sessions - zero counts, null win rate, null streak`() {
        val stats = computeStats(emptyList())
        assertEquals(0, stats.totalMatchSessions)
        assertEquals(0, stats.totalTrainingSessions)
        assertEquals(0, stats.completedMatchSessions)
        assertNull(stats.winRateGlobal)
        assertNull(stats.activeStreak)
        assertTrue(stats.winRateBySurface.isEmpty())
    }

    @Test
    fun `3 victories on Clay - win rate 100 percent, surface shown, streak Victories(3)`() {
        val sessions = listOf(
            fakeSession(1, result = "VICTORY", surface = "Clay", createdAt = 3000L),
            fakeSession(2, result = "VICTORY", surface = "Clay", createdAt = 2000L),
            fakeSession(3, result = "VICTORY", surface = "Clay", createdAt = 1000L)
        )
        val stats = computeStats(sessions)
        assertEquals(1.0f, stats.winRateGlobal)
        assertEquals(1, stats.winRateBySurface.size)
        assertEquals(1.0f, stats.winRateBySurface[0].winRate)
        assertTrue(stats.activeStreak is ActiveStreak.Victories)
        assertEquals(3, (stats.activeStreak as ActiveStreak.Victories).count)
    }

    @Test
    fun `2 matches on surface - winRate null (Donnees insuffisantes)`() {
        val sessions = listOf(
            fakeSession(1, result = "VICTORY", surface = "Hard"),
            fakeSession(2, result = "DEFEAT", surface = "Hard")
        )
        val stats = computeStats(sessions)
        assertEquals(1, stats.winRateBySurface.size)
        assertNull(stats.winRateBySurface[0].winRate)
    }

    @Test
    fun `streak breaks correctly - 1 defeat after 2 victories, streak is Defeats(1)`() {
        val sessions = listOf(
            fakeSession(1, result = "DEFEAT", createdAt = 3000L),
            fakeSession(2, result = "VICTORY", createdAt = 2000L),
            fakeSession(3, result = "VICTORY", createdAt = 1000L)
        )
        val stats = computeStats(sessions)
        assertTrue(stats.activeStreak is ActiveStreak.Defeats)
        assertEquals(1, (stats.activeStreak as ActiveStreak.Defeats).count)
    }

    @Test
    fun `TRAINING sessions counted in totalTrainingSessions but not in win rate`() {
        val sessions = listOf(
            fakeSession(1, sessionType = SessionType.TRAINING, result = null),
            fakeSession(2, result = "VICTORY")
        )
        val stats = computeStats(sessions)
        assertEquals(1, stats.totalTrainingSessions)
        assertEquals(1, stats.totalMatchSessions)
        assertEquals(1, stats.completedMatchSessions)
    }

    @Test
    fun `DRAW and ABANDONED not counted in win rate`() {
        val sessions = listOf(
            fakeSession(1, result = "DRAW"),
            fakeSession(2, result = "ABANDONED"),
            fakeSession(3, result = "VICTORY")
        )
        val stats = computeStats(sessions)
        assertEquals(1, stats.completedMatchSessions)
        assertEquals(1.0f, stats.winRateGlobal)
    }

    @Test
    fun `win rate 50 percent with 2 victories and 2 defeats on same surface`() {
        val sessions = listOf(
            fakeSession(1, result = "VICTORY", surface = "Hard", createdAt = 4000L),
            fakeSession(2, result = "DEFEAT", surface = "Hard", createdAt = 3000L),
            fakeSession(3, result = "VICTORY", surface = "Hard", createdAt = 2000L),
            fakeSession(4, result = "DEFEAT", surface = "Hard", createdAt = 1000L)
        )
        val stats = computeStats(sessions)
        assertEquals(0.5f, stats.winRateGlobal)
        assertEquals(0.5f, stats.winRateBySurface[0].winRate)
    }

    @Test
    fun `INTERRUPTED sessions not counted as completed`() {
        val sessions = listOf(
            fakeSession(1, status = SessionStatus.INTERRUPTED, result = "VICTORY")
        )
        val stats = computeStats(sessions)
        assertEquals(0, stats.completedMatchSessions)
        assertNull(stats.winRateGlobal)
    }

    @Test
    fun `surfaces sorted by match count descending`() {
        val sessions = listOf(
            fakeSession(1, result = "VICTORY", surface = "Clay", createdAt = 5000L),
            fakeSession(2, result = "VICTORY", surface = "Clay", createdAt = 4000L),
            fakeSession(3, result = "VICTORY", surface = "Clay", createdAt = 3000L),
            fakeSession(4, result = "VICTORY", surface = "Hard", createdAt = 2000L),
            fakeSession(5, result = "DEFEAT", surface = "Hard", createdAt = 1000L)
        )
        val stats = computeStats(sessions)
        assertEquals(2, stats.winRateBySurface.size)
        assertEquals("Clay", stats.winRateBySurface[0].surface)
        assertEquals("Hard", stats.winRateBySurface[1].surface)
    }

    @Test
    fun `INTERRUPTED session with DEFEAT result breaks a victory streak`() {
        val sessions = listOf(
            fakeSession(1, status = SessionStatus.INTERRUPTED, result = "DEFEAT", createdAt = 3000L),
            fakeSession(2, result = "VICTORY", createdAt = 2000L),
            fakeSession(3, result = "VICTORY", createdAt = 1000L)
        )
        val stats = computeStats(sessions)
        assertTrue(stats.activeStreak is ActiveStreak.Defeats)
        assertEquals(1, (stats.activeStreak as ActiveStreak.Defeats).count)
        assertEquals(2, stats.completedMatchSessions)
        assertEquals(1.0f, stats.winRateGlobal)
    }
}
