package com.secondserve.data.wearable

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.secondserve.data.wearable.dto.GameOverPayload
import com.secondserve.data.wearable.dto.ScoreEventPayload
import com.secondserve.data.wearable.dto.toDomain
import com.secondserve.domain.repository.ScoreRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class DataLayerListener : WearableListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DataLayerListenerEntryPoint {
        fun scoreRepository(): ScoreRepository
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
                scoreRepository.updateScore(score)
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
            val score = payload.score_snapshot.toDomain()
            serviceScope.launch {
                scoreRepository.updateScore(score)
                Timber.d("DataLayerListener: ScoreRepository updated via game_over")
            }
        } catch (e: Exception) {
            Timber.e(e, "DataLayerListener: failed to handle game_over")
        }
    }
}
