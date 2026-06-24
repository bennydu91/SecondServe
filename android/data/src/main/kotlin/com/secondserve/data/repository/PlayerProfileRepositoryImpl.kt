package com.secondserve.data.repository

import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.db.entity.PlayerProfileEntity
import com.secondserve.data.local.db.entity.RankingHistoryEntity
import com.secondserve.data.local.db.entity.toDomain
import com.secondserve.data.local.db.entity.toPreferredSurfacesList
import com.secondserve.data.local.db.entity.toPreferredSurfacesString
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.ProfileDetailsRequest
import com.secondserve.data.remote.api.dto.RankingRequest
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchContextProfile
import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.WorkAxisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class PlayerProfileRepositoryImpl(
    private val dao: PlayerProfileDao,
    private val vpsApiService: VpsApiService,
    private val workAxisRepository: WorkAxisRepository
) : PlayerProfileRepository {

    private val profileWriteMutex = Mutex()

    override suspend fun getProfile(): AppResult<PlayerProfile?> = try {
        val entity = dao.getProfile()
        AppResult.Success(entity?.toDomain())
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppResult.Error(e)
    }

    override suspend fun saveRanking(series: String, points: Int): AppResult<Unit> = try {
        profileWriteMutex.withLock {
            val now = System.currentTimeMillis()
            val current = dao.getProfile()
            dao.saveProfileAndHistory(
                PlayerProfileEntity(
                    id = 1,
                    currentSeries = series,
                    currentPoints = points,
                    playStyle = current?.playStyle,
                    preferredSurfaces = current?.preferredSurfaces,
                    coachInstruction1 = current?.coachInstruction1,
                    coachInstruction2 = current?.coachInstruction2,
                    coachInstruction3 = current?.coachInstruction3,
                    updatedAt = now
                ),
                RankingHistoryEntity(series = series, points = points, recordedAt = now, updatedAt = now)
            )
        }
        try {
            vpsApiService.saveRanking(RankingRequest(series = series, points = points))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "VPS ranking sync failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppResult.Error(e)
    }

    override fun getRankingHistory(): Flow<List<RankingEntry>> =
        dao.getRankingHistory().map { entities -> entities.map { it.toDomain() } }

    override suspend fun buildMatchContextProfile(): MatchContextProfile {
        val profile = dao.getProfile()
        // NFR-C3/S5: fftLicenseNumber is excluded — stored in EncryptedSharedPreferences only,
        // never included in VPS-bound context.
        return MatchContextProfile(
            fftSeries = profile?.currentSeries,
            playStyle = profile?.playStyle,
            preferredSurfaces = profile?.preferredSurfaces.toPreferredSurfacesList(),
            coachInstructions = listOfNotNull(
                profile?.coachInstruction1?.takeIf { it.isNotBlank() },
                profile?.coachInstruction2?.takeIf { it.isNotBlank() },
                profile?.coachInstruction3?.takeIf { it.isNotBlank() }
            ),
            activeWorkAxes = workAxisRepository.getActiveWorkAxesTitles()
        )
    }

    override suspend fun saveProfileDetails(
        playStyle: String?,
        preferredSurfaces: List<String>,
        coachInstruction1: String?,
        coachInstruction2: String?,
        coachInstruction3: String?
    ): AppResult<Unit> = try {
        profileWriteMutex.withLock {
            val now = System.currentTimeMillis()
            val current = dao.getProfile()
            dao.upsertProfile(
                PlayerProfileEntity(
                    id = 1,
                    currentSeries = current?.currentSeries,
                    currentPoints = current?.currentPoints,
                    playStyle = playStyle,
                    preferredSurfaces = preferredSurfaces.toPreferredSurfacesString(),
                    coachInstruction1 = coachInstruction1?.takeIf { it.isNotBlank() },
                    coachInstruction2 = coachInstruction2?.takeIf { it.isNotBlank() },
                    coachInstruction3 = coachInstruction3?.takeIf { it.isNotBlank() },
                    updatedAt = now
                )
            )
        }
        try {
            vpsApiService.updateProfileDetails(
                ProfileDetailsRequest(
                    playStyle = playStyle,
                    preferredSurfaces = preferredSurfaces.toPreferredSurfacesString(),
                    coachInstruction1 = coachInstruction1?.takeIf { it.isNotBlank() },
                    coachInstruction2 = coachInstruction2?.takeIf { it.isNotBlank() },
                    coachInstruction3 = coachInstruction3?.takeIf { it.isNotBlank() }
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "VPS profile details sync failed — local save succeeded")
        }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AppResult.Error(e)
    }

    override fun observeMatchSessionCount(): Flow<Int> = flowOf(0)
    // TODO(Story 2.3): replace with SessionDao.countMatchSessions() when sessions table exists
}
