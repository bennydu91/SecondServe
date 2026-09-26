package com.secondserve.wear.presentation.match

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.wear.monitoring.WearMonitoringQueue
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
import kotlinx.coroutines.cancel
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
    private lateinit var monitoringQueue: WearMonitoringQueue
    private val createdViewModels = mutableListOf<ScoreViewModel>()

    @BeforeEach
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mockk()
        monitoringQueue = mockk(relaxed = true)
        coEvery { dataLayerClient.sendScoreEvent(any()) } returns AppResult.Success(Unit)
        coEvery { dataLayerClient.sendGameOver(any()) } returns AppResult.Success(Unit)
    }

    @AfterEach
    fun tearDown() {
        // Cancel every ViewModel scope created by the test BEFORE resetMain().
        // Orbit's event loop runs on Dispatchers.Default (real thread pool, not
        // controllable by the test scheduler). Cancelling the scope kills the whole
        // job tree deterministically; the old Thread.sleep(50) was a wall-clock race
        // that lost whenever the Default pool was slow to post back to Main.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) = ScoreViewModel(dataLayerClient, monitoringQueue, savedStateHandle).also {
        createdViewModels.add(it)
    }

    @Test
    fun `initial state has empty score and canUndo false`() = runTest {
        val vm = createViewModel()
        assertEquals(MatchScore(), vm.container.stateFlow.value.score)
        assertFalse(vm.container.stateFlow.value.canUndo)
    }

    @Test
    fun `opponentName is null when no opponent arg is provided`() = runTest {
        val vm = createViewModel()
        assertEquals(null, vm.container.stateFlow.value.opponentName)
    }

    @Test
    fun `opponentName reflects the nav arg when present`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ScoreViewModel.ARG_OPPONENT to "Marceau"))
        )
        assertEquals("Marceau", vm.container.stateFlow.value.opponentName)
    }

    @Test
    fun `opponentName is trimmed`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ScoreViewModel.ARG_OPPONENT to "  Marceau  "))
        )
        assertEquals("Marceau", vm.container.stateFlow.value.opponentName)
    }

    @Test
    fun `opponentName falls back to null when the arg is blank`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ScoreViewModel.ARG_OPPONENT to "   "))
        )
        assertEquals(null, vm.container.stateFlow.value.opponentName)
    }

    @Test
    fun `recordPoint updates score to FIFTEEN`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A).join()
        val state = vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }
        assertTrue(state.canUndo)
    }

    @Test
    fun `undo after recordPoint restores ZERO`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A).join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }
        vm.undo().join()
        val state = vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.ZERO && !it.canUndo }
        assertFalse(state.canUndo)
    }

    @Test
    fun `undo when no points does nothing`() = runTest {
        val vm = createViewModel()
        vm.undo().join()
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
        repeat(24) { vm.recordPoint(Player.A).join() }
        // Suspend until Orbit has processed all intents and emitted the match-over state
        val matchOverState = vm.container.stateFlow.first { it.score.isMatchOver }
        assertTrue(matchOverState.score.isMatchOver)

        val scoreBeforeGuard = matchOverState.score
        vm.recordPoint(Player.A).join()  // guard: engine.isMatchOver → no-op, no state emission
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(scoreBeforeGuard, vm.container.stateFlow.value.score)
    }

    @Test
    fun `tie-break activates at 6-6 in games`() = runTest {
        val vm = createViewModel()
        // Alternate game wins: A and B each win 6 games → 6-6 → tie-break
        repeat(6) {
            repeat(4) { vm.recordPoint(Player.A).join() }
            repeat(4) { vm.recordPoint(Player.B).join() }
        }
        // Suspend until Orbit has processed all intents and emitted the tie-break state
        val tieBrState = vm.container.stateFlow.first { it.score.isTieBreak }
        testDispatcher.scheduler.advanceUntilIdle()
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
        repeat(24) { vm.recordPoint(Player.A).join() }
        vm.container.stateFlow.first { it.score.isMatchOver }

        vm.cancelMatchOver().join()

        val state = vm.container.stateFlow.first { !it.score.isMatchOver }
        testDispatcher.scheduler.advanceUntilIdle()
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
        repeat(24) { vm.recordPoint(Player.A).join() } // set 1 → A wins 6-0
        repeat(24) { vm.recordPoint(Player.B).join() } // set 2 → B wins 6-0 → super tie-break

        val state = vm.container.stateFlow.first { it.score.isSuperTieBreak }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(state.score.isSuperTieBreak)
    }

    @Test
    fun `undo sends corrected score_event to DataLayer (AC 5)`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A).join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.undo().join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.ZERO && !it.canUndo }
        testDispatcher.scheduler.advanceUntilIdle()

        // sendScoreEvent must be called twice: once after recordPoint, once after undo
        coVerify(exactly = 2) { dataLayerClient.sendScoreEvent(any()) }
        // And the second call carries the corrected (restored) score
        coVerify { dataLayerClient.sendScoreEvent(match { it.currentGamePointsA == GamePoint.ZERO }) }
    }

    @Test
    fun `swapLast moves the last point to the other player without losing it`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A).join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.swapLast().join()

        val state = vm.container.stateFlow.first { it.score.currentGamePointsB == GamePoint.FIFTEEN }
        assertEquals(GamePoint.ZERO, state.score.currentGamePointsA)
        assertTrue(state.canUndo)
    }

    @Test
    fun `swapLast twice returns the point to the original scorer`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A).join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.swapLast().join()
        vm.container.stateFlow.first { it.score.currentGamePointsB == GamePoint.FIFTEEN }
        vm.swapLast().join()

        val state = vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }
        assertEquals(GamePoint.ZERO, state.score.currentGamePointsB)
    }

    @Test
    fun `swapLast when no points does nothing`() = runTest {
        val vm = createViewModel()
        vm.swapLast().join()
        assertEquals(MatchScore(), vm.container.stateFlow.value.score)
        assertFalse(vm.container.stateFlow.value.canUndo)
    }

    @Test
    fun `swapLast sends corrected score_event to DataLayer`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A).join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.swapLast().join()
        vm.container.stateFlow.first { it.score.currentGamePointsB == GamePoint.FIFTEEN }
        testDispatcher.scheduler.advanceUntilIdle()

        // sendScoreEvent must be called twice: once after recordPoint, once after swapLast
        coVerify(exactly = 2) { dataLayerClient.sendScoreEvent(any()) }
        coVerify { dataLayerClient.sendScoreEvent(match { it.currentGamePointsB == GamePoint.FIFTEEN }) }
    }

    @Test
    fun `swapLast does nothing when match is over`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(ScoreViewModel.ARG_MATCH_FORMAT to MatchFormat.BEST_OF_1.name)
            )
        )
        repeat(24) { vm.recordPoint(Player.A).join() }
        val matchOverState = vm.container.stateFlow.first { it.score.isMatchOver }

        vm.swapLast().join()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(matchOverState.score, vm.container.stateFlow.value.score)
    }

    @Test
    fun `cancelMatchOver is no-op when match is not over`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A).join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }

        vm.cancelMatchOver().join()

        // State unchanged — match not over, cancelMatchOver is a no-op
        assertEquals(GamePoint.FIFTEEN, vm.container.stateFlow.value.score.currentGamePointsA)
    }

    @Test
    fun `game_over sent automatically when first game ends (odd total = changeover)`() = runTest {
        val vm = createViewModel()
        // A wins game 1 (love game: 4 points A at love → game 1-0, total=1, odd → changeover)
        repeat(4) { vm.recordPoint(Player.A).join() }
        vm.container.stateFlow.first { it.score.currentSetGamesA == 1 }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `game_over NOT sent when second game ends (even total = no changeover)`() = runTest {
        val vm = createViewModel()
        // A wins game 1 (1-0, total=1, odd → changeover) then game 2 (2-0, total=2, even → no changeover)
        repeat(4) { vm.recordPoint(Player.A).join() } // game 1 → changeover
        repeat(4) { vm.recordPoint(Player.A).join() } // game 2 → no changeover
        vm.container.stateFlow.first { it.score.currentSetGamesA == 2 }
        testDispatcher.scheduler.advanceUntilIdle()

        // sendGameOver ne doit être appelé qu'UNE seule fois (jeu 1 uniquement)
        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `game_over carries correct score snapshot (AC 1 — score_snapshot complet)`() = runTest {
        val vm = createViewModel()
        // A wins game 1 at love → changeover → sendGameOver avec score 1-0
        repeat(4) { vm.recordPoint(Player.A).join() }
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
        repeat(4) { vm.recordPoint(Player.A).join() }
        val state = vm.container.stateFlow.first { it.score.currentSetGamesA == 1 }
        assertEquals(1, state.score.currentSetGamesA)
        assertEquals(0, state.score.currentSetGamesB)
    }

    @Test
    fun `sendGameOver NOT called when undo is performed`() = runTest {
        val vm = createViewModel()
        // A wins game 1 (changeover → sendGameOver called once)
        repeat(4) { vm.recordPoint(Player.A).join() }
        vm.container.stateFlow.first { it.score.currentSetGamesA == 1 }
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }

        // Undo a point in the next game (no game over)
        vm.recordPoint(Player.A).join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.FIFTEEN }
        vm.undo().join()
        vm.container.stateFlow.first { it.score.currentGamePointsA == GamePoint.ZERO }
        testDispatcher.scheduler.advanceUntilIdle()

        // sendGameOver must stay at 1 (no additional call during undo)
        coVerify(exactly = 1) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `sendGameOver NOT called when match is over (MatchOver event)`() = runTest {
        val vm = createViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(ScoreViewModel.ARG_MATCH_FORMAT to MatchFormat.BEST_OF_3.name)
            )
        )
        // Set 1: A wins 6-0 (changeovers at games 1,3,5 = 3)
        repeat(6 * 4) { vm.recordPoint(Player.A).join() }
        vm.container.stateFlow.first { it.score.completedSets.size == 1 }
        // Set 2: A wins 6-0 (changeovers at games 7,9,11 = 3); game 12 → MatchOver (no changeover)
        repeat(6 * 4) { vm.recordPoint(Player.A).join() }
        vm.container.stateFlow.first { it.score.isMatchOver }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.container.stateFlow.value.score.isMatchOver)
        // MatchOver → isChangeover() returns false → sendGameOver NOT called for the last game
        val expectedChangeovers = 6
        coVerify(exactly = expectedChangeovers) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `game_over sent when set ends with tie-break 7-6 (SetWon via awardTieBreakGame)`() = runTest {
        val vm = createViewModel()
        // Bring score to 6-6 by alternating games (A wins odd totals, B wins even totals)
        repeat(4) { vm.recordPoint(Player.A).join() } // 1-0, total=1 (odd → changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 1-1, total=2 (even)
        repeat(4) { vm.recordPoint(Player.A).join() } // 2-1, total=3 (odd → changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 2-2, total=4 (even)
        repeat(4) { vm.recordPoint(Player.A).join() } // 3-2, total=5 (odd → changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 3-3, total=6 (even)
        repeat(4) { vm.recordPoint(Player.A).join() } // 4-3, total=7 (odd → changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 4-4, total=8 (even)
        repeat(4) { vm.recordPoint(Player.A).join() } // 5-4, total=9 (odd → changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 5-5, total=10 (even)
        repeat(4) { vm.recordPoint(Player.A).join() } // 6-5, total=11 (odd → changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 6-6 → tie-break, total=12 (even, no changeover)
        // Tie-break: A wins 7-0 → awardSet → SetWon(changeover=true, totalGamesInSet=13 odd)
        repeat(7) { vm.recordPoint(Player.A).join() }
        vm.container.stateFlow.first { it.score.completedSets.size == 1 }
        testDispatcher.scheduler.advanceUntilIdle()

        // 6 changeovers on regular games (totals 1,3,5,7,9,11) + 1 on tie-break = 7
        coVerify(exactly = 7) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `game_over sent when set ends with contested tie-break (A wins 7-5 in tie-break points)`() = runTest {
        val vm = createViewModel()
        // Bring score to 6-6 by alternating games (6 changeovers at odd totals 1,3,5,7,9,11)
        repeat(4) { vm.recordPoint(Player.A).join() } // 1-0, total=1 (changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 1-1, total=2
        repeat(4) { vm.recordPoint(Player.A).join() } // 2-1, total=3 (changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 2-2, total=4
        repeat(4) { vm.recordPoint(Player.A).join() } // 3-2, total=5 (changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 3-3, total=6
        repeat(4) { vm.recordPoint(Player.A).join() } // 4-3, total=7 (changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 4-4, total=8
        repeat(4) { vm.recordPoint(Player.A).join() } // 5-4, total=9 (changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 5-5, total=10
        repeat(4) { vm.recordPoint(Player.A).join() } // 6-5, total=11 (changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // 6-6 → tie-break, total=12
        // Contested tie-break: A and B alternate 5 rounds (5-5), then A wins 2 more → 7-5
        repeat(5) {
            vm.recordPoint(Player.A).join()
            vm.recordPoint(Player.B).join()
        }
        vm.recordPoint(Player.A).join() // A=6, B=5
        vm.recordPoint(Player.A).join() // A=7, B=5 → 2-point lead → awardTieBreakGame → SetWon(changeover=true)
        vm.container.stateFlow.first { it.score.completedSets.size == 1 }
        testDispatcher.scheduler.advanceUntilIdle()

        // Same count as uncontested: 6 (regular games) + 1 (tie-break) = 7
        coVerify(exactly = 7) { dataLayerClient.sendGameOver(any()) }
    }

    @Test
    fun `game_over sent when set ends with odd total games (SetWon changeover)`() = runTest {
        val vm = createViewModel()
        // A wins 6-1: jeux 1,3,5,7 (total impair) → changeover → 4 game_over
        repeat(4) { vm.recordPoint(Player.A).join() } // game 1 (1-0, total=1 → changeover)
        repeat(4) { vm.recordPoint(Player.A).join() } // game 2 (2-0, total=2 → no changeover)
        repeat(4) { vm.recordPoint(Player.A).join() } // game 3 (3-0, total=3 → changeover)
        repeat(4) { vm.recordPoint(Player.A).join() } // game 4 (4-0, total=4 → no changeover)
        repeat(4) { vm.recordPoint(Player.B).join() } // game 5 (4-1, total=5 → changeover)
        repeat(4) { vm.recordPoint(Player.A).join() } // game 6 (5-1, total=6 → no changeover)
        repeat(4) { vm.recordPoint(Player.A).join() } // game 7 → A wins 6-1, SetWon (total=7 → changeover)
        vm.container.stateFlow.first { it.score.completedSets.isNotEmpty() }
        // stateFlow.first{} resumes when reduce{} completes, but Orbit's Default thread may not
        // have yet dispatched viewModelScope.launch{sendGameOver} for the set-winning game.
        testDispatcher.scheduler.advanceUntilIdle()

        // Jeux avec changeover (total impair): 1, 3, 5, 7 → 4 game_over
        coVerify(exactly = 4) { dataLayerClient.sendGameOver(any()) }
    }
}
