package com.secondserve.feature.profile

import app.cash.turbine.test
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.AxisSuggestion
import com.secondserve.domain.model.WorkAxis
import com.secondserve.domain.repository.WorkAxisRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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

@OptIn(ExperimentalCoroutinesApi::class)
class WorkAxesViewModelSuggestionsTest {

    private lateinit var repository: WorkAxisRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        every { repository.getWorkAxes() } returns flowOf(emptyList())
        every { repository.observePendingSuggestions() } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = WorkAxesViewModel(repository)

    private fun suggestion(id: Long, title: String) =
        AxisSuggestion(id = id, title = title, generatedAt = 1000L)

    private fun axis(id: Long, title: String) = WorkAxis(id, title, 1000L, 1000L)

    @Test
    fun `init whenNoPendingSuggestions triesToGenerate`() = runTest {
        coEvery { repository.hasPendingSuggestions() } returns false
        coEvery { repository.hasCoachingData() } returns true
        coEvery { repository.generateAndSaveSuggestions() } returns AppResult.Success(Unit)

        createViewModel()

        coVerify { repository.generateAndSaveSuggestions() }
    }

    @Test
    fun `init whenPendingSuggestionsExist doesNotGenerate`() = runTest {
        coEvery { repository.hasPendingSuggestions() } returns true

        createViewModel()

        coVerify(exactly = 0) { repository.generateAndSaveSuggestions() }
    }

    @Test
    fun `init whenGenerationError setsSuggestionsError`() = runTest {
        coEvery { repository.hasPendingSuggestions() } returns false
        coEvery { repository.hasCoachingData() } returns true
        coEvery { repository.generateAndSaveSuggestions() } returns AppResult.Error(RuntimeException("VPS down"))

        val viewModel = createViewModel()

        assertEquals("Suggestions IA indisponibles", viewModel.container.stateFlow.value.suggestionsError)
    }

    @Test
    fun `init whenNoCoachingData doesNotShowSpinnerAndDoesNotGenerate`() = runTest {
        coEvery { repository.hasPendingSuggestions() } returns false
        coEvery { repository.hasCoachingData() } returns false

        val viewModel = createViewModel()

        assertEquals(false, viewModel.container.stateFlow.value.isGeneratingSuggestions)
        assertEquals(null, viewModel.container.stateFlow.value.suggestionsError)
        coVerify(exactly = 0) { repository.generateAndSaveSuggestions() }
    }

    @Test
    fun `acceptSuggestion whenAtMaxCapacity showsError`() = runTest {
        val threeAxes = listOf(axis(1, "A"), axis(2, "B"), axis(3, "C"))
        every { repository.getWorkAxes() } returns flowOf(threeAxes)
        coEvery { repository.hasPendingSuggestions() } returns true

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.acceptSuggestion(1L)
            val effect = awaitItem()
            assertIs<WorkAxesSideEffect.ShowError>(effect)
            assertEquals("Maximum 3 axes actifs atteint", effect.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.acceptSuggestion(any()) }
    }

    @Test
    fun `acceptSuggestion whenSuccess postsSuggestionAcceptedSideEffect`() = runTest {
        coEvery { repository.hasPendingSuggestions() } returns true
        coEvery { repository.acceptSuggestion(5L) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.acceptSuggestion(5L)
            assertEquals(WorkAxesSideEffect.SuggestionAccepted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignoreSuggestion callsRepository`() = runTest {
        coEvery { repository.hasPendingSuggestions() } returns true
        coEvery { repository.ignoreSuggestion(7L) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()
        viewModel.ignoreSuggestion(7L)

        coVerify { repository.ignoreSuggestion(7L) }
    }
}
