package com.secondserve.wear

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.secondserve.data.wearable.DataLayerClient
import com.secondserve.data.wearable.dto.StartSessionPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import timber.log.Timber

class WearDataLayerListener : WearableListenerService() {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val json = messageEvent.data.toString(Charsets.UTF_8)
        Timber.d("WearDataLayerListener: received path=%s", messageEvent.path)

        when (messageEvent.path) {
            DataLayerClient.PATH_START_SESSION -> handleStartSession(json)
            else -> Timber.d("WearDataLayerListener: unknown path=%s, ignoring", messageEvent.path)
        }
    }

    private fun handleStartSession(json: String) {
        try {
            val payload = moshi.adapter(StartSessionPayload::class.java).fromJson(json)
            if (payload == null) {
                Timber.e("WearDataLayerListener: null StartSessionPayload")
                return
            }
            val intent = Intent(applicationContext, WearActivity::class.java).apply {
                putExtra("matchFormat", payload.matchFormat)
                putExtra("thirdSetRule", payload.thirdSetRule)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            applicationContext.startActivity(intent)
            Timber.d("WearDataLayerListener: launching WearActivity with format=%s", payload.matchFormat)
        } catch (e: Exception) {
            Timber.e(e, "WearDataLayerListener: failed to handle start_session")
        }
    }
}
