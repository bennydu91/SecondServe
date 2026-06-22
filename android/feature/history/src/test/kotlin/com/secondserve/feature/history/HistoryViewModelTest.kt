package com.secondserve.feature.history

import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.SessionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sessionRepository: SessionRepository
    private lateinit var viewModel: HistoryViewModel

    private fun fakeSession(id: Long = 1L) = Session(
        id = id,
        surface = "Clay",
        format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
        status = SessionStatus.COMPLETED,
        result = "VICTORY",
        scoreText = "6-3, 6-2",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading then Content when sessions flow emits`() = runTest {
        val sessions = listOf(fakeSession())
        val sessionsFlow = MutableStateFlow(sessions)
        every { sessionRepository.getAllSessions() } returns sessionsFlow

        viewModel = HistoryViewModel(sessionRepository)

        val state = viewModel.container.stateFlow.first { it is HistoryUiState.Content }
        assertEquals(1, (state as HistoryUiState.Content).sessions.size)
    }

    @Test
    fun `Content state with empty list when no sessions`() = runTest {
        val sessionsFlow = MutableStateFlow(emptyList<Session>())
        every { sessionRepository.getAllSessions() } returns sessionsFlow

        viewModel = HistoryViewModel(sessionRepository)

        val state = viewModel.container.stateFlow.first { it is HistoryUiState.Content }
        assertTrue((state as HistoryUiState.Content).sessions.isEmpty())
    }

    @Test
    fun `sessions list is updated when flow emits new value`() = runTest {
        val sessionsFlow = MutableStateFlow(emptyList<Session>())
        every { sessionRepository.getAllSessions() } returns sessionsFlow

        viewModel = HistoryViewModel(sessionRepository)
        viewModel.container.stateFlow.first { it is HistoryUiState.Content }

        val newSessions = listOf(fakeSession(1L), fakeSession(2L))
        sessionsFlow.value = newSessions

        val state = viewModel.container.stateFlow.first {
            it is HistoryUiState.Content && it.sessions.size == 2
        }
        assertEquals(2, (state as HistoryUiState.Content).sessions.size)
    }

    @Test
    fun `Error state when getAllSessions flow throws exception`() = runTest {
        every { sessionRepository.getAllSessions() } returns flow { throw RuntimeException("DB error") }

        viewModel = HistoryViewModel(sessionRepository)

        val state = viewModel.container.stateFlow.first { it is HistoryUiState.Error }
        assertTrue(state is HistoryUiState.Error)
    }

    @Test
    fun `onSessionClicked emits NavigateToDetail side effect`() = runTest {
        val sessionsFlow = MutableStateFlow(listOf(fakeSession()))
        every { sessionRepository.getAllSessions() } returns sessionsFlow

        viewModel = HistoryViewModel(sessionRepository)
        viewModel.container.stateFlow.first { it is HistoryUiState.Content }

        val sideEffect = coroutineScope {
            val sideEffectDeferred = async {
                viewModel.container.sideEffectFlow.first()
            }
            viewModel.onSessionClicked(42L)
            sideEffectDeferred.await()
        }
        assertTrue(sideEffect is HistorySideEffect.NavigateToDetail)
        assertEquals(42L, (sideEffect as HistorySideEffect.NavigateToDetail).sessionId)

    }
}
