package com.secondserve.wear.presentation.match

import androidx.lifecycle.SavedStateHandle
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.ThirdSetRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScoreViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var dataLayerClient: DataLayerClient

    @BeforeEach
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mockk()
        coEvery { dataLayerClient.sendScoreEvent(any()) } returns AppResult.Success(Unit)
    }

    @AfterEach
    fun tearDown() {
        // Drain all pending coroutines (Orbit internals + sendScoreEventAsync) before
        // resetting Main — prevents them from failing on a real dispatcher without a Looper.
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = ScoreViewModel(dataLayerClient, savedStateHandle)

    @Test
    fun `initial state has empty score and canUndo false`() = runTest {
        val vm = createViewModel()
        assertEquals(MatchScore(), vm.container.stateFlow.value.score)
        assertFalse(vm.container.stateFlow.value.canUndo)
    }

    @Test
    fun `recordPoint updates score to FIFTEEN`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A)
        val state = vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }
        assertTrue(state.canUndo)
    }

    @Test
    fun `undo after recordPoint restores ZERO`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A)
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }
        vm.undo()
        val state = vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.ZERO && !it.canUndo }
        assertFalse(state.canUndo)
    }

    @Test
    fun `undo when no points does nothing`() = runTest {
        val vm = createViewModel()
        vm.undo()
        // undo is a no-op: state never changes, stateFlow value stays at initial
        assertEquals(MatchScore(), vm.container.stateFlow.value.score)
        assertFalse(vm.container.stateFlow.value.canUndo)
    }

    @Test
    fun `recordPoint after match over does nothing`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(ScoreViewModel.ARG_MATCH_FORMAT to MatchFormat.BEST_OF_1.name)
            )
        )
        // 24 = 6 games × 4 points at love (only A scores → no deuce possible)
        repeat(24) { vm.recordPoint(Player.A) }
        // Suspend until Orbit has processed all intents and emitted the match-over state
        val matchOverState = vm.container.stateFlow.first { it.score.isMatchOver }
        assertTrue(matchOverState.score.isMatchOver)

        val scoreBeforeGuard = matchOverState.score
        vm.recordPoint(Player.A)  // guard: engine.isMatchOver → no-op, no state emission
        assertEquals(scoreBeforeGuard, vm.container.stateFlow.value.score)
    }

    @Test
    fun `tie-break activates at 6-6 in games`() = runTest {
        val vm = createViewModel()
        // Alternate game wins: A and B each win 6 games → 6-6 → tie-break
        repeat(6) {
            repeat(4) { vm.recordPoint(Player.A) }
            repeat(4) { vm.recordPoint(Player.B) }
        }
        // Suspend until Orbit has processed all intents and emitted the tie-break state
        val tieBrState = vm.container.stateFlow.first { it.score.isTieBreak }
        assertTrue(tieBrState.score.isTieBreak)
    }

    @Test
    fun `cancelMatchOver restores score after wrong match-ending point`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(ScoreViewModel.ARG_MATCH_FORMAT to MatchFormat.BEST_OF_1.name)
            )
        )
        // 24 = 6 games × 4 points at love (only A scores → no deuce possible)
        repeat(24) { vm.recordPoint(Player.A) }
        vm.container.stateFlow.first { it.score.isMatchOver }

        vm.cancelMatchOver()

        val state = vm.container.stateFlow.first { !it.score.isMatchOver }
        assertFalse(state.score.isMatchOver)
        assertTrue(state.canUndo)
    }

    @Test
    fun `super tie-break activates after one set each with SUPER_TIE_BREAK_10 format`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf(
                ScoreViewModel.ARG_MATCH_FORMAT to MatchFormat.BEST_OF_3.name,
                ScoreViewModel.ARG_THIRD_SET_RULE to ThirdSetRule.SUPER_TIE_BREAK_10.name
            ))
        )
        // 24 = 6 games × 4 points at love per set (single scorer per block → no deuce)
        repeat(24) { vm.recordPoint(Player.A) } // set 1 → A wins 6-0
        repeat(24) { vm.recordPoint(Player.B) } // set 2 → B wins 6-0 → super tie-break

        val state = vm.container.stateFlow.first { it.score.isSuperTieBreak }
        assertTrue(state.score.isSuperTieBreak)
    }

    @Test
    fun `undo sends corrected score_event to DataLayer (AC 5)`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A)
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.undo()
        testDispatcher.scheduler.advanceUntilIdle()

        // sendScoreEvent must be called twice: once after recordPoint, once after undo
        coVerify(exactly = 2) { dataLayerClient.sendScoreEvent(any()) }
        // And the second call carries the corrected (restored) score
        coVerify { dataLayerClient.sendScoreEvent(match { it.currentGamePointsA == GamePoint.ZERO }) }
    }

    @Test
    fun `cancelMatchOver is no-op when match is not over`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A)
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.cancelMatchOver()

        // State unchanged — match not over, cancelMatchOver is a no-op
        assertEquals(GamePoint.FIFTEEN, vm.container.stateFlow.value.score.currentGamePointsA)
    }
}
