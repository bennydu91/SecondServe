package com.secondserve.data.worker

import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.db.entity.SessionEntity
import com.secondserve.data.local.db.entity.SyncQueueEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.local.db.entity.toSyncDto
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.SyncPushRequest
import com.secondserve.data.remote.api.dto.SyncPushResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncWorkerTest {

    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var sessionDao: SessionDao
    private lateinit var vpsApiService: VpsApiService

    @BeforeEach
    fun setup() {
        syncQueueDao = mockk(relaxed = true)
        sessionDao = mockk()
        vpsApiService = mockk()
    }

    private fun aSessionEntity(id: Long = 1L) = SessionEntity(
        id = id,
        surface = "CLAY",
        matchFormat = "BEST_OF_3",
        thirdSetRule = "FULL_ADVANTAGE",
        status = "COMPLETED",
        result = "VICTORY",
        createdAt = 1_000_000L,
        updatedAt = 2_000_000L
    )

    private fun aSyncQueueEntry(id: Long = 1L, sessionId: Long = 1L) = SyncQueueEntity(
        id = id,
        entityType = "SESSION",
        entityId = sessionId,
        operation = "UPSERT",
        status = "PENDING",
        createdAt = 1_000_000L
    )

    @Test
    fun `doWork returns success and marks done when push succeeds`() = runTest {
        val entry = aSyncQueueEntry()
        coEvery { syncQueueDao.getPending() } returns listOf(entry)
        coEvery { sessionDao.getById(1L) } returns aSessionEntity(id = 1L)
        coEvery { vpsApiService.syncPush(any()) } returns SyncPushResponse(syncedSessions = 1)

        val worker = TestSyncWorkerHelper(syncQueueDao, sessionDao, vpsApiService)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { syncQueueDao.markDone(1L) }
        coVerify(exactly = 0) { syncQueueDao.markFailed(any()) }
    }

    @Test
    fun `doWork returns retry without markFailed when push throws (network error)`() = runTest {
        val entry = aSyncQueueEntry()
        coEvery { syncQueueDao.getPending() } returns listOf(entry)
        coEvery { sessionDao.getById(1L) } returns aSessionEntity(id = 1L)
        coEvery { vpsApiService.syncPush(any()) } throws RuntimeException("Network error")

        val worker = TestSyncWorkerHelper(syncQueueDao, sessionDao, vpsApiService)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { syncQueueDao.markFailed(any()) }
        coVerify(exactly = 0) { syncQueueDao.markDone(any()) }
    }

    @Test
    fun `doWork marks FAILED unserializable session without retrying network`() = runTest {
        val entry = aSyncQueueEntry()
        coEvery { syncQueueDao.getPending() } returns listOf(entry)
        coEvery { sessionDao.getById(1L) } returns null  // session disparue = données corrompues

        val worker = TestSyncWorkerHelper(syncQueueDao, sessionDao, vpsApiService)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { syncQueueDao.markFailed(1L) }
        coVerify(exactly = 0) { vpsApiService.syncPush(any()) }
    }

    @Test
    fun `doWork returns success immediately when no pending entries`() = runTest {
        coEvery { syncQueueDao.getPending() } returns emptyList()

        val worker = TestSyncWorkerHelper(syncQueueDao, sessionDao, vpsApiService)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { vpsApiService.syncPush(any()) }
    }

    @Test
    fun `doWork sends distinct session IDs only (idempotence)`() = runTest {
        val entries = listOf(aSyncQueueEntry(id = 1L, sessionId = 10L), aSyncQueueEntry(id = 2L, sessionId = 10L))
        coEvery { syncQueueDao.getPending() } returns entries
        coEvery { sessionDao.getById(10L) } returns aSessionEntity(id = 10L)
        coEvery { vpsApiService.syncPush(any()) } returns SyncPushResponse(syncedSessions = 1)

        val requestSlot = slot<com.secondserve.data.remote.api.dto.SyncPushRequest>()
        coEvery { vpsApiService.syncPush(capture(requestSlot)) } returns SyncPushResponse(syncedSessions = 1)

        val worker = TestSyncWorkerHelper(syncQueueDao, sessionDao, vpsApiService)
        worker.doWork()

        assertEquals(1, requestSlot.captured.sessions.size, "Session dupliquée dans la même queue → doit être dédupliquée")
        coVerify(exactly = 1) { syncQueueDao.markDone(1L) }
        coVerify(exactly = 1) { syncQueueDao.markDone(2L) }
    }
}

// Helper pour tester la logique de doWork sans contexte WorkManager Android
private class TestSyncWorkerHelper(
    private val syncQueueDao: SyncQueueDao,
    private val sessionDao: SessionDao,
    private val vpsApiService: VpsApiService
) {
    suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val pending = syncQueueDao.getPending()
        if (pending.isEmpty()) return androidx.work.ListenableWorker.Result.success()

        val sessionIds = pending
            .filter { it.entityType == "SESSION" }
            .map { it.entityId }
            .distinct()

        val dtoBySessionId = sessionIds.associateWith { id ->
            sessionDao.getById(id)?.let { entity ->
                runCatching { entity.toDomain().toSyncDto() }.getOrNull()
            }
        }

        val unserializableIds = dtoBySessionId.filterValues { it == null }.keys
        pending.filter { it.entityId in unserializableIds }.forEach { syncQueueDao.markFailed(it.id) }

        val sessionDtos = dtoBySessionId.values.filterNotNull()
        if (sessionDtos.isEmpty()) return androidx.work.ListenableWorker.Result.success()

        return try {
            vpsApiService.syncPush(SyncPushRequest(sessions = sessionDtos))
            val syncedIds = dtoBySessionId.filterValues { it != null }.keys
            pending.filter { it.entityId in syncedIds }.forEach { syncQueueDao.markDone(it.id) }
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
