package com.secondserve.data.wearable

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.secondserve.data.wearable.dto.GameOverPayload
import com.secondserve.data.wearable.dto.ScoreEventPayload
import com.secondserve.data.wearable.dto.StartSessionPayload
import com.secondserve.data.wearable.dto.StartSessionRequestPayload
import com.secondserve.data.wearable.dto.toDto
import com.secondserve.domain.AppResult
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.ThirdSetRule
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataLayerClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    companion object {
        const val PATH_SCORE_EVENT = "/secondserve/score_event"
        const val PATH_GAME_OVER = "/secondserve/game_over"
        const val PATH_CLOSE_SESSION = "/secondserve/close_session"
        const val PATH_START_SESSION = "/secondserve/start_session"
        const val PATH_START_SESSION_REQUEST = "/secondserve/start_session_request"
        const val PATH_MONITOR_EVENT = "/secondserve/monitor_event"
        const val PATH_MONITOR_ERROR = "/secondserve/monitor_error"
    }

    suspend fun sendScoreEvent(score: MatchScore): AppResult<Unit> {
        val payload = ScoreEventPayload(ts = System.currentTimeMillis(), score = score.toDto())
        val json = moshi.adapter(ScoreEventPayload::class.java).toJson(payload)
        return sendMessage(PATH_SCORE_EVENT, json.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendGameOver(score: MatchScore): AppResult<Unit> {
        val payload = GameOverPayload(ts = System.currentTimeMillis(), scoreSnapshot = score.toDto())
        val json = moshi.adapter(GameOverPayload::class.java).toJson(payload)
        return sendMessage(PATH_GAME_OVER, json.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendCloseRequest(): AppResult<Unit> {
        val payload = """{"type":"CLOSE_SESSION","ts":${System.currentTimeMillis()}}"""
        return sendMessage(PATH_CLOSE_SESSION, payload.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendStartSession(
        sessionId: Long,
        matchFormat: MatchFormat,
        thirdSetRule: ThirdSetRule
    ): AppResult<Unit> {
        val payload = StartSessionPayload(
            ts = System.currentTimeMillis(),
            sessionId = sessionId,
            matchFormat = matchFormat.name,
            thirdSetRule = thirdSetRule.name
        )
        val json = moshi.adapter(StartSessionPayload::class.java).toJson(payload)
        return sendMessage(PATH_START_SESSION, json.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendStartSessionRequest(
        matchFormat: MatchFormat,
        thirdSetRule: ThirdSetRule
    ): AppResult<Unit> {
        val payload = StartSessionRequestPayload(
            ts = System.currentTimeMillis(),
            matchFormat = matchFormat.name,
            thirdSetRule = thirdSetRule.name
        )
        val json = moshi.adapter(StartSessionRequestPayload::class.java).toJson(payload)
        return sendMessage(PATH_START_SESSION_REQUEST, json.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendMonitorEvent(eventType: String, payload: Map<String, String>): AppResult<Unit> {
        val json = """{"event_type":"$eventType","payload":${payloadToJson(payload)},"source":"wear"}"""
        return sendMessage(PATH_MONITOR_EVENT, json.toByteArray(Charsets.UTF_8))
    }

    suspend fun sendMonitorError(error: String, stacktrace: String): AppResult<Unit> {
        val escaped = stacktrace.replace("\"", "'").take(2000)
        val json = """{"event_type":"wear.error","payload":{"error":"$error","stacktrace":"$escaped"},"source":"wear"}"""
        return sendMessage(PATH_MONITOR_ERROR, json.toByteArray(Charsets.UTF_8))
    }

    private fun payloadToJson(map: Map<String, String>): String =
        "{" + map.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" } + "}"

    private suspend fun sendMessage(path: String, payload: ByteArray): AppResult<Unit> {
        return try {
            val nodeId = getPhoneNodeId()
            if (nodeId == null) {
                Timber.d("DataLayerClient: no connected phone node for path=%s", path)
                return AppResult.Error(Exception("No connected phone node"))
            }
            Wearable.getMessageClient(context).sendMessage(nodeId, path, payload).await()
            Timber.d("DataLayerClient: sent %s (%d bytes)", path, payload.size)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "DataLayerClient: sendMessage failed for path=%s", path)
            AppResult.Error(e)
        }
    }

    private suspend fun getPhoneNodeId(): String? {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.firstOrNull()?.id
        } catch (e: Exception) {
            Timber.e(e, "DataLayerClient: getPhoneNodeId failed")
            null
        }
    }
}
