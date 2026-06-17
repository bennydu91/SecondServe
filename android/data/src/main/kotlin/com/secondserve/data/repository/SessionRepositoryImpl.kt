package com.secondserve.data.repository

import com.secondserve.data.local.dao.SessionDao
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
    private val dao: SessionDao
) : SessionRepository {

    override suspend fun createSession(session: Session): AppResult<Session> = try {
        val id = dao.insert(session.toEntity())
        Timber.d("SessionRepository: session créée id=%d", id)
        AppResult.Success(session.copy(id = id))
    } catch (e: Exception) {
        Timber.e(e, "SessionRepository: createSession failed")
        AppResult.Error(e)
    }

    override fun getAllSessions(): Flow<List<Session>> =
        dao.getAllSessions().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSessionById(id: Long): Session? =
        dao.getById(id)?.toDomain()
}
