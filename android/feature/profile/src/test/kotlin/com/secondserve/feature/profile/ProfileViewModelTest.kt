package com.secondserve.feature.profile

import app.cash.turbine.test
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.repository.PlayerProfileRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private lateinit var repository: PlayerProfileRepository
    private lateinit var playerDataStore: PlayerDataStore
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        playerDataStore = mockk()
        coEvery { repository.getProfile() } returns AppResult.Success(null)
        coEvery { repository.getRankingHistory() } returns flowOf(emptyList())
        coEvery { repository.buildMatchContextProfile() } returns MatchContextProfile()
        coEvery { repository.observeMatchSessionCount() } returns flowOf(0)
        every { playerDataStore.getFftLicenseNumber() } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ProfileViewModel(repository, playerDataStore)

    @Test
    fun `saveRanking with valid series and positive points emits RankingSaved`() = runTest {
        coEvery { repository.saveRanking("15/2", 100) } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.saveRanking("15/2", 100)
            assertEquals(ProfileSideEffect.RankingSaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveRanking with invalid series emits ShowError without calling repository`() = runTest {
        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.saveRanking("invalide", 100)
            val effect = awaitItem()
            assertIs<ProfileSideEffect.ShowError>(effect)
            assertTrue(effect.message.contains("invalide"))
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.saveRanking(any(), any()) }
    }

    @Test
    fun `saveRanking with negative points emits ShowError without calling repository`() = runTest {
        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.saveRanking("15/2", -1)
            val effect = awaitItem()
            assertIs<ProfileSideEffect.ShowError>(effect)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.saveRanking(any(), any()) }
    }

    @Test
    fun `saveProfileDetails emits ProfileDetailsSaved on success`() = runTest {
        coEvery {
            repository.saveProfileDetails(any(), any(), any(), any(), any())
        } returns AppResult.Success(Unit)

        val viewModel = createViewModel()

        viewModel.container.sideEffectFlow.test {
            viewModel.saveProfileDetails("OFFENSIVE", listOf("CLAY"), "Améliorer le service", null, null)
            assertEquals(ProfileSideEffect.ProfileDetailsSaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `matchSessionCount is 0 when observeMatchSessionCount emits 0`() = runTest {
        val viewModel = createViewModel()

        assertEquals(0, viewModel.container.stateFlow.value.matchSessionCount)
    }

    @Test
    fun `saveFftLicense stores license in PlayerDataStore and updates state`() = runTest {
        every { playerDataStore.saveFftLicenseNumber("1234567") } returns Unit

        val viewModel = createViewModel()

        viewModel.saveFftLicense("1234567")

        coVerify { playerDataStore.saveFftLicenseNumber("1234567") }
        assertEquals("1234567", viewModel.container.stateFlow.value.fftLicenseNumber)
    }

    @Test
    fun `FFT license is not passed to saveProfileDetails`() = runTest {
        coEvery {
            repository.saveProfileDetails(any(), any(), any(), any(), any())
        } returns AppResult.Success(Unit)

        val viewModel = createViewModel()
        viewModel.saveProfileDetails("OFFENSIVE", listOf("CLAY"), null, null, null)

        coVerify {
            repository.saveProfileDetails(
                playStyle = "OFFENSIVE",
                preferredSurfaces = listOf("CLAY"),
                coachInstruction1 = null,
                coachInstruction2 = null,
                coachInstruction3 = null
            )
        }
    }
}
