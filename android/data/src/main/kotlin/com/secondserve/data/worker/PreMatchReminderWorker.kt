package com.secondserve.data.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.remote.api.VpsApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class PreMatchReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vpsApiService: VpsApiService,
    private val playerProfileDao: PlayerProfileDao,
    private val workAxisDao: WorkAxisDao,
    private val playerDataStore: PlayerDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId == -1L) return Result.success()

        val content = fetchVpsContentOrFallback(sessionId)
        if (content.isNullOrBlank()) return Result.success()

        postNotification(content)
        return Result.success()
    }

    private suspend fun fetchVpsContentOrFallback(sessionId: Long): String? {
        return try {
            val response = vpsApiService.getPendingNotification(sessionId)
            response.content.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.d("PreMatchReminderWorker: VPS unavailable — using fallback")
            buildFallback()
        }
    }

    private suspend fun buildFallback(): String? {
        val surface = try { playerProfileDao.getProfile()?.preferredSurfaces } catch (e: Exception) { null }
        val axis = try {
            workAxisDao.getAllTitles().firstOrNull { it.isNotBlank() }
        } catch (e: Exception) { null }
        return when {
            axis != null && surface != null ->
                "Rappel pré-match : concentre-toi sur « $axis » (surface : $surface)."
            axis != null ->
                "Rappel pré-match : concentre-toi sur « $axis »."
            surface != null ->
                "Rappel pré-match : match sur $surface — sois prêt !"
            else -> "Rappel : tu as un match bientôt. Reste focus !"
        }
    }

    private fun postNotification(content: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Rappel pré-match")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val granted = ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            try {
                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Timber.d("PreMatchReminderWorker: SecurityException on notify: %s", e.message)
            }
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val CHANNEL_ID = "coaching_notifications"
        const val NOTIFICATION_ID = 1002
    }
}
