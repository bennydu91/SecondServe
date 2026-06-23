package com.secondserve.data.repository

import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.db.entity.CoachingAnalysisEntity
import com.secondserve.data.local.db.entity.CoachingCacheEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.CoachingAnalysis
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.repository.CoachingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CoachingRepositoryImpl @Inject constructor(
    private val dao: CoachingCacheDao,
    private val analysisDao: CoachingAnalysisDao
) : CoachingRepository {

    override suspend fun getCachedAdvice(matchId: Long, pattern: MatchPattern): CoachingCacheEntry? =
        dao.getEntry(matchId, pattern.name)?.toDomain()

    override suspend fun saveAdvice(matchId: Long, pattern: MatchPattern, content: String) {
        dao.upsertEntry(
            CoachingCacheEntity(
                matchId = matchId,
                pattern = pattern.name,
                content = content,
                generatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun markMatchEntriesStale(matchId: Long) =
        dao.markAllStale(matchId)

    override suspend fun getAdvicesForSession(sessionId: Long): List<CoachingCacheEntry> =
        dao.getAllForMatch(sessionId).map { it.toDomain() }

    override suspend fun saveAnalysis(sessionId: Long, content: String): AppResult<CoachingAnalysis> = try {
        val entity = CoachingAnalysisEntity(
            sessionId = sessionId,
            content = content,
            generatedAt = System.currentTimeMillis()
        )
        val id = analysisDao.insert(entity)
        if (id == -1L) {
            // IGNORE strategy: row already exists — return the existing analysis
            val existing = analysisDao.getBySessionId(sessionId)
            AppResult.Success((existing ?: entity).toDomain())
        } else {
            AppResult.Success(entity.copy(id = id).toDomain())
        }
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun getAnalysisForSession(sessionId: Long): CoachingAnalysis? =
        analysisDao.getBySessionId(sessionId)?.toDomain()

    override fun observeAnalysisForSession(sessionId: Long): Flow<CoachingAnalysis?> =
        analysisDao.observeBySessionId(sessionId).map { it?.toDomain() }
}
