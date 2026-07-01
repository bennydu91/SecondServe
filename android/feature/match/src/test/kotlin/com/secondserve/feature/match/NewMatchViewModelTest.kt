package com.secondserve.feature.match

import com.secondserve.data.monitoring.MonitoringEventQueue
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
    private lateinit var monitoringEventQueue: MonitoringEventQueue
    private lateinit var viewModel: NewMatchViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk()
        notificationScheduler = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)
        monitoringEventQueue = mockk(relaxed = true)

        viewModel = NewMatchViewModel(
            sessionRepository = sessionRepository,
            notificationScheduler = notificationScheduler,
            dataLayerClient = dataLayerClient,
            monitoringEventQueue = monitoringEventQueue,
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
            opponent = "Marceau",
            status = SessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        coEvery { sessionRepository.createSession(any()) } returns AppResult.Success(createdSession)
        coEvery {
            dataLayerClient.sendStartSession(
                sessionId = 42L,
                matchFormat = MatchFormat.BEST_OF_3,
                thirdSetRule = ThirdSetRule.FULL_ADVANTAGE,
                opponent = "Marceau"
            )
        } returns AppResult.Success(Unit)

        viewModel.onSurfaceSelected("Clay")
        viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3)
        viewModel.onThirdSetRuleSelected(ThirdSetRule.FULL_ADVANTAGE)
        viewModel.onOpponentChanged("Marceau")

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
                thirdSetRule = ThirdSetRule.FULL_ADVANTAGE,
                opponent = "Marceau"
            )
        }
    }

    @Test
    fun `startMatch sends null opponent to DataLayer when opponent field left blank`() = runTest {
        val now = System.currentTimeMillis()
        val createdSession = Session(
            id = 43L,
            surface = "Clay",
            format = SessionFormat(matchFormat = MatchFormat.BEST_OF_3, thirdSetRule = ThirdSetRule.FULL_ADVANTAGE),
            opponent = null,
            status = SessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        coEvery { sessionRepository.createSession(any()) } returns AppResult.Success(createdSession)
        coEvery {
            dataLayerClient.sendStartSession(
                sessionId = 43L,
                matchFormat = MatchFormat.BEST_OF_3,
                thirdSetRule = ThirdSetRule.FULL_ADVANTAGE,
                opponent = null
            )
        } returns AppResult.Success(Unit)

        viewModel.onSurfaceSelected("Clay")
        viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3)
        viewModel.onThirdSetRuleSelected(ThirdSetRule.FULL_ADVANTAGE)
        // opponent field never touched — reste vide ("") côté state, Session.opponent = null

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is NewMatchSideEffect.SessionStarted }
        }

        viewModel.startMatch()
        sideEffectDeferred.await()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            dataLayerClient.sendStartSession(
                sessionId = 43L,
                matchFormat = MatchFormat.BEST_OF_3,
                thirdSetRule = ThirdSetRule.FULL_ADVANTAGE,
                opponent = null
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

        coVerify(exactly = 0) { dataLayerClient.sendStartSession(any(), any(), any(), any()) }
    }

    @Test
    fun `startMatch does not call sendStartSession when session is PLANNED`() = runTest {
        val now = System.currentTimeMillis()
        val futureScheduledAt = now + 2 * 60 * 60 * 1000L  // 2 hours in the future
        val createdSession = Session(
            id = 42L,
            surface = "Clay",
            format = SessionFormat(matchFormat = MatchFormat.BEST_OF_3, thirdSetRule = ThirdSetRule.FULL_ADVANTAGE),
            status = SessionStatus.PLANNED,
            scheduledAt = futureScheduledAt,
            createdAt = now,
            updatedAt = now
        )
        coEvery { sessionRepository.createSession(any()) } returns AppResult.Success(createdSession)
        coEvery { notificationScheduler.schedulePreMatchReminder(any(), any()) } returns Unit

        viewModel.onSurfaceSelected("Clay")
        viewModel.onMatchFormatSelected(MatchFormat.BEST_OF_3)
        viewModel.onThirdSetRuleSelected(ThirdSetRule.FULL_ADVANTAGE)
        viewModel.onScheduledToggled(true)
        viewModel.onScheduledAtChanged(futureScheduledAt)

        val sideEffectDeferred = async {
            viewModel.container.sideEffectFlow.first { it is NewMatchSideEffect.SessionPlanned }
        }

        viewModel.startMatch()
        sideEffectDeferred.await()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { dataLayerClient.sendStartSession(any(), any(), any(), any()) }
    }
}
