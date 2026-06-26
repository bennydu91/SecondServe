package com.secondserve.feature.match

import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.notification.NotificationScheduler
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewMatchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var sessionRepository: SessionRepository
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var dataLayerClient: DataLayerClient
    private lateinit var viewModel: NewMatchViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk()
        notificationScheduler = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)

        viewModel = NewMatchViewModel(
            sessionRepository = sessionRepository,
            notificationScheduler = notificationScheduler,
            dataLayerClient = dataLayerClient
        )
    }

    @AfterEach
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `startMatch calls sendStartSession with correct args after successful session creation`() = runTest {
        val now = System.currentTimeMillis()
        val createdSession = Session(
            id = 42L,
            surface = "Clay",
            format = SessionFormat(matchFormat = MatchFormat.BEST_OF_3, thirdSetRule = ThirdSetRule.FULL_ADVANTAGE),
            status = SessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        coEvery { sessionRepository.createSession(any()) } returns AppResult.Success(createdSession)
        coEvery {
            dataLayerClient.sendStartSession(
                sessionId = 42L,
                matchFormat = MatchFormat.BEST_OF_3,
                thirdSetRule = ThirdSetRule.FULL_ADVANTAGE
            )
        } returns AppResult.Success(Unit)

        viewModel.onSurfaceSelected("Clay")
        viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3)
        viewModel.onThirdSetRuleSelected(ThirdSetRule.FULL_ADVANTAGE)

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is NewMatchSideEffect.SessionStarted }
        }

        viewModel.startMatch()
        sideEffectDeferred.await()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            dataLayerClient.sendStartSession(
                sessionId = 42L,
                matchFormat = MatchFormat.BEST_OF_3,
                thirdSetRule = ThirdSetRule.FULL_ADVANTAGE
            )
        }
    }

    @Test
    fun `startMatch does not call sendStartSession when session creation fails`() = runTest {
        coEvery { sessionRepository.createSession(any()) } returns AppResult.Error(RuntimeException("DB error"))

        viewModel.onSurfaceSelected("Clay")
        viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3)
        viewModel.onThirdSetRuleSelected(ThirdSetRule.FULL_ADVANTAGE)

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is NewMatchSideEffect.ShowError }
        }

        viewModel.startMatch()
        sideEffectDeferred.await()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { dataLayerClient.sendStartSession(any(), any(), any()) }
    }
}
