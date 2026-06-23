package com.secondserve.feature.match

import androidx.lifecycle.SavedStateHandle
import com.secondserve.domain.AppResult
import com.secondserve.domain.analysis.AnalysisScheduler
import com.secondserve.domain.event.DataLayerEventBus
import com.secondserve.domain.model.CoachingResult
import com.secondserve.domain.model.CoachingSource
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.SetResult
import com.secondserve.domain.repository.ScoreRepository
import com.secondserve.domain.sync.SyncScheduler
import com.secondserve.domain.usecase.match.CloseMatchUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var scoreRepository: ScoreRepository
    private lateinit var closeMatchUseCase: CloseMatchUseCase
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var analysisScheduler: AnalysisScheduler
    private lateinit var dataLayerEventBus: DataLayerEventBus
    private lateinit var coachingCachePrefetcher: CoachingCachePrefetcher
    private lateinit var coachingResolver: CoachingResolver
    private lateinit var viewModel: MatchViewModel

    private val scoreFlow = MutableStateFlow<MatchScore?>(null)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        scoreRepository = mockk()
        closeMatchUseCase = mockk()
        syncScheduler = mockk(relaxed = true)
        analysisScheduler = mockk(relaxed = true)
        dataLayerEventBus = DataLayerEventBus()
        coachingCachePrefetcher = mockk(relaxed = true)
        coachingResolver = mockk()

        every { scoreRepository.latestScore } returns scoreFlow

        viewModel = MatchViewModel(
            scoreRepository = scoreRepository,
            closeMatchUseCase = closeMatchUseCase,
            syncScheduler = syncScheduler,
            analysisScheduler = analysisScheduler,
            dataLayerEventBus = dataLayerEventBus,
            coachingCachePrefetcher = coachingCachePrefetcher,
            coachingResolver = coachingResolver,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 10L))
        )
    }

    @AfterEach
    fun tearDown() {
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `onCloseRequested sets showCloseDialog to true`() = runTest {
        viewModel.onCloseRequested()
        val state = viewModel.container.stateFlow.first { it.showCloseDialog }
        assertTrue(state.showCloseDialog)
    }

    @Test
    fun `onCloseDialogDismissed sets showCloseDialog to false`() = runTest {
        viewModel.onCloseRequested()
        viewModel.container.stateFlow.first { it.showCloseDialog }
        viewModel.onCloseDialogDismissed()
        val state = viewModel.container.stateFlow.first { !it.showCloseDialog }
        assertFalse(state.showCloseDialog)
    }

    @Test
    fun `onFeelingRatingSelected updates feelingRating`() = runTest {
        viewModel.onFeelingRatingSelected(4)
        val state = viewModel.container.stateFlow.first { it.feelingRating == 4 }
        assertEquals(4, state.feelingRating)
    }

    @Test
    fun `confirmClose emits SessionClosed on success`() = runTest {
        val score = MatchScore(
            completedSets = listOf(SetResult(6, 4), SetResult(6, 3)),
            isMatchOver = true
        )
        scoreFlow.value = score
        coEvery { closeMatchUseCase(any(), any(), any(), any()) } returns AppResult.Success(Unit)

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is MatchSideEffect.SessionClosed }
        }

        viewModel.confirmClose()
        sideEffectDeferred.await()

        assertTrue(sideEffectDeferred.getCompleted() is MatchSideEffect.SessionClosed)
        verify(exactly = 1) { syncScheduler.scheduleImmediate() }
        verify(exactly = 1) { analysisScheduler.schedule(10L) }
    }

    @Test
    fun `confirmClose emits ShowError on repository failure`() = runTest {
        scoreFlow.value = MatchScore()
        coEvery { closeMatchUseCase(any(), any(), any(), any()) } returns AppResult.Error(RuntimeException("DB error"))

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is MatchSideEffect.ShowError }
        }

        viewModel.confirmClose()
        sideEffectDeferred.await()

        val state = viewModel.container.stateFlow.first { !it.isClosing }
        assertFalse(state.isClosing)
        coVerify(exactly = 0) { syncScheduler.scheduleImmediate() }
    }

    @Test
    fun `DataLayerEventBus close request triggers showCloseDialog`() = runTest {
        assertFalse(viewModel.container.stateFlow.value.showCloseDialog)

        dataLayerEventBus.emitCloseRequest()
        val state = viewModel.container.stateFlow.first { it.showCloseDialog }

        assertTrue(state.showCloseDialog)
    }

    @Test
    fun `sessionId is read from SavedStateHandle`() {
        assertEquals(10L, viewModel.sessionId)
    }

    @Test
    fun `initMatch is called with sessionId on ViewModel init`() {
        verify(exactly = 1) { coachingCachePrefetcher.initMatch(10L) }
    }

    @Test
    fun `confirmClose uses session id from SavedStateHandle`() = runTest {
        scoreFlow.value = MatchScore()
        coEvery { closeMatchUseCase(10L, any(), any(), any()) } returns AppResult.Success(Unit)

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is MatchSideEffect.SessionClosed }
        }

        viewModel.confirmClose()
        sideEffectDeferred.await()

        coVerify(exactly = 1) { closeMatchUseCase(10L, any(), any(), any()) }
    }

    @Test
    fun `feelingComment null when blank`() = runTest {
        scoreFlow.value = MatchScore()
        coEvery { closeMatchUseCase(any(), any(), any(), null) } returns AppResult.Success(Unit)

        viewModel.onFeelingCommentChanged("   ")

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is MatchSideEffect.SessionClosed }
        }

        viewModel.confirmClose()
        sideEffectDeferred.await()

        coVerify { closeMatchUseCase(any(), any(), any(), null) }
    }

    @Test
    fun `gameOver event triggers resolve and updates coachingAdvice`() = runTest {
        val score = MatchScore(currentSetGamesA = 1, currentSetGamesB = 0)
        val expected = CoachingResult("Bravo pour ce jeu.", CoachingSource.CACHE)
        coEvery { coachingResolver.resolve(10L, score) } returns expected

        dataLayerEventBus.emitGameOver(score)

        val state = viewModel.container.stateFlow.first { it.coachingAdvice != null }
        assertEquals(expected, state.coachingAdvice)
    }

    @Test
    fun `gameOver with isMatchOver=true does not update coachingAdvice`() = runTest {
        val score = MatchScore(isMatchOver = true)
        coEvery { coachingResolver.resolve(10L, score) } returns null

        dataLayerEventBus.emitGameOver(score)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.container.stateFlow.value.coachingAdvice)
    }
}
