package com.secondserve.feature.profile

import app.cash.turbine.test
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.WorkAxis
import com.secondserve.domain.repository.WorkAxisRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkAxesViewModelTest {

    private lateinit var repository: WorkAxisRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        coEvery { repository.getWorkAxes() } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = WorkAxesViewModel(repository)

    private fun axis(id: Long, title: String) = WorkAxis(id, title, 1000L, 1000L)

    @Test
    fun `createWorkAxis when at max capacity emits ShowError`() = runTest {
        val threeAxes = listOf(axis(1, "A"), axis(2, "B"), axis(3, "C"))
        coEvery { repository.getWorkAxes() } returns flowOf(threeAxes)

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.createWorkAxis("D")
            val effect = awaitItem()
            assertIs<WorkAxesSideEffect.ShowError>(effect)
            assertEquals("Maximum 3 axes actifs atteint", effect.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.createWorkAxis(any()) }
    }

    @Test
    fun `createWorkAxis with blank title emits ShowError`() = runTest {
        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.createWorkAxis("   ")
            val effect = awaitItem()
            assertIs<WorkAxesSideEffect.ShowError>(effect)
            assertTrue(effect.message.contains("vide"))
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.createWorkAxis(any()) }
    }

    @Test
    fun `createWorkAxis with valid title emits WorkAxisCreated`() = runTest {
        coEvery { repository.createWorkAxis("Revers") } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.createWorkAxis("Revers")
            assertEquals(WorkAxesSideEffect.WorkAxisCreated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteWorkAxis emits WorkAxisDeleted on success`() = runTest {
        coEvery { repository.deleteWorkAxis(1L) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.deleteWorkAxis(1L)
            assertEquals(WorkAxesSideEffect.WorkAxisDeleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateWorkAxis with blank title emits ShowError`() = runTest {
        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.updateWorkAxis(1L, "")
            val effect = awaitItem()
            assertIs<WorkAxesSideEffect.ShowError>(effect)
            assertTrue(effect.message.contains("vide"))
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.updateWorkAxis(any(), any()) }
    }

    @Test
    fun `updateWorkAxis with valid title emits WorkAxisUpdated`() = runTest {
        coEvery { repository.updateWorkAxis(1L, "Nouveau") } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.updateWorkAxis(1L, "Nouveau")
            assertEquals(WorkAxesSideEffect.WorkAxisUpdated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isAtMaxCapacity is true when 3 work axes exist`() = runTest {
        val threeAxes = listOf(axis(1, "A"), axis(2, "B"), axis(3, "C"))
        coEvery { repository.getWorkAxes() } returns flowOf(threeAxes)

        val viewModel = createViewModel()

        assertTrue(viewModel.container.stateFlow.value.isAtMaxCapacity)
        assertEquals(3, viewModel.container.stateFlow.value.workAxes.size)
    }
}
