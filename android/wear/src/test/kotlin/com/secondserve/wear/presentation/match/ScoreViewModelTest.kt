package com.secondserve.wear.presentation.match

import androidx.lifecycle.SavedStateHandle
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataLayerClient: DataLayerClient

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mockk()
        coEvery { dataLayerClient.sendScoreEvent(any()) } returns AppResult.Success(Unit)
    }

    @AfterEach
    fun tearDown() {
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
        assertEquals(GamePoint.FIFTEEN, vm.container.stateFlow.value.score.currentGamePointsA)
        assertTrue(vm.container.stateFlow.value.canUndo)
    }

    @Test
    fun `undo after recordPoint restores ZERO`() = runTest {
        val vm = createViewModel()
        vm.recordPoint(Player.A)
        vm.undo()
        assertEquals(GamePoint.ZERO, vm.container.stateFlow.value.score.currentGamePointsA)
        assertFalse(vm.container.stateFlow.value.canUndo)
    }

    @Test
    fun `undo when no points does nothing`() = runTest {
        val vm = createViewModel()
        vm.undo()
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
        // Win 6-0 set (6 games × 4 points) to trigger match over with BEST_OF_1
        repeat(24) { vm.recordPoint(Player.A) }
        assertTrue(vm.container.stateFlow.value.score.isMatchOver)

        val scoreBeforeGuard = vm.container.stateFlow.value.score
        vm.recordPoint(Player.A)
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
        assertTrue(vm.container.stateFlow.value.score.isTieBreak)
    }
}
