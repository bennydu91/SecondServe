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
        coEvery { dataLayerClient.sendGameOver(any()) } returns AppResult.Success(Unit)
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
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(state.score.isSuperTieBreak)
    }

    @Test
    fun `undo sends corrected score_event to DataLayer (AC 5)`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A)
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.undo()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.ZERO && !it.canUndo }
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

    @Test
    fun `game_over sent automatically when first game ends (odd total = changeover)`() = runTest {
        val vm = createViewModel()
        // A wins game 1 (love game: 4 points A at love → game 1-0, total=1, odd → changeover)
        repeat(4) { vm.recordPoint(Player.A) }
        vm.container.stateFlow.first { it.score.currentSetGamesA == 1 }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `game_over NOT sent when second game ends (even total = no changeover)`() = runTest {
        val vm = createViewModel()
        // A wins game 1 (1-0, total=1, odd → changeover) then game 2 (2-0, total=2, even → no changeover)
        repeat(4) { vm.recordPoint(Player.A) } // game 1 → changeover
        repeat(4) { vm.recordPoint(Player.A) } // game 2 → no changeover
        vm.container.stateFlow.first { it.score.currentSetGamesA == 2 }
        testDispatcher.scheduler.advanceUntilIdle()

        // sendGameOver ne doit être appelé qu'UNE seule fois (jeu 1 uniquement)
        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `game_over carries correct score snapshot (AC 1 — score_snapshot complet)`() = runTest {
        val vm = createViewModel()
        // A wins game 1 at love → changeover → sendGameOver avec score 1-0
        repeat(4) { vm.recordPoint(Player.A) }
        vm.container.stateFlow.first { it.score.currentSetGamesA == 1 }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            dataLayerClient.sendGameOver(match { score ->
                score.currentSetGamesA == 1 && score.currentSetGamesB == 0
            })
        }
    }

    @Test
    fun `UI state updates before game_over is sent (AC 2 — no UI block)`() = runTest {
        val vm = createViewModel()
        // A wins game 1 — l'état UI doit refléter 1-0 immédiatement sans attendre DataLayer
        repeat(4) { vm.recordPoint(Player.A) }
        val state = vm.container.stateFlow.first { it.score.currentSetGamesA == 1 }
        assertEquals(1, state.score.currentSetGamesA)
        assertEquals(0, state.score.currentSetGamesB)
    }

    @Test
    fun `game_over sent when set ends with odd total games (SetWon changeover)`() = runTest {
        val vm = createViewModel()
        // A wins 6-1: jeux 1,3,5,7 (total impair) → changeover → 4 game_over
        repeat(4) { vm.recordPoint(Player.A) } // game 1 (1-0, total=1 → changeover)
        repeat(4) { vm.recordPoint(Player.A) } // game 2 (2-0, total=2 → no changeover)
        repeat(4) { vm.recordPoint(Player.A) } // game 3 (3-0, total=3 → changeover)
        repeat(4) { vm.recordPoint(Player.A) } // game 4 (4-0, total=4 → no changeover)
        repeat(4) { vm.recordPoint(Player.B) } // game 5 (4-1, total=5 → changeover)
        repeat(4) { vm.recordPoint(Player.A) } // game 6 (5-1, total=6 → no changeover)
        repeat(4) { vm.recordPoint(Player.A) } // game 7 → A wins 6-1, SetWon (total=7 → changeover)
        vm.container.stateFlow.first { it.score.completedSets.isNotEmpty() }
        testDispatcher.scheduler.advanceUntilIdle()

        // Jeux avec changeover (total impair): 1, 3, 5, 7 → 4 game_over
        coVerify(exactly = 4) { dataLayerClient.sendGameOver(any()) }
    }
}
