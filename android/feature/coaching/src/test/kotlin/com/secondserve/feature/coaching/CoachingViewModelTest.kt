package com.secondserve.feature.coaching

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var coachingRepository: CoachingRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var playerProfileRepository: PlayerProfileRepository
    private lateinit var vpsMistralEngine: InferenceEngine
    private lateinit var viewModel: CoachingViewModel

    private val synthesisFlow = MutableStateFlow<com.secondserve.domain.model.CoachingSynthesis?>(null)
    private val analysesFlow = MutableStateFlow<List<com.secondserve.domain.model.CoachingAnalysis>>(emptyList())

    private fun aSession() = Session(
        id = 1L,
        surface = "CLAY",
        format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
        status = SessionStatus.COMPLETED,
        sessionType = SessionType.MATCH,
        result = "VICTORY",
        scoreText = "6-3 7-5",
        createdAt = 1_000_000L,
        updatedAt = 2_000_000L
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coachingRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        playerProfileRepository = mockk(relaxed = true)
        vpsMistralEngine = mockk(relaxed = true)

        coEvery { coachingRepository.observeLatestSynthesis() } returns synthesisFlow
        coEvery { coachingRepository.observeAllAnalyses() } returns analysesFlow
        coEvery { coachingRepository.getLatestSynthesis() } returns null
        coEvery { sessionRepository.getCompletedSince(any()) } returns listOf(aSession())
        coEvery { playerProfileRepository.buildMatchContextProfile() } returns MatchContextProfile()

        viewModel = CoachingViewModel(
            coachingRepository = coachingRepository,
            sessionRepository = sessionRepository,
            playerProfileRepository = playerProfileRepository,
            vpsMistralEngine = vpsMistralEngine
        )
    }

    @AfterEach
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `generateNow sets timeout error message when engine throws TimeoutCancellationException`() = runTest(testDispatcher) {
        // withTimeout(0L) lance TimeoutCancellationException de façon synchrone (sans suspension)
        // ce qui simule la réception de l'exception par le catch (e: TimeoutCancellationException) du ViewModel
        coEvery { vpsMistralEngine.generate(any()) } coAnswers {
            withTimeout(0L) { AppResult.Success("") }
        }

        viewModel.generateNow()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.container.stateFlow.first { !it.synthesisInProgress }
        assertEquals("Délai dépassé — réessayez", state.error)
    }

    @Test
    fun `generateNow clears synthesisInProgress after TimeoutCancellationException`() = runTest(testDispatcher) {
        coEvery { vpsMistralEngine.generate(any()) } coAnswers {
            withTimeout(0L) { AppResult.Success("") }
        }

        viewModel.generateNow()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.container.stateFlow.first { !it.synthesisInProgress }
        assertFalse(state.synthesisInProgress)
    }

    @Test
    fun `generateNow succeeds and clears error on successful engine response`() = runTest(testDispatcher) {
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("Synthèse générée.")
        coEvery { coachingRepository.saveSynthesis(any(), any()) } returns AppResult.Success(
            com.secondserve.domain.model.CoachingSynthesis(
                id = 1L,
                content = "Synthèse générée.",
                sessionCount = 1,
                generatedAt = System.currentTimeMillis()
            )
        )

        viewModel.generateNow()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.container.stateFlow.first { !it.synthesisInProgress }
        assertNull(state.error)
        assertFalse(state.synthesisInProgress)
    }
}
