package com.secondserve.wear.presentation.start

import androidx.lifecycle.viewModelScope
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private val createdViewModels = mutableListOf<StartMatchViewModel>()

    @BeforeEach
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mockk()
    }

    @AfterEach
    fun tearDown() {
        // Cancel every ViewModel scope created by the test BEFORE resetMain().
        // Orbit's event loop runs on Dispatchers.Default (real thread pool, not
        // controllable by the test scheduler); nested viewModelScope.launch{} jobs
        // (e.g. the phone-response timeout) survive the test body. Cancelling the
        // scope kills the whole job tree deterministically. Thread.sleep() was a
        // flaky wall-clock race; advanceUntilIdle() was insufficient because it can
        // only drain what is already queued on the test scheduler.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StartMatchViewModel(dataLayerClient).also {
        createdViewModels.add(it)
    }

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
    fun `initial state has no surface selected and canStart is false`() = runTest {
        val vm = createViewModel()
        val state = vm.container.stateFlow.value
        assertEquals(null, state.surface)
        assertFalse(state.canStart)
    }

    @Test
    fun `selectSurface updates surface and enables canStart`() = runTest {
        val vm = createViewModel()
        vm.selectSurface("CLAY")
        val state = vm.container.stateFlow.first { it.surface == "CLAY" }
        assertEquals("CLAY", state.surface)
        assertTrue(state.canStart)
    }

    @Test
    fun `confirmStart without surface does nothing`() = runTest {
        val vm = createViewModel()

        vm.confirmStart()

        assertFalse(vm.container.stateFlow.value.isLoading)
        coVerify(exactly = 0) { dataLayerClient.sendStartSessionRequest(any(), any(), any()) }
    }

    @Test
    fun `confirmStart with phone available keeps isLoading true and calls DataLayerClient with surface`() = runTest {
        coEvery { dataLayerClient.sendStartSessionRequest(any(), any(), any()) } returns AppResult.Success(Unit)
        val vm = createViewModel()
        vm.selectSurface("CLAY").join()

        vm.confirmStart().join()
        // intent() returns the Job Orbit dispatched to its event loop (Dispatchers.Default,
        // a real thread pool). join() waits for the WHOLE intent body — including the state
        // reductions and the mock call — instead of racing a StateFlow predicate against the
        // Default thread (conflation can drop the transient isLoading=true value).

        assertTrue(vm.container.stateFlow.value.isLoading)
        coVerify {
            dataLayerClient.sendStartSessionRequest(
                MatchFormat.BEST_OF_3,
                ThirdSetRule.FULL_ADVANTAGE,
                "CLAY"
            )
        }
    }

    @Test
    fun `confirmStart in degraded mode clears isLoading and calls DataLayerClient`() = runTest {
        coEvery { dataLayerClient.sendStartSessionRequest(any(), any(), any()) } returns
            AppResult.Error(Exception("No connected phone node"))
        val vm = createViewModel()
        vm.selectSurface("HARD").join()

        vm.confirmStart().join()
        // Same: join() makes the transient isLoading=true->false sequence deterministic;
        // stateFlow.first{ it.isLoading } could hang forever if the intent already finished
        // before the collector subscribed (StateFlow conflation drops the intermediate true).

        assertFalse(vm.container.stateFlow.value.isLoading)
        coVerify {
            dataLayerClient.sendStartSessionRequest(
                MatchFormat.BEST_OF_3,
                ThirdSetRule.FULL_ADVANTAGE,
                "HARD"
            )
        }
    }

    @Test
    fun `confirmStart times out after PHONE_RESPONSE_TIMEOUT_MS and falls back to local`() = runTest {
        coEvery { dataLayerClient.sendStartSessionRequest(any(), any(), any()) } returns AppResult.Success(Unit)
        val vm = createViewModel()
        vm.selectSurface("CLAY").join()

        vm.confirmStart().join()
        // The intent body completed: isLoading=true was reduced and the timeout job
        // (viewModelScope.launch { delay(TIMEOUT) … }) is pending on the test scheduler.
        assertTrue(vm.container.stateFlow.value.isLoading)

        advanceTimeBy(StartMatchViewModel.PHONE_RESPONSE_TIMEOUT_MS + 1)
        runCurrent()
        // After the timeout fires, the nested intent{} is dispatched onto Orbit's event
        // loop (Dispatchers.Default). Side effects are channel-based (not conflated), so
        // first{} cannot miss the StartLocal emission even if it happened before subscribe.
        vm.container.sideEffectFlow.first { it is StartMatchSideEffect.StartLocal }

        assertFalse(vm.container.stateFlow.value.isLoading)
    }
}
