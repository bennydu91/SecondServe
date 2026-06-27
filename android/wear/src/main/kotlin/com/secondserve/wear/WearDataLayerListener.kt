package com.secondserve.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
            // On NE fait PAS un startActivity() direct : ce service tourne en arrière-plan
            // (réveillé par GMS), donc l'OS bloque le Background Activity Launch
            // ("BAL_BLOCK", cf. logcat). On passe par une notification full-screen-intent,
            // chemin de lancement privilégié que le système autorise depuis l'arrière-plan.
            launchViaFullScreenIntent(intent)
            Timber.d("WearDataLayerListener: full-screen-intent posted for format=%s", payload.matchFormat)
        } catch (e: Exception) {
            Timber.e(e, "WearDataLayerListener: failed to handle start_session")
        }
    }

    private fun launchViaFullScreenIntent(launchIntent: Intent) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Démarrage de match",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ouvre le suivi de score quand un match est démarré depuis le téléphone"
            }
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Match en cours")
            .setContentText("Touchez pour suivre le score")
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "match_launch"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_CODE = 4201
    }
}
