package com.secondserve.feature.history

import androidx.lifecycle.SavedStateHandle
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.CoachingRepository
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SessionDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sessionRepository: SessionRepository
    private lateinit var coachingRepository: CoachingRepository

    private fun fakeSession(id: Long = 10L) = Session(
        id = id,
        surface = "Hard",
        format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
        status = SessionStatus.COMPLETED,
        result = "DEFEAT",
        scoreText = "4-6, 3-6",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun fakeAdvice(id: Long = 1L) = CoachingCacheEntry(
        id = id,
        matchId = 10L,
        pattern = MatchPattern.NEUTRAL_TRANSITION,
        content = "Conseil test",
        generatedAt = System.currentTimeMillis()
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk()
        coachingRepository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state becomes Content with session and advices when loaded`() = runTest {
        val session = fakeSession()
        val advices = listOf(fakeAdvice())
        coEvery { sessionRepository.getSessionById(10L) } returns session
        coEvery { coachingRepository.getAdvicesForSession(10L) } returns advices

        val viewModel = SessionDetailViewModel(
            sessionRepository = sessionRepository,
            coachingRepository = coachingRepository,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 10L))
        )

        val state = viewModel.container.stateFlow.first { it is SessionDetailUiState.Content }
        val content = state as SessionDetailUiState.Content
        assertEquals(session, content.session)
        assertEquals(1, content.advices.size)
    }

    @Test
    fun `state becomes Error when session not found`() = runTest {
        coEvery { sessionRepository.getSessionById(99L) } returns null
        coEvery { coachingRepository.getAdvicesForSession(99L) } returns emptyList()

        val viewModel = SessionDetailViewModel(
            sessionRepository = sessionRepository,
            coachingRepository = coachingRepository,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 99L))
        )

        val state = viewModel.container.stateFlow.first { it is SessionDetailUiState.Error }
        assertTrue(state is SessionDetailUiState.Error)
    }

    @Test
    fun `Content state includes empty advices list when no coaching entries`() = runTest {
        val session = fakeSession()
        coEvery { sessionRepository.getSessionById(10L) } returns session
        coEvery { coachingRepository.getAdvicesForSession(10L) } returns emptyList()

        val viewModel = SessionDetailViewModel(
            sessionRepository = sessionRepository,
            coachingRepository = coachingRepository,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to 10L))
        )

        val state = viewModel.container.stateFlow.first { it is SessionDetailUiState.Content }
        assertTrue((state as SessionDetailUiState.Content).advices.isEmpty())
    }
}
