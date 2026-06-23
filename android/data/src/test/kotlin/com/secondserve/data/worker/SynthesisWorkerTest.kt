package com.secondserve.data.worker

import android.content.Context
import androidx.work.WorkerParameters
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingSynthesis
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SynthesisWorkerTest {

    private lateinit var coachingRepository: CoachingRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var playerProfileRepository: PlayerProfileRepository
    private lateinit var vpsMistralEngine: InferenceEngine

    @BeforeEach
    fun setup() {
        coachingRepository = mockk(relaxed = true)
        sessionRepository = mockk()
        playerProfileRepository = mockk()
        vpsMistralEngine = mockk()

        coEvery { coachingRepository.getLatestSynthesis() } returns null
        coEvery { playerProfileRepository.buildMatchContextProfile() } returns MatchContextProfile()
    }

    private fun aSession(id: Long = 1L) = Session(
        id = id,
        surface = "CLAY",
        format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
        status = SessionStatus.COMPLETED,
        result = "VICTORY",
        scoreText = "6/4 6/3",
        createdAt = 1_000_000L,
        updatedAt = 2_000_000L
    )

    private fun makeWorker() = SynthesisWorker(
        context = mockk(relaxed = true),
        params = mockk(relaxed = true),
        coachingRepository = coachingRepository,
        sessionRepository = sessionRepository,
        playerProfileRepository = playerProfileRepository,
        vpsMistralEngine = vpsMistralEngine
    )

    @Test
    fun `runWork returns success without calling VPS when fewer than 3 new sessions`() = runTest {
        coEvery { sessionRepository.countCompletedSince(any()) } returns 2

        val result = makeWorker().runWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
        coVerify(exactly = 0) { coachingRepository.saveSynthesis(any(), any()) }
    }

    @Test
    fun `runWork calls saveSynthesis when 3 or more sessions and VPS succeeds`() = runTest {
        coEvery { sessionRepository.countCompletedSince(any()) } returns 3
        coEvery { sessionRepository.getCompletedSince(any()) } returns listOf(aSession(1L), aSession(2L), aSession(3L))
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("Synthèse globale.")
        coEvery { coachingRepository.saveSynthesis("Synthèse globale.", 3) } returns AppResult.Success(
            CoachingSynthesis(id = 1L, content = "Synthèse globale.", sessionCount = 3, generatedAt = 0L)
        )

        val result = makeWorker().runWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { coachingRepository.saveSynthesis("Synthèse globale.", 3) }
    }

    @Test
    fun `runWork returns retry when VPS returns error`() = runTest {
        coEvery { sessionRepository.countCompletedSince(any()) } returns 5
        coEvery { sessionRepository.getCompletedSince(any()) } returns listOf(aSession(), aSession(2L), aSession(3L), aSession(4L), aSession(5L))
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Error(RuntimeException("VPS down"))

        val result = makeWorker().runWork()

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { coachingRepository.saveSynthesis(any(), any()) }
    }

    @Test
    fun `runWork returns retry when countCompletedSince throws`() = runTest {
        coEvery { sessionRepository.countCompletedSince(any()) } throws RuntimeException("DB error")

        val result = makeWorker().runWork()

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
    }

    @Test
    fun `runWork uses lastSynthesis generatedAt as afterMs threshold`() = runTest {
        val lastSynthesis = CoachingSynthesis(id = 1L, content = "Précédente.", sessionCount = 3, generatedAt = 5_000_000L)
        coEvery { coachingRepository.getLatestSynthesis() } returns lastSynthesis
        coEvery { sessionRepository.countCompletedSince(5_000_000L) } returns 1

        val result = makeWorker().runWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { sessionRepository.countCompletedSince(5_000_000L) }
    }

    @Test
    fun `buildSynthesisPrompt includes sessions surface, format, result and profile data`() = runTest {
        val promptSlot = slot<String>()
        val profile = MatchContextProfile(
            fftSeries = "15/2",
            playStyle = "Attaquant",
            activeWorkAxes = listOf("Service slice")
        )
        val sessions = listOf(aSession(1L), aSession(2L), aSession(3L))
        coEvery { sessionRepository.countCompletedSince(any()) } returns 3
        coEvery { sessionRepository.getCompletedSince(any()) } returns sessions
        coEvery { playerProfileRepository.buildMatchContextProfile() } returns profile
        coEvery { vpsMistralEngine.generate(capture(promptSlot)) } returns AppResult.Success("OK")
        coEvery { coachingRepository.saveSynthesis(any(), any()) } returns AppResult.Success(
            CoachingSynthesis(id = 1L, content = "OK", sessionCount = 3, generatedAt = 0L)
        )

        makeWorker().runWork()

        val prompt = promptSlot.captured
        assertTrue(prompt.contains("CLAY"), "prompt doit contenir la surface")
        assertTrue(prompt.contains("BEST_OF_3"), "prompt doit contenir le format")
        assertTrue(prompt.contains("VICTORY"), "prompt doit contenir le résultat")
        assertTrue(prompt.contains("15/2"), "prompt doit contenir le classement FFT")
        assertTrue(prompt.contains("Attaquant"), "prompt doit contenir le style de jeu")
        assertTrue(prompt.contains("Service slice"), "prompt doit contenir les axes de travail")
    }
}
