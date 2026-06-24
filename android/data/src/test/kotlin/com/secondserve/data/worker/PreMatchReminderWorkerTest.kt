package com.secondserve.data.worker

import android.content.Context
import androidx.work.ListenableWorker
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.PendingNotificationResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PreMatchReminderWorkerTest {

    private lateinit var vpsApiService: VpsApiService
    private lateinit var playerProfileDao: PlayerProfileDao
    private lateinit var workAxisDao: WorkAxisDao
    private lateinit var playerDataStore: PlayerDataStore
    private lateinit var sessionDao: SessionDao

    @BeforeEach
    fun setup() {
        vpsApiService = mockk()
        playerProfileDao = mockk()
        workAxisDao = mockk()
        playerDataStore = mockk(relaxed = true)
        sessionDao = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun makeWorker(sessionId: Long = 42L): PreMatchReminderWorker {
        val params = mockk<androidx.work.WorkerParameters>(relaxed = true) {
            every { inputData.getLong(PreMatchReminderWorker.KEY_SESSION_ID, -1L) } returns sessionId
        }
        return PreMatchReminderWorker(
            context = mockk(relaxed = true),
            params = params,
            vpsApiService = vpsApiService,
            playerProfileDao = playerProfileDao,
            workAxisDao = workAxisDao,
            playerDataStore = playerDataStore,
            sessionDao = sessionDao,
        )
    }

    // ─── Cas 1 : session_id invalide → retour immédiat, VPS non appelé ────────

    @Test
    fun `doWork_whenSessionIdInvalid_returnsSuccessWithoutCallingVps`() = runTest {
        val result = makeWorker(sessionId = -1L).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { vpsApiService.getPendingNotification(any()) }
    }

    // ─── Cas 2 : VPS disponible → contenu récupéré ────────────────────────────

    @Test
    fun `doWork_whenVpsSucceeds_callsVpsAndReturnsSuccess`() = runTest {
        coEvery { vpsApiService.getPendingNotification(42L) } returns
            PendingNotificationResponse(content = "Prépare ton service en variant les effets.")

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { vpsApiService.getPendingNotification(42L) }
    }

    // ─── Cas 3 : VPS indisponible → fallback local avec profil ───────────────

    @Test
    fun `doWork_whenVpsFails_usesFallbackFromProfileAndReturnsSuccess`() = runTest {
        coEvery { vpsApiService.getPendingNotification(any()) } throws RuntimeException("VPS down")
        coEvery { playerProfileDao.getProfile() } returns aProfile()
        coEvery { workAxisDao.getAllTitles() } returns listOf("Service", "Revers")

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { playerProfileDao.getProfile() }
        coVerify(exactly = 1) { workAxisDao.getAllTitles() }
    }

    // ─── Cas 4 : VPS indisponible ET aucune donnée locale → message générique ─

    @Test
    fun `doWork_whenVpsFailsAndNoLocalData_usesGenericFallbackAndReturnsSuccess`() = runTest {
        coEvery { vpsApiService.getPendingNotification(any()) } throws RuntimeException("VPS down")
        coEvery { playerProfileDao.getProfile() } returns null
        coEvery { workAxisDao.getAllTitles() } returns emptyList()

        val result = makeWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun aProfile() = PlayerProfileEntity(
        id = 1,
        currentSeries = "30/2",
        currentPoints = null,
        playStyle = "Offensif",
        preferredSurfaces = "Terre battue",
        coachInstruction1 = null,
        coachInstruction2 = null,
        coachInstruction3 = null,
        updatedAt = 0L
    )
}
