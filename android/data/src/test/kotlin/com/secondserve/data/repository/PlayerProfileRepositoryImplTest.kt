package com.secondserve.data.repository

import app.cash.turbine.test
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.ProfileDetailsResponse
import com.secondserve.data.remote.api.dto.RankingEntryDto
import com.secondserve.domain.repository.WorkAxisRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import com.secondserve.domain.AppResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerProfileRepositoryImplTest {

    private lateinit var dao: PlayerProfileDao
    private lateinit var vpsApiService: VpsApiService
    private lateinit var workAxisRepository: WorkAxisRepository
    private lateinit var repository: PlayerProfileRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        vpsApiService = mockk()
        workAxisRepository = mockk()
        coEvery { workAxisRepository.getActiveWorkAxesTitles() } returns emptyList()
        repository = PlayerProfileRepositoryImpl(dao, vpsApiService, workAxisRepository)
    }

    private fun profileEntity(
        currentSeries: String? = null,
        currentPoints: Int? = null,
        playStyle: String? = null,
        preferredSurfaces: String? = null,
        coachInstruction1: String? = null,
        coachInstruction2: String? = null,
        coachInstruction3: String? = null,
        updatedAt: Long = 1000L
    ) = PlayerProfileEntity(
        id = 1,
        currentSeries = currentSeries,
        currentPoints = currentPoints,
        playStyle = playStyle,
        preferredSurfaces = preferredSurfaces,
        coachInstruction1 = coachInstruction1,
        coachInstruction2 = coachInstruction2,
        coachInstruction3 = coachInstruction3,
        updatedAt = updatedAt
    )

    @Test
    fun `saveRanking with valid series upserts profile and inserts history atomically`() = runTest {
        val profileSlot = slot<PlayerProfileEntity>()
        val historySlot = slot<RankingHistoryEntity>()
        coEvery { dao.getProfile() } returns null
        coEvery { dao.saveProfileAndHistory(capture(profileSlot), capture(historySlot)) } returns Unit
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
        coEvery { dao.getProfile() } returns null
        coEvery { dao.saveProfileAndHistory(any(), any()) } returns Unit
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
        coEvery { dao.getProfile() } returns profileEntity(currentSeries = "15/2", currentPoints = 850)

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

    @Test
    fun `buildMatchContextProfile excludes empty and null coach instructions`() = runTest {
        coEvery { dao.getProfile() } returns profileEntity(
            coachInstruction1 = "travail du revers",
            coachInstruction2 = "",
            coachInstruction3 = null
        )

        val context = repository.buildMatchContextProfile()

        assertEquals(listOf("travail du revers"), context.coachInstructions)
    }

    @Test
    fun `buildMatchContextProfile parses preferredSurfaces from CSV`() = runTest {
        coEvery { dao.getProfile() } returns profileEntity(preferredSurfaces = "CLAY,HARD")

        val context = repository.buildMatchContextProfile()

        assertEquals(listOf("CLAY", "HARD"), context.preferredSurfaces)
    }

    @Test
    fun `buildMatchContextProfile returns activeWorkAxes from workAxisRepository`() = runTest {
        coEvery { dao.getProfile() } returns profileEntity()
        coEvery { workAxisRepository.getActiveWorkAxesTitles() } returns listOf("Revers", "Service")

        val context = repository.buildMatchContextProfile()

        assertEquals(listOf("Revers", "Service"), context.activeWorkAxes)
    }

    @Test
    fun `buildMatchContextProfile returns empty activeWorkAxes when no work axes`() = runTest {
        coEvery { dao.getProfile() } returns profileEntity()
        coEvery { workAxisRepository.getActiveWorkAxesTitles() } returns emptyList()

        val context = repository.buildMatchContextProfile()

        assertTrue(context.activeWorkAxes.isEmpty())
    }

    @Test
    fun `saveProfileDetails saves to Room and syncs to VPS`() = runTest {
        val profileSlot = slot<PlayerProfileEntity>()
        coEvery { dao.getProfile() } returns null
        coEvery { dao.upsertProfile(capture(profileSlot)) } returns Unit
        coEvery { vpsApiService.updateProfileDetails(any()) } returns ProfileDetailsResponse(updatedAt = 1000L)

        val result = repository.saveProfileDetails(
            playStyle = "OFFENSIVE",
            preferredSurfaces = listOf("CLAY", "HARD"),
            coachInstruction1 = "Améliorer le service",
            coachInstruction2 = null,
            coachInstruction3 = null
        )

        assertIs<AppResult.Success<Unit>>(result)
        assertEquals("OFFENSIVE", profileSlot.captured.playStyle)
        assertEquals("CLAY,HARD", profileSlot.captured.preferredSurfaces)
        assertEquals("Améliorer le service", profileSlot.captured.coachInstruction1)
        assertNull(profileSlot.captured.coachInstruction2)
    }

    @Test
    fun `saveProfileDetails when VPS fails local save still succeeds`() = runTest {
        coEvery { dao.getProfile() } returns null
        coEvery { dao.upsertProfile(any()) } returns Unit
        coEvery { vpsApiService.updateProfileDetails(any()) } throws RuntimeException("network error")

        val result = repository.saveProfileDetails(
            playStyle = "DEFENSIVE",
            preferredSurfaces = emptyList(),
            coachInstruction1 = null,
            coachInstruction2 = null,
            coachInstruction3 = null
        )

        assertIs<AppResult.Success<Unit>>(result)
    }

    @Test
    fun `buildMatchContextProfile trims whitespace from preferred surfaces`() = runTest {
        coEvery { dao.getProfile() } returns profileEntity(preferredSurfaces = "CLAY, HARD")

        val context = repository.buildMatchContextProfile()

        assertEquals(listOf("CLAY", "HARD"), context.preferredSurfaces)
    }

    @Test
    fun `getProfile rethrows CancellationException`() = runTest {
        coEvery { dao.getProfile() } throws CancellationException("cancelled")
        assertFailsWith<CancellationException> {
            repository.getProfile()
        }
    }

    @Test
    fun `saveRanking rethrows CancellationException`() = runTest {
        coEvery { dao.getProfile() } throws CancellationException("cancelled")
        assertFailsWith<CancellationException> {
            repository.saveRanking("15/2", 850)
        }
    }

    @Test
    fun `saveProfileDetails rethrows CancellationException`() = runTest {
        coEvery { dao.getProfile() } throws CancellationException("cancelled")
        assertFailsWith<CancellationException> {
            repository.saveProfileDetails("OFFENSIVE", listOf("CLAY"), null, null, null)
        }
    }

    @Test
    fun `saveProfileDetails reads updated profile written by saveRanking via Mutex`() = runTest {
        val savedProfiles = mutableListOf<PlayerProfileEntity>()
        coEvery { dao.getProfile() } coAnswers { savedProfiles.lastOrNull() }
        coEvery { dao.saveProfileAndHistory(any(), any()) } coAnswers { savedProfiles.add(firstArg()) }
        coEvery { dao.upsertProfile(any()) } coAnswers { savedProfiles.add(firstArg()) }
        coEvery { vpsApiService.saveRanking(any()) } throws RuntimeException("offline")
        coEvery { vpsApiService.updateProfileDetails(any()) } throws RuntimeException("offline")

        repository.saveRanking("15/2", 850)
        repository.saveProfileDetails("OFFENSIVE", listOf("CLAY"), null, null, null)

        assertEquals(2, savedProfiles.size)
        val finalEntity = savedProfiles.last()
        assertEquals("15/2", finalEntity.currentSeries, "saveProfileDetails doit préserver currentSeries de saveRanking")
        assertEquals("OFFENSIVE", finalEntity.playStyle)
    }

    @Test
    fun `observeMatchSessionCount emits 0`() = runTest {
        repository.observeMatchSessionCount().test {
            assertEquals(0, awaitItem())
            awaitComplete()
        }
    }
}
