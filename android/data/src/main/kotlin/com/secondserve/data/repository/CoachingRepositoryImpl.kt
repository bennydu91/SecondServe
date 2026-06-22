package com.secondserve.data.repository

import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.db.entity.CoachingCacheEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.MatchPattern
import com.secondserve.domain.repository.CoachingRepository
import javax.inject.Inject

class CoachingRepositoryImpl @Inject constructor(
    private val dao: CoachingCacheDao
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
}
