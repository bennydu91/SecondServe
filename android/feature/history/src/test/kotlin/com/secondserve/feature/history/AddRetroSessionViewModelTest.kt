package com.secondserve.feature.history

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SurfaceConstants
import com.secondserve.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddRetroSessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var sessionRepository: SessionRepository

    private fun fakeSession(id: Long = 42L) = Session(
        id = id,
        surface = SurfaceConstants.CLAY,
        format = SessionFormat(MatchFormat.BEST_OF_1),
        status = SessionStatus.COMPLETED,
        result = "VICTORY",
        createdAt = 1_700_000_000_000L,
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
    fun `canSubmit is false with no fields set`() = runTest {
        val vm = AddRetroSessionViewModel(sessionRepository)
        val state = vm.container.stateFlow.first()
        assertFalse(state.canSubmit)
    }

    @Test
    fun `canSubmit is true when required fields are set for BEST_OF_1`() = runTest {
        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_1)
        vm.onResultSelected("VICTORY")
        vm.onMatchDateSelected(1_700_000_000_000L)

        val state = vm.container.stateFlow.first {
            it.selectedSurface != null && it.matchDateMillis != null
        }
        assertTrue(state.canSubmit)
    }

    @Test
    fun `canSubmit is false for BEST_OF_3 when thirdSetRule not selected`() = runTest {
        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_3)
        vm.onResultSelected("VICTORY")
        vm.onMatchDateSelected(1_700_000_000_000L)

        val state = vm.container.stateFlow.first { it.selectedMatchFormat != null }
        assertFalse(state.canSubmit)
    }

    @Test
    fun `submit success emits SessionCreated side effect`() = runTest {
        coEvery { sessionRepository.createCompletedSession(any()) } returns AppResult.Success(fakeSession())

        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_1)
        vm.onResultSelected("VICTORY")
        vm.onMatchDateSelected(1_700_000_000_000L)

        val effect = coroutineScope {
            val deferred = async { vm.container.sideEffectFlow.first() }
            vm.submit()
            deferred.await()
        }
        assertTrue(effect is AddRetroSessionSideEffect.SessionCreated)
    }

    @Test
    fun `submit failure emits ShowError side effect`() = runTest {
        coEvery { sessionRepository.createCompletedSession(any()) } returns AppResult.Error(
            RuntimeException("DB error")
        )

        val vm = AddRetroSessionViewModel(sessionRepository)
        vm.onSurfaceSelected(SurfaceConstants.CLAY)
        vm.onMatchFormatSelected(MatchFormat.BEST_OF_1)
        vm.onResultSelected("DEFEAT")
        vm.onMatchDateSelected(1_700_000_000_000L)

        val effect = coroutineScope {
            val deferred = async { vm.container.sideEffectFlow.first() }
            vm.submit()
            deferred.await()
        }
        assertTrue(effect is AddRetroSessionSideEffect.ShowError)
    }
}
