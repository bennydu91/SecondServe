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
import com.secondserve.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val syncQueueDao: SyncQueueDao,
    private val database: SecondServeDatabase
) : SessionRepository {

    override suspend fun createSession(session: Session): AppResult<Session> = try {
        val id = dao.insert(session.toEntity())
        Timber.d("SessionRepository: session créée id=%d", id)
        AppResult.Success(session.copy(id = id))
    } catch (e: Exception) {
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
        Timber.d("SessionRepository: session %d closed, SyncQueue entry created", sessionId)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "SessionRepository: closeSession failed")
        AppResult.Error(e)
    }
}

