package com.secondserve.wear.presentation.start

import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
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
class StartMatchViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var dataLayerClient: DataLayerClient

    @BeforeEach
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mockk()
    }

    @AfterEach
    fun tearDown() {
        // Orbit intent blocks run on Dispatchers.Default — give them time to post back to Main
        // before resetting, to avoid "no Looper" crashes on background threads.
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StartMatchViewModel(dataLayerClient)

    @Test
    fun `initial state has BEST_OF_3 format and FULL_ADVANTAGE rule`() = runTest {
        val vm = createViewModel()
        val state = vm.container.stateFlow.value
        assertEquals(MatchFormat.BEST_OF_3, state.matchFormat)
        assertEquals(ThirdSetRule.FULL_ADVANTAGE, state.thirdSetRule)
        assertFalse(state.isLoading)
    }

    @Test
    fun `selectFormat updates matchFormat in state`() = runTest {
        val vm = createViewModel()
        vm.selectFormat(MatchFormat.BEST_OF_1)
        val state = vm.container.stateFlow.first { it.matchFormat == MatchFormat.BEST_OF_1 }
        assertEquals(MatchFormat.BEST_OF_1, state.matchFormat)
    }

    @Test
    fun `confirmStart with phone available keeps isLoading true and calls DataLayerClient`() = runTest {
        coEvery { dataLayerClient.sendStartSessionRequest(any(), any()) } returns AppResult.Success(Unit)
        val vm = createViewModel()

        vm.confirmStart()
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.container.stateFlow.value.isLoading)
        coVerify {
            dataLayerClient.sendStartSessionRequest(
                MatchFormat.BEST_OF_3,
                ThirdSetRule.FULL_ADVANTAGE
            )
        }
    }

    @Test
    fun `confirmStart in degraded mode clears isLoading and calls DataLayerClient`() = runTest {
        coEvery { dataLayerClient.sendStartSessionRequest(any(), any()) } returns
            AppResult.Error(Exception("No connected phone node"))
        val vm = createViewModel()

        vm.confirmStart()
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.container.stateFlow.value.isLoading)
        coVerify {
            dataLayerClient.sendStartSessionRequest(
                MatchFormat.BEST_OF_3,
                ThirdSetRule.FULL_ADVANTAGE
            )
        }
    }
}
