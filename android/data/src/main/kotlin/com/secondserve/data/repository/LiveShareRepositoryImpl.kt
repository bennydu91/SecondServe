package com.secondserve.data.repository

import com.secondserve.data.local.dao.LiveShareDao
import com.secondserve.data.local.db.entity.LiveShareEntity
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.CreateShareRequest
import com.secondserve.data.remote.api.dto.LiveScoreUpdateRequest
import com.secondserve.data.remote.api.dto.LiveSetResultDto
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.LiveShareContext
import com.secondserve.domain.model.LiveShareInfo
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.repository.LiveShareRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveShareRepositoryImpl @Inject constructor(
    private val dao: LiveShareDao,
    private val vpsApiService: VpsApiService
) : LiveShareRepository {

    override suspend fun getOrCreateShare(sessionId: Long): AppResult<LiveShareInfo> {
        dao.getBySessionId(sessionId)?.let {
            return AppResult.Success(LiveShareInfo(token = it.token, url = it.url))
        }
        return try {
            val response = vpsApiService.createLiveShare(CreateShareRequest(sessionId))
            dao.insert(
                LiveShareEntity(
                    sessionId = sessionId,
                    token = response.token,
                    url = response.url,
                    createdAt = System.currentTimeMillis()
                )
            )
            AppResult.Success(LiveShareInfo(token = response.token, url = response.url))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "LiveShareRepository: création du lien échouée")
            AppResult.Error(e)
        }
    }

    override suspend fun getCachedShare(sessionId: Long): LiveShareInfo? =
        dao.getBySessionId(sessionId)?.let { LiveShareInfo(token = it.token, url = it.url) }

    override suspend fun pushScore(sessionId: Long, score: MatchScore, context: LiveShareContext) {
        try {
            vpsApiService.pushLiveScore(
                sessionId,
                LiveScoreUpdateRequest(
                    completedSets = score.completedSets.map { LiveSetResultDto(it.gamesA, it.gamesB) },
                    currentSetGamesA = score.currentSetGamesA,
                    currentSetGamesB = score.currentSetGamesB,
                    currentSetPointLog = score.currentSetPointLog.map { it.name },
                    currentGamePointsA = score.currentGamePointsA.name,
                    currentGamePointsB = score.currentGamePointsB.name,
                    tieBreakPointsA = score.tieBreakPointsA,
                    tieBreakPointsB = score.tieBreakPointsB,
                    isTieBreak = score.isTieBreak,
                    isSuperTieBreak = score.isSuperTieBreak,
                    isMatchOver = score.isMatchOver,
                    matchWinner = score.matchWinner?.name,
                    playerAName = context.playerAName,
                    playerBName = context.playerBName,
                    surface = context.surface,
                    tournament = context.tournament,
                    competitionType = context.competitionType,
                    startedAt = context.startedAt
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "LiveShareRepository: poussée du score échouée — ignorée (auto-réparant au prochain point)")
        }
    }
}
