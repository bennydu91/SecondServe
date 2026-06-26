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
import kotlinx.coroutines.test.advanceTimeBy
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
        // viewModelScope.launch{} (timeout job) runs on Main/testDispatcher but the nested
        // intent{} inside posts to Dispatchers.Default — same race as ScoreViewModelTest.
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
        vm.container.stateFlow.first { it.isLoading }

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
        vm.container.stateFlow.first { it.isLoading }
        vm.container.stateFlow.first { !it.isLoading }

        assertFalse(vm.container.stateFlow.value.isLoading)
        coVerify {
            dataLayerClient.sendStartSessionRequest(
                MatchFormat.BEST_OF_3,
                ThirdSetRule.FULL_ADVANTAGE
            )
        }
    }

    @Test
    fun `confirmStart times out after PHONE_RESPONSE_TIMEOUT_MS and falls back to local`() = runTest {
        coEvery { dataLayerClient.sendStartSessionRequest(any(), any()) } returns AppResult.Success(Unit)
        val vm = createViewModel()

        vm.confirmStart()
        vm.container.stateFlow.first { it.isLoading }

        advanceTimeBy(StartMatchViewModel.PHONE_RESPONSE_TIMEOUT_MS + 1)

        // Orbit dispatches intent{} on Dispatchers.Default (real thread pool). Suspending on
        // stateFlow.first{} idles the test scheduler and lets that thread emit the state change.
        val finalState = vm.container.stateFlow.first { !it.isLoading }
        assertFalse(finalState.isLoading)
    }
}
