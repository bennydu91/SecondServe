package com.secondserve.feature.profile

import app.cash.turbine.test
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.repository.PlayerProfileRepository
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
class ProfileViewModelTest {

    private lateinit var repository: PlayerProfileRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        coEvery { repository.getProfile() } returns AppResult.Success(null)
        coEvery { repository.getRankingHistory() } returns flowOf(emptyList())
        coEvery { repository.buildMatchContextProfile() } returns MatchContextProfile()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveRanking with valid series and positive points emits RankingSaved`() = runTest {
        coEvery { repository.saveRanking("15/2", 100) } returns AppResult.Success(Unit)

        val viewModel = ProfileViewModel(repository)

        viewModel.container.sideEffectFlow.test {
            viewModel.saveRanking("15/2", 100)
            assertEquals(ProfileSideEffect.RankingSaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveRanking with invalid series emits ShowError without calling repository`() = runTest {
        val viewModel = ProfileViewModel(repository)

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
        val viewModel = ProfileViewModel(repository)

        viewModel.container.sideEffectFlow.test {
            viewModel.saveRanking("15/2", -1)
            val effect = awaitItem()
            assertIs<ProfileSideEffect.ShowError>(effect)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.saveRanking(any(), any()) }
    }
}
