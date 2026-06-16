package com.secondserve.data.repository

import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.RankingRequest
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.repository.PlayerProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class PlayerProfileRepositoryImpl(
    private val dao: PlayerProfileDao,
    private val vpsApiService: VpsApiService
) : PlayerProfileRepository {

    override suspend fun getProfile(): AppResult<PlayerProfile?> = try {
        val entity = dao.getProfile()
        AppResult.Success(entity?.toDomain())
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override suspend fun saveRanking(series: String, points: Int): AppResult<Unit> = try {
        val now = System.currentTimeMillis()
        dao.saveProfileAndHistory(
            PlayerProfileEntity(id = 1, currentSeries = series, currentPoints = points, updatedAt = now),
            RankingHistoryEntity(series = series, points = points, recordedAt = now, updatedAt = now)
        )
        try {
            vpsApiService.saveRanking(RankingRequest(series = series, points = points))
        } catch (e: Exception) {
            Timber.w(e, "VPS ranking sync failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(e)
    }

    override fun getRankingHistory(): Flow<List<RankingEntry>> =
        dao.getRankingHistory().map { entities -> entities.map { it.toDomain() } }

    override suspend fun buildMatchContextProfile(): MatchContextProfile {
        val profile = dao.getProfile()
        return MatchContextProfile(fftSeries = profile?.currentSeries)
    }
}
