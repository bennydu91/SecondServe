package com.secondserve.data.repository

import androidx.room.withTransaction
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.db.SecondServeDatabase
import com.secondserve.data.local.db.entity.SyncQueueEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.local.db.entity.toEntity
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.Session
import com.secondserve.domain.notification.NotificationScheduler
import com.secondserve.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val syncQueueDao: SyncQueueDao,
    private val database: SecondServeDatabase,
    private val notificationScheduler: NotificationScheduler
) : SessionRepository {

    override suspend fun createSession(session: Session): AppResult<Session> = try {
        val id = dao.insert(session.toEntity())
        if (session.scheduledAt != null) {
            val now = System.currentTimeMillis()
            syncQueueDao.insert(SyncQueueEntity(
                entityType = SyncQueueEntity.ENTITY_TYPE_SESSION,
                entityId = id,
                operation = SyncQueueEntity.OPERATION_UPSERT,
                createdAt = now
            ))
        }
        Timber.d("SessionRepository: session créée id=%d (planned=%b)", id, session.scheduledAt != null)
        AppResult.Success(session.copy(id = id))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "SessionRepository: createSession failed")
        AppResult.Error(e)
    }

    override suspend fun createCompletedSession(session: Session): AppResult<Session> = try {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val id = dao.insert(session.toEntity())
            syncQueueDao.insert(SyncQueueEntity(
                entityType = SyncQueueEntity.ENTITY_TYPE_SESSION,
                entityId = id,
                operation = SyncQueueEntity.OPERATION_UPSERT,
                createdAt = now
            ))
            AppResult.Success(session.copy(id = id))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "SessionRepository: createCompletedSession failed")
        AppResult.Error(e)
    }

    override fun getAllSessions(): Flow<List<Session>> =
        dao.getAllSessions().map { entities ->
            entities.mapNotNull { entity ->
                runCatching { entity.toDomain() }.getOrElse { e ->
                    Timber.e(e, "SessionRepository: skipping corrupted session id=${entity.id}")
                    null
                }
            }
        }

    override suspend fun getSessionById(id: Long): Session? =
        dao.getById(id)?.let { entity ->
            runCatching { entity.toDomain() }.getOrElse { e ->
                Timber.e(e, "SessionRepository: getSessionById id=${entity.id} failed to map")
                null
            }
        }

    override suspend fun getPointSummaryForSession(sessionId: Long): Pair<Int, Int> {
        val counts = dao.getPointCountsByScorer(sessionId)
        val self = counts.firstOrNull { it.scorer == "SELF" }?.cnt ?: 0
        val opponent = counts.firstOrNull { it.scorer == "OPPONENT" }?.cnt ?: 0
        return Pair(self, opponent)
    }

    override suspend fun updateMatchStats(
        sessionId: Long,
        firstServePercentSelf: Int?,
        firstServePercentOpponent: Int?,
        winnersSelf: Int?,
        winnersOpponent: Int?
    ): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        val existing = dao.getById(sessionId) ?: return AppResult.Error(
            IllegalArgumentException("Session $sessionId introuvable")
        )
        database.withTransaction {
            dao.update(existing.copy(
                firstServePercentSelf = firstServePercentSelf,
                firstServePercentOpponent = firstServePercentOpponent,
                winnersSelf = winnersSelf,
                winnersOpponent = winnersOpponent,
                updatedAt = now
            ))
            syncQueueDao.insert(SyncQueueEntity(
                entityType = SyncQueueEntity.ENTITY_TYPE_SESSION,
                entityId = sessionId,
                operation = SyncQueueEntity.OPERATION_UPSERT,
                createdAt = now
            ))
        }
        Timber.d("SessionRepository: stats fines mises à jour pour session %d", sessionId)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "SessionRepository: updateMatchStats failed")
        AppResult.Error(e)
    }

    override suspend fun countCompletedSince(afterMs: Long): Int =
        dao.countCompletedSince(afterMs)

    override suspend fun getCompletedSince(afterMs: Long): List<Session> =
        dao.getCompletedSince(afterMs).mapNotNull { entity ->
            runCatching { entity.toDomain() }.getOrElse { e ->
                Timber.e(e, "SessionRepository: skipping corrupted session id=${entity.id}")
                null
            }
        }

    override suspend fun deleteSession(sessionId: Long): AppResult<Unit> = try {
        dao.deleteById(sessionId)
        notificationScheduler.cancelPreMatchReminder(sessionId)
        syncQueueDao.insert(SyncQueueEntity(
            entityType = SyncQueueEntity.ENTITY_TYPE_SESSION,
            entityId = sessionId,
            operation = SyncQueueEntity.OPERATION_DELETE,
            createdAt = System.currentTimeMillis()
        ))
        Timber.d("SessionRepository: session %d supprimée + reminder annulé + sync DELETE enqueued", sessionId)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "SessionRepository: deleteSession failed for id=%d", sessionId)
        AppResult.Error(e)
    }

    override suspend fun closeSession(
        sessionId: Long,
        result: String,
        scoreText: String?,
        feelingRating: Int?,
        feelingComment: String?
    ): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        val existing = dao.getById(sessionId) ?: return AppResult.Error(
            IllegalArgumentException("Session $sessionId introuvable")
        )
        database.withTransaction {
            dao.update(existing.copy(
                status = "COMPLETED",
                result = result,
                scoreText = scoreText,
                feelingRating = feelingRating,
                feelingComment = feelingComment,
                updatedAt = now
            ))
            syncQueueDao.insert(SyncQueueEntity(
                entityType = SyncQueueEntity.ENTITY_TYPE_SESSION,
                entityId = sessionId,
                operation = SyncQueueEntity.OPERATION_UPSERT,
                createdAt = now
            ))
        }
        if (existing.status == "PLANNED") {
            notificationScheduler.cancelPreMatchReminder(sessionId)
        }
        Timber.d("SessionRepository: session %d closed, SyncQueue entry created", sessionId)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Timber.e(e, "SessionRepository: closeSession failed")
        AppResult.Error(e)
    }
}

