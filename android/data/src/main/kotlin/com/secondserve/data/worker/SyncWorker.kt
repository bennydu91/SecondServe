package com.secondserve.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.local.db.entity.toSyncDto
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.SyncPushRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val sessionDao: SessionDao,
    private val vpsApiService: VpsApiService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = syncQueueDao.getPending()
        if (pending.isEmpty()) return Result.success()

        val sessionIds = pending
            .filter { it.entityType == "SESSION" }
            .map { it.entityId }
            .distinct()

        // Sépare sessions sérialisables des sessions corrompues (erreur de données permanente)
        val dtoBySessionId = sessionIds.associateWith { id ->
            sessionDao.getById(id)?.let { entity ->
                runCatching { entity.toDomain().toSyncDto() }.getOrElse { e ->
                    Timber.e(e, "SyncWorker: cannot serialize session %d — marking FAILED", id)
                    null
                }
            }
        }

        val unserializableIds = dtoBySessionId.filterValues { it == null }.keys
        pending.filter { it.entityId in unserializableIds }.forEach { syncQueueDao.markFailed(it.id) }

        val sessionDtos = dtoBySessionId.values.filterNotNull()
        if (sessionDtos.isEmpty()) return Result.success()

        return try {
            vpsApiService.syncPush(SyncPushRequest(sessions = sessionDtos))
            val syncedIds = dtoBySessionId.filterValues { it != null }.keys
            pending.filter { it.entityId in syncedIds }.forEach { syncQueueDao.markDone(it.id) }
            Timber.d("SyncWorker: %d sessions synced", sessionDtos.size)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: push failed — will retry")
            // Pas de markFailed ici : les entrées restent PENDING pour le prochain retry WorkManager
            Result.retry()
        }
    }
}
