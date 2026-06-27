package com.secondserve.data.wearable

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.secondserve.data.wearable.dto.GameOverPayload
import com.secondserve.data.wearable.dto.ScoreEventPayload
import com.secondserve.data.wearable.dto.StartSessionRequestPayload
import com.secondserve.data.wearable.dto.toDomain
import com.secondserve.domain.AppResult
import com.secondserve.domain.event.DataLayerEventBus
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.ThirdSetRule
import com.secondserve.domain.repository.ScoreRepository
import com.secondserve.domain.repository.SessionRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.secondserve.data.monitoring.MonitoringClient
import com.secondserve.data.monitoring.MonitoringEventQueue
import com.secondserve.data.monitoring.dto.MonitoringEventDto
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

class DataLayerListener : WearableListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DataLayerListenerEntryPoint {
        fun scoreRepository(): ScoreRepository
        fun dataLayerEventBus(): DataLayerEventBus
        fun sessionRepository(): SessionRepository
        fun dataLayerClient(): DataLayerClient
        fun monitoringClient(): MonitoringClient
        fun monitoringEventQueue(): MonitoringEventQueue
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scoreRepository: ScoreRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DataLayerListenerEntryPoint::class.java
        ).scoreRepository()
    }

    private val dataLayerEventBus: DataLayerEventBus by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DataLayerListenerEntryPoint::class.java
        ).dataLayerEventBus()
    }

    private val sessionRepository: SessionRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DataLayerListenerEntryPoint::class.java
        ).sessionRepository()
    }

    private val dataLayerClient: DataLayerClient by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DataLayerListenerEntryPoint::class.java
        ).dataLayerClient()
    }

    private val monitoringClient: MonitoringClient by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DataLayerListenerEntryPoint::class.java
        ).monitoringClient()
    }

    private val monitoringEventQueue: MonitoringEventQueue by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DataLayerListenerEntryPoint::class.java
        ).monitoringEventQueue()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val json = messageEvent.data.toString(Charsets.UTF_8)
        Timber.d("DataLayerListener: received path=%s payload=%s", messageEvent.path, json)

        when (messageEvent.path) {
            DataLayerClient.PATH_SCORE_EVENT -> handleScoreEvent(json)
            DataLayerClient.PATH_GAME_OVER -> handleGameOver(json)
            DataLayerClient.PATH_CLOSE_SESSION -> handleCloseSession()
            DataLayerClient.PATH_START_SESSION_REQUEST -> handleStartSessionRequest(json)
            DataLayerClient.PATH_MONITOR_EVENT -> handleMonitorEvent(json)
            DataLayerClient.PATH_MONITOR_ERROR -> handleMonitorError(json)
            else -> Timber.d("DataLayerListener: unknown path=%s, ignoring", messageEvent.path)
        }
    }

    private fun handleScoreEvent(json: String) {
        try {
            val payload = moshi.adapter(ScoreEventPayload::class.java).fromJson(json)
            if (payload == null) {
                Timber.e("DataLayerListener: null ScoreEventPayload from JSON")
                return
            }
            val score = payload.score.toDomain()
            serviceScope.launch {
                withContext(NonCancellable) { scoreRepository.updateScore(score) }
                Timber.d("DataLayerListener: ScoreRepository updated via score_event")
            }
        } catch (e: Exception) {
            Timber.e(e, "DataLayerListener: failed to handle score_event")
        }
    }

    private fun handleGameOver(json: String) {
        try {
            val payload = moshi.adapter(GameOverPayload::class.java).fromJson(json)
            if (payload == null) {
                Timber.e("DataLayerListener: null GameOverPayload from JSON")
                return
            }
            val score = payload.scoreSnapshot.toDomain()
            serviceScope.launch {
                withContext(NonCancellable) {
                    scoreRepository.updateScore(score)
                    dataLayerEventBus.emitGameOver(score)
                }
                Timber.d("DataLayerListener: score updated and gameOver emitted")
            }
        } catch (e: Exception) {
            Timber.e(e, "DataLayerListener: failed to handle game_over")
        }
    }

    private fun handleCloseSession() {
        dataLayerEventBus.emitCloseRequest()
        Timber.d("DataLayerListener: close_session request received from Watch")
    }

    private fun handleStartSessionRequest(json: String) {
        try {
            val payload = moshi.adapter(StartSessionRequestPayload::class.java).fromJson(json)
            if (payload == null) {
                Timber.e("DataLayerListener: null StartSessionRequestPayload from JSON")
                return
            }
            val matchFormat = runCatching { MatchFormat.valueOf(payload.matchFormat) }
                .getOrElse { Timber.e("DataLayerListener: unknown matchFormat %s", payload.matchFormat); return }
            val thirdSetRule = runCatching { ThirdSetRule.valueOf(payload.thirdSetRule) }
                .getOrElse { ThirdSetRule.FULL_ADVANTAGE }

            serviceScope.launch {
                val session = Session(
                    surface = payload.surface,
                    format = SessionFormat(matchFormat = matchFormat, thirdSetRule = thirdSetRule),
                    status = SessionStatus.ACTIVE,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Wrap critical operations (createSession + emitStartSession) in NonCancellable
                val sessionId = withContext(NonCancellable) {
                    val result = sessionRepository.createSession(session)
                    if (result is AppResult.Error) {
                        Timber.e(result.exception, "DataLayerListener: failed to create session from watch request")
                        null
                    } else {
                        val id = (result as AppResult.Success).data.id
                        dataLayerEventBus.emitStartSession(id)
                        id
                    }
                }

                if (sessionId == null) return@launch

                // sendStartSession + Intent (fire-and-forget, not critical)
                dataLayerClient.sendStartSession(sessionId, matchFormat, thirdSetRule)
                    .also { if (it is AppResult.Error) Timber.d("DataLayerListener: sendStartSession to watch failed") }

                val intent = Intent("com.secondserve.ACTION_OPEN_MATCH").apply {
                    setClassName(applicationContext.packageName, "${applicationContext.packageName}.OpenMatchAlias")
                    putExtra("sessionId", sessionId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                // Ce service tourne en arrière-plan (réveillé par GMS) : un startActivity() direct
                // est bloqué par le Background Activity Launch quand le téléphone n'est pas au
                // premier plan (usage watch-first, téléphone en poche). On passe par une
                // notification full-screen-intent, chemin de lancement autorisé depuis l'arrière-plan.
                launchViaFullScreenIntent(intent, sessionId)
                Timber.d("DataLayerListener: session %d created from watch request", sessionId)
            }
        } catch (e: Exception) {
            Timber.e(e, "DataLayerListener: failed to handle start_session_request")
        }
    }

    private fun launchViaFullScreenIntent(launchIntent: Intent, sessionId: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                MATCH_LAUNCH_CHANNEL_ID,
                "Démarrage de match",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ouvre le suivi de match quand une session est démarrée depuis la montre"
            }
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            sessionId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, MATCH_LAUNCH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Match en cours")
            .setContentText("Touchez pour suivre le match et recevoir le coaching")
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        nm.notify(MATCH_LAUNCH_NOTIFICATION_ID, notification)
    }

    private fun handleMonitorEvent(json: String) {
        serviceScope.launch {
            try {
                val obj = JSONObject(json)
                val eventType = obj.getString("event_type")
                monitoringEventQueue.enqueue(eventType, emptyMap(), "wear")
            } catch (e: Exception) {
                Timber.e(e, "DataLayerListener: handleMonitorEvent failed")
            }
        }
    }

    private fun handleMonitorError(json: String) {
        serviceScope.launch {
            try {
                val obj = JSONObject(json)
                val payload = obj.getJSONObject("payload")
                monitoringClient.sendEvent(MonitoringEventDto(
                    eventType = "wear.error",
                    payload = mapOf(
                        "error" to payload.optString("error"),
                        "stacktrace" to payload.optString("stacktrace"),
                    ),
                    source = "wear",
                ))
            } catch (e: Exception) {
                Timber.e(e, "DataLayerListener: handleMonitorError failed")
            }
        }
    }

    companion object {
        private const val MATCH_LAUNCH_CHANNEL_ID = "match_launch"
        private const val MATCH_LAUNCH_NOTIFICATION_ID = 4202
    }
}
