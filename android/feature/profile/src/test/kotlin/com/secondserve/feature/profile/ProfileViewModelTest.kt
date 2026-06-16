package com.secondserve.feature.profile

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.repository.PlayerProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.orbitmvi.orbit.test.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private lateinit var repository: PlayerProfileRepository
    private val testDispatcher = StandardTestDispatcher()

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
        viewModel.test(this) {
            expectInitialState()
            viewModel.saveRanking("15/2", 100)
            expectSideEffect(ProfileSideEffect.RankingSaved)
        }
    }

    @Test
    fun `saveRanking with invalid series emits ShowError without calling repository`() = runTest {
        val viewModel = ProfileViewModel(repository)
        viewModel.test(this) {
            expectInitialState()
            viewModel.saveRanking("invalide", 100)
            val effect = expectSideEffect()
            assertTrue(effect is ProfileSideEffect.ShowError)
            assertTrue((effect as ProfileSideEffect.ShowError).message.contains("invalide"))
        }
        coVerify(exactly = 0) { repository.saveRanking(any(), any()) }
    }

    @Test
    fun `saveRanking with negative points emits ShowError without calling repository`() = runTest {
        val viewModel = ProfileViewModel(repository)
        viewModel.test(this) {
            expectInitialState()
            viewModel.saveRanking("15/2", -1)
            val effect = expectSideEffect()
            assertTrue(effect is ProfileSideEffect.ShowError)
        }
        coVerify(exactly = 0) { repository.saveRanking(any(), any()) }
    }
}
