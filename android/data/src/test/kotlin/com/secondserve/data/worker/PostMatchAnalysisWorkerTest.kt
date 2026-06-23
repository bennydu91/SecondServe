package com.secondserve.data.worker

import android.content.Context
import androidx.work.WorkerParameters
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingAnalysis
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

class PostMatchAnalysisWorkerTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var playerProfileRepository: PlayerProfileRepository
    private lateinit var coachingRepository: CoachingRepository
    private lateinit var vpsMistralEngine: InferenceEngine

    @BeforeEach
    fun setup() {
        sessionRepository = mockk()
        playerProfileRepository = mockk()
        coachingRepository = mockk(relaxed = true)
        vpsMistralEngine = mockk()

        coEvery { playerProfileRepository.buildMatchContextProfile() } returns MatchContextProfile()
        coEvery { sessionRepository.getPointSummaryForSession(any()) } returns Pair(10, 8)
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

    private fun makeWorker() = PostMatchAnalysisWorker(
        context = mockk(relaxed = true),
        params = mockk(relaxed = true),
        sessionRepository = sessionRepository,
        playerProfileRepository = playerProfileRepository,
        coachingRepository = coachingRepository,
        vpsMistralEngine = vpsMistralEngine
    )

    @Test
    fun `doWork saves analysis and returns success on VPS success`() = runTest {
        coEvery { sessionRepository.getSessionById(1L) } returns aSession()
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("Bonne analyse.")
        coEvery { coachingRepository.saveAnalysis(1L, "Bonne analyse.") } returns AppResult.Success(
            CoachingAnalysis(id = 1L, sessionId = 1L, content = "Bonne analyse.", generatedAt = 0L)
        )

        val result = makeWorker().runWork(1L)

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { coachingRepository.saveAnalysis(1L, "Bonne analyse.") }
    }

    @Test
    fun `doWork returns retry on VPS error`() = runTest {
        coEvery { sessionRepository.getSessionById(1L) } returns aSession()
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Error(RuntimeException("VPS down"))

        val result = makeWorker().runWork(1L)

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { coachingRepository.saveAnalysis(any(), any()) }
    }

    @Test
    fun `doWork returns failure when session not found`() = runTest {
        coEvery { sessionRepository.getSessionById(99L) } returns null

        val result = makeWorker().runWork(99L)

        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
        coVerify(exactly = 0) { coachingRepository.saveAnalysis(any(), any()) }
    }

    @Test
    fun `doWork returns failure when sessionId is missing`() = runTest {
        val result = makeWorker().runWork(-1L)

        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { sessionRepository.getSessionById(any()) }
    }

    @Test
    fun `doWork returns retry when saveAnalysis fails`() = runTest {
        coEvery { sessionRepository.getSessionById(1L) } returns aSession()
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("Bonne analyse.")
        coEvery { coachingRepository.saveAnalysis(any(), any()) } returns AppResult.Error(RuntimeException("DB error"))

        val result = makeWorker().runWork(1L)

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns failure when buildMatchContextProfile throws`() = runTest {
        coEvery { sessionRepository.getSessionById(1L) } returns aSession()
        coEvery { playerProfileRepository.buildMatchContextProfile() } throws RuntimeException("Profile unavailable")

        val result = makeWorker().runWork(1L)

        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
    }

    @Test
    fun `doWork returns failure when AppResult Loading returned`() = runTest {
        coEvery { sessionRepository.getSessionById(1L) } returns aSession()
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Loading

        val result = makeWorker().runWork(1L)

        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `buildPrompt includes all required AC1 fields`() = runTest {
        val promptSlot = slot<String>()
        val profile = MatchContextProfile(
            fftSeries = "15/1",
            playStyle = "Défensif",
            activeWorkAxes = listOf("Revers lifté"),
            coachInstructions = listOf("Attaquer plus tôt")
        )
        coEvery { sessionRepository.getSessionById(1L) } returns aSession()
        coEvery { playerProfileRepository.buildMatchContextProfile() } returns profile
        coEvery { sessionRepository.getPointSummaryForSession(1L) } returns Pair(15, 12)
        coEvery { vpsMistralEngine.generate(capture(promptSlot)) } returns AppResult.Success("Analyse.")
        coEvery { coachingRepository.saveAnalysis(any(), any()) } returns AppResult.Success(
            CoachingAnalysis(id = 1L, sessionId = 1L, content = "Analyse.", generatedAt = 0L)
        )

        makeWorker().runWork(1L)

        val prompt = promptSlot.captured
        assertTrue(prompt.contains("CLAY"), "prompt doit contenir la surface")
        assertTrue(prompt.contains("BEST_OF_3"), "prompt doit contenir le format")
        assertTrue(prompt.contains("6/4 6/3"), "prompt doit contenir le score")
        assertTrue(prompt.contains("VICTORY"), "prompt doit contenir le résultat")
        assertTrue(prompt.contains("15"), "prompt doit contenir les points gagnés")
        assertTrue(prompt.contains("12"), "prompt doit contenir les points perdus")
        assertTrue(prompt.contains("15/1"), "prompt doit contenir le classement FFT")
        assertTrue(prompt.contains("Défensif"), "prompt doit contenir le style de jeu")
        assertTrue(prompt.contains("Revers lifté"), "prompt doit contenir les axes de travail")
        assertTrue(prompt.contains("Attaquer plus tôt"), "prompt doit contenir les instructions coaching")
    }
}
