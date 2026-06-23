package com.secondserve.feature.match

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.CoachingSource
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CoachingResolverTest {
    private lateinit var inferenceEngine: InferenceEngine
    private lateinit var vpsEngine: InferenceEngine
    private lateinit var coachingRepository: CoachingRepository
    private lateinit var playerProfileRepository: PlayerProfileRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var resolver: CoachingResolver

    // Score neutre — NEUTRAL_TRANSITION attendu par CoachingPatternDetector
    private val neutralScore = MatchScore()

    @BeforeEach
    fun setup() {
        inferenceEngine = mockk()
        vpsEngine = mockk()
        coachingRepository = mockk()
        playerProfileRepository = mockk()
        sessionRepository = mockk()
        resolver = CoachingResolver(inferenceEngine, vpsEngine, coachingRepository, playerProfileRepository, sessionRepository)

        coEvery { playerProfileRepository.buildMatchContextProfile() } returns MatchContextProfile(
            fftSeries = null, playStyle = null, activeWorkAxes = emptyList(), coachInstructions = emptyList()
        )
        coEvery { sessionRepository.getSessionById(any()) } returns null
        coEvery { coachingRepository.getCachedAdvice(any(), any()) } returns null
        coEvery { vpsEngine.generate(any()) } returns AppResult.Error(RuntimeException("VPS disabled in tests"))
    }

    @Test
    fun `resolve returns null when match is over`() = runTest {
        val score = MatchScore(isMatchOver = true)
        assertNull(resolver.resolve(1L, score))
    }

    @Test
    fun `resolve returns GEMINI result when engine succeeds`() = runTest {
        coEvery { inferenceEngine.generate(any()) } returns AppResult.Success("Conseil Gemini")
        val result = resolver.resolve(1L, neutralScore)
        assertNotNull(result)
        assertEquals(CoachingSource.GEMINI, result!!.source)
        assertEquals("Conseil Gemini", result.text)
    }

    @Test
    fun `resolve returns VPS_MISTRAL when Gemini fails and VPS succeeds`() = runTest {
        coEvery { inferenceEngine.generate(any()) } returns AppResult.Error(RuntimeException("Gemini error"))
        coEvery { vpsEngine.generate(any()) } returns AppResult.Success("Conseil VPS")
        val result = resolver.resolve(1L, neutralScore)
        assertNotNull(result)
        assertEquals(CoachingSource.VPS_MISTRAL, result!!.source)
        assertEquals("Conseil VPS", result.text)
    }

    @Test
    fun `resolve falls back to CACHE when Gemini and VPS fail`() = runTest {
        coEvery { inferenceEngine.generate(any()) } returns AppResult.Error(RuntimeException("LLM error"))
        coEvery { vpsEngine.generate(any()) } returns AppResult.Error(RuntimeException("VPS error"))
        val cached = CoachingCacheEntry(
            matchId = 1L,
            pattern = MatchPattern.NEUTRAL_TRANSITION,
            content = "Conseil cache",
            generatedAt = 0L
        )
        coEvery { coachingRepository.getCachedAdvice(1L, MatchPattern.NEUTRAL_TRANSITION) } returns cached
        val result = resolver.resolve(1L, neutralScore)
        assertEquals(CoachingSource.CACHE, result!!.source)
        assertEquals("Conseil cache", result.text)
    }

    @Test
    fun `resolve falls back to STATIC when Gemini, VPS and cache all fail`() = runTest {
        coEvery { inferenceEngine.generate(any()) } returns AppResult.Error(RuntimeException())
        coEvery { vpsEngine.generate(any()) } returns AppResult.Error(RuntimeException())
        coEvery { coachingRepository.getCachedAdvice(any(), any()) } returns null
        val result = resolver.resolve(1L, neutralScore)
        assertEquals(CoachingSource.STATIC, result!!.source)
        assertTrue(result.text.isNotBlank())
    }
}
