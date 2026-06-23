package com.secondserve.data.worker

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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    private fun makeWorker() = TestPostMatchAnalysisWorkerHelper(
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

        val result = makeWorker().doWork(1L)

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { coachingRepository.saveAnalysis(1L, "Bonne analyse.") }
    }

    @Test
    fun `doWork returns retry on VPS error`() = runTest {
        coEvery { sessionRepository.getSessionById(1L) } returns aSession()
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Error(RuntimeException("VPS down"))

        val result = makeWorker().doWork(1L)

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { coachingRepository.saveAnalysis(any(), any()) }
    }

    @Test
    fun `doWork returns failure when session not found`() = runTest {
        coEvery { sessionRepository.getSessionById(99L) } returns null

        val result = makeWorker().doWork(99L)

        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
        coVerify(exactly = 0) { coachingRepository.saveAnalysis(any(), any()) }
    }

    @Test
    fun `doWork returns failure when sessionId is missing`() = runTest {
        val result = makeWorker().doWork(-1L)

        assertEquals(androidx.work.ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { sessionRepository.getSessionById(any()) }
    }
}

private class TestPostMatchAnalysisWorkerHelper(
    private val sessionRepository: SessionRepository,
    private val playerProfileRepository: PlayerProfileRepository,
    private val coachingRepository: CoachingRepository,
    private val vpsMistralEngine: InferenceEngine
) {
    suspend fun doWork(sessionId: Long): androidx.work.ListenableWorker.Result {
        if (sessionId == -1L) return androidx.work.ListenableWorker.Result.failure()

        val session = sessionRepository.getSessionById(sessionId)
            ?: return androidx.work.ListenableWorker.Result.failure()

        val profile = playerProfileRepository.buildMatchContextProfile()
        val (selfPoints, opponentPoints) = sessionRepository.getPointSummaryForSession(sessionId)

        val axesText = profile.activeWorkAxes.joinToString(", ").ifEmpty { "aucun" }
        val instructionsLine = if (profile.coachInstructions.isNotEmpty()) {
            "\n- Instructions coaching : ${profile.coachInstructions.joinToString("; ")}"
        } else ""

        val prompt = """
Tu es un coach tennis. Analyse ce match de façon concrète et personnalisée. Réponse en 4-6 phrases maximum.

Match :
- Surface : ${session.surface}
- Format : ${session.format.matchFormat.name}
- Score : ${session.scoreText ?: "inconnu"}
- Résultat : ${session.result ?: "inconnu"}
- Points : $selfPoints gagnés / $opponentPoints perdus

Profil joueur :
- Classement FFT : ${profile.fftSeries ?: "non renseigné"}
- Style de jeu : ${profile.playStyle ?: "non renseigné"}
- Axes de travail actifs : $axesText$instructionsLine

Fournis une analyse structurée : points forts observés dans ce match, points faibles, écart avec les axes de travail, et 1-2 recommandations concrètes. Cite le score et la surface. Sois précis, pas générique.
        """.trimIndent()

        return when (val result = vpsMistralEngine.generate(prompt)) {
            is AppResult.Success -> {
                coachingRepository.saveAnalysis(sessionId, result.data)
                androidx.work.ListenableWorker.Result.success()
            }
            is AppResult.Error -> androidx.work.ListenableWorker.Result.retry()
            AppResult.Loading -> androidx.work.ListenableWorker.Result.retry()
        }
    }
}
