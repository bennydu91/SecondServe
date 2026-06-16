package com.secondserve.data.repository

import app.cash.turbine.test
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.RankingEntryDto
import com.secondserve.data.remote.api.dto.RankingRequest
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerProfileRepositoryImplTest {

    private lateinit var dao: PlayerProfileDao
    private lateinit var vpsApiService: VpsApiService
    private lateinit var repository: PlayerProfileRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        vpsApiService = mockk()
        repository = PlayerProfileRepositoryImpl(dao, vpsApiService)
    }

    @Test
    fun `saveRanking with valid series upserts profile and inserts history`() = runTest {
        val profileSlot = slot<PlayerProfileEntity>()
        val historySlot = slot<RankingHistoryEntity>()
        coEvery { dao.upsertProfile(capture(profileSlot)) } returns Unit
        coEvery { dao.insertRanking(capture(historySlot)) } returns Unit
        coEvery { vpsApiService.saveRanking(any()) } returns RankingEntryDto(1, "15/2", 850, 0L)

        val result = repository.saveRanking("15/2", 850)

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals("15/2", profileSlot.captured.currentSeries)
        assertEquals(850, profileSlot.captured.currentPoints)
        assertEquals("15/2", historySlot.captured.series)
        assertEquals(850, historySlot.captured.points)
    }

    @Test
    fun `saveRanking when VPS fails local save still succeeds`() = runTest {
        coEvery { dao.upsertProfile(any()) } returns Unit
        coEvery { dao.insertRanking(any()) } returns Unit
        coEvery { vpsApiService.saveRanking(any()) } throws RuntimeException("network error")

        val result = repository.saveRanking("15/2", 850)

        assertIs<AppResult.Success<Unit>>(result)
    }

    @Test
    fun `getRankingHistory returns Flow with entries ordered by date descending`() = runTest {
        val entities = listOf(
            RankingHistoryEntity(id = 2, series = "15/2", points = 850, recordedAt = 2000L, updatedAt = 2000L),
            RankingHistoryEntity(id = 1, series = "40", points = 500, recordedAt = 1000L, updatedAt = 1000L)
        )
        coEvery { dao.getRankingHistory() } returns flowOf(entities)

        repository.getRankingHistory().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("15/2", items[0].series)
            assertEquals("40", items[1].series)
            awaitComplete()
        }
    }

    @Test
    fun `buildMatchContextProfile returns fftSeries from current profile`() = runTest {
        coEvery { dao.getProfile() } returns PlayerProfileEntity(
            id = 1, currentSeries = "15/2", currentPoints = 850, updatedAt = 1000L
        )

        val context = repository.buildMatchContextProfile()

        assertEquals("15/2", context.fftSeries)
        assertNull(context.playStyle)
        assertTrue(context.activeWorkAxes.isEmpty())
    }

    @Test
    fun `buildMatchContextProfile returns null fftSeries when no profile`() = runTest {
        coEvery { dao.getProfile() } returns null

        val context = repository.buildMatchContextProfile()

        assertNull(context.fftSeries)
    }
}
