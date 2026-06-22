package com.secondserve.feature.history

import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.SessionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class StatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var sessionRepository: SessionRepository

    private fun fakeSession(id: Long = 1L) = Session(
        id = id,
        surface = "Clay",
        format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
        sessionType = SessionType.MATCH,
        status = SessionStatus.COMPLETED,
        result = "VICTORY",
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
    fun `initial Loading then Content when flow emits`() = runTest {
        val sessionsFlow = MutableStateFlow(listOf(fakeSession(1)))
        every { sessionRepository.getAllSessions() } returns sessionsFlow

        val vm = StatsViewModel(sessionRepository)
        val state = vm.container.stateFlow.first { it is StatsUiState.Content }
        assertTrue(state is StatsUiState.Content)
    }

    @Test
    fun `Content state with empty list when no sessions`() = runTest {
        val sessionsFlow = MutableStateFlow(emptyList<Session>())
        every { sessionRepository.getAllSessions() } returns sessionsFlow

        val vm = StatsViewModel(sessionRepository)
        val state = vm.container.stateFlow.first { it is StatsUiState.Content }
        val content = state as StatsUiState.Content
        assertEquals(0, content.stats.totalMatchSessions)
    }

    @Test
    fun `Error state when flow throws`() = runTest {
        every { sessionRepository.getAllSessions() } returns flow { throw RuntimeException("DB") }

        val vm = StatsViewModel(sessionRepository)
        val state = vm.container.stateFlow.first { it is StatsUiState.Error }
        assertTrue(state is StatsUiState.Error)
        assertEquals("DB", (state as StatsUiState.Error).message)
    }

    @Test
    fun `stats recalculate automatically when flow emits new list`() = runTest {
        val sessionsFlow = MutableStateFlow(emptyList<Session>())
        every { sessionRepository.getAllSessions() } returns sessionsFlow

        val vm = StatsViewModel(sessionRepository)
        vm.container.stateFlow.first { it is StatsUiState.Content }

        sessionsFlow.value = listOf(fakeSession(1))
        val state = vm.container.stateFlow.first {
            it is StatsUiState.Content && it.stats.totalMatchSessions == 1
        }
        assertEquals(1, (state as StatsUiState.Content).stats.totalMatchSessions)
    }
}
