package com.secondserve.data.worker

import android.content.Context
import androidx.work.WorkerParameters
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingSynthesisDao
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.entity.CoachingAnalysisEntity
import com.secondserve.data.local.db.entity.CoachingSynthesisEntity
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.SessionEntity
import com.secondserve.domain.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationWorkerTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var playerProfileDao: PlayerProfileDao
    private lateinit var workAxisDao: WorkAxisDao
    private lateinit var synthesisDao: CoachingSynthesisDao
    private lateinit var analysisDao: CoachingAnalysisDao
    private lateinit var playerDataStore: PlayerDataStore
    private lateinit var vpsMistralEngine: InferenceEngine

    @BeforeEach
    fun setup() {
        sessionDao = mockk()
        playerProfileDao = mockk()
        workAxisDao = mockk()
        synthesisDao = mockk()
        analysisDao = mockk()
        playerDataStore = mockk(relaxed = true)
        vpsMistralEngine = mockk()
    }

    private fun makeWorker() = NotificationWorker(
        context = mockk(relaxed = true),
        params = mockk(relaxed = true),
        sessionDao = sessionDao,
        playerProfileDao = playerProfileDao,
        workAxisDao = workAxisDao,
        synthesisDao = synthesisDao,
        analysisDao = analysisDao,
        playerDataStore = playerDataStore,
        vpsMistralEngine = vpsMistralEngine
    )

    private val now = System.currentTimeMillis()

    // ─── Cas 1 : mode silencieux actif ───────────────────────────────────────

    @Test
    fun `doWork_whenSilentModeActive_returnsSuccessWithoutCallingVps`() = runTest {
        coEvery { playerDataStore.getSilentModeUntil() } returns now + 3_600_000L

        val result = makeWorker().doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
        coVerify(exactly = 0) { sessionDao.countCompletedSince(any()) }
    }

    // ─── Cas 2 : mode silencieux expiré → réinitialisation automatique ───────

    @Test
    fun `doWork_whenSilentModeExpired_resetsSilentModeAndContinues`() = runTest {
        coEvery { playerDataStore.getSilentModeUntil() } returns now - 3_600_000L
        coEvery { sessionDao.countCompletedSince(any()) } returns 0

        makeWorker().doWork()

        coVerify(exactly = 1) { playerDataStore.saveSilentModeUntil(0L) }
    }

    // ─── Cas 3 : aucune session dans les 30 jours ────────────────────────────

    @Test
    fun `doWork_whenNoSessionsIn30Days_returnsSuccessWithoutNotification`() = runTest {
        coEvery { playerDataStore.getSilentModeUntil() } returns 0L
        coEvery { sessionDao.countCompletedSince(any()) } returns 0

        val result = makeWorker().doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
    }

    // ─── Cas 4 : VPS succès → contenu généré ─────────────────────────────────

    @Test
    fun `doWork_whenVpsSucceeds_postsNotification`() = runTest {
        coEvery { playerDataStore.getSilentModeUntil() } returns 0L
        coEvery { sessionDao.countCompletedSince(any()) } returns 3
        coEvery { playerProfileDao.getProfile() } returns aProfile()
        coEvery { workAxisDao.getAllTitles() } returns listOf("Service", "Revers")
        coEvery { sessionDao.getCompletedSince(any()) } returns listOf(aSession())
        coEvery { synthesisDao.getLatest() } returns aSynthesis()
        coEvery { analysisDao.getMostRecent() } returns null
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Success("Travaille ton service en deuxième balle.")

        val worker = makeWorker()
        val content = worker.generateContent("Terre battue", listOf("Service"), "VICTORY")

        assertEquals("Travaille ton service en deuxième balle.", content)
        coVerify(exactly = 1) { vpsMistralEngine.generate(any()) }
    }

    // ─── Cas 5 : VPS échoue → fallback local ─────────────────────────────────

    @Test
    fun `doWork_whenVpsFails_usesLocalFallback`() = runTest {
        coEvery { synthesisDao.getLatest() } returns aSynthesis()
        coEvery { analysisDao.getMostRecent() } returns null
        coEvery { vpsMistralEngine.generate(any()) } returns AppResult.Error(RuntimeException("VPS down"))

        val worker = makeWorker()
        val content = worker.generateContent("Terre battue", listOf("Service"), "VICTORY")

        assertEquals("Surface : Terre battue | Axe du moment : Service | Résultat récent : VICTORY", content)
    }

    // ─── Cas 6 : aucune donnée disponible → pas de notification ──────────────

    @Test
    fun `doWork_whenNoCoachingDataAndNoProfile_skipsNotification`() = runTest {
        coEvery { synthesisDao.getLatest() } returns null
        coEvery { analysisDao.getMostRecent() } returns null

        val worker = makeWorker()
        val content = worker.generateContent(null, emptyList(), null)

        assertEquals(null, content)
        coVerify(exactly = 0) { vpsMistralEngine.generate(any()) }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun aProfile() = PlayerProfileEntity(
        id = 1,
        currentSeries = "15/1",
        currentPoints = null,
        playStyle = "Défensif",
        preferredSurfaces = "Terre battue",
        coachInstruction1 = null,
        coachInstruction2 = null,
        coachInstruction3 = null,
        updatedAt = 0L
    )

    private fun aSession() = SessionEntity(
        id = 1L,
        surface = "CLAY",
        matchFormat = "BEST_OF_3",
        thirdSetRule = "FULL_ADVANTAGE",
        status = "COMPLETED",
        result = "VICTORY",
        scoreText = "6/4 6/3",
        createdAt = 1_000_000L,
        updatedAt = 2_000_000L
    )

    private fun aSynthesis() = CoachingSynthesisEntity(
        id = 1L,
        content = "Synthèse de coaching multi-matchs.",
        sessionCount = 3,
        generatedAt = 1_000_000L
    )
}
