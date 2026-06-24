package com.secondserve.data.worker

import android.Manifest
import android.annotation.SuppressLint
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
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.remote.api.VpsApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.HttpException
import timber.log.Timber

@HiltWorker
class PreMatchReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vpsApiService: VpsApiService,
    private val playerProfileDao: PlayerProfileDao,
    private val workAxisDao: WorkAxisDao,
    private val playerDataStore: PlayerDataStore,
    private val sessionDao: SessionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId == -1L) return Result.success()

        val content = fetchVpsContentOrFallback(sessionId)
        if (content.isNullOrBlank()) return Result.success()

        val notificationId = (NOTIFICATION_ID_BASE + sessionId.rem(100_000L)).toInt()
        postNotification(content, notificationId)
        return Result.success()
    }

    private suspend fun fetchVpsContentOrFallback(sessionId: Long): String? {
        return try {
            val response = vpsApiService.getPendingNotification(sessionId)
            response.content.takeIf { it.isNotBlank() }
        } catch (e: HttpException) {
            Timber.d("PreMatchReminderWorker: contenu VPS non disponible (HTTP %d) — fallback local", e.code())
            buildFallback(sessionId)
        } catch (e: Exception) {
            Timber.d("PreMatchReminderWorker: VPS réseau indisponible — fallback local")
            buildFallback(sessionId)
        }
    }

    private suspend fun buildFallback(sessionId: Long): String? {
        val session = try { sessionDao.getById(sessionId) } catch (e: Exception) { null }
        val opponent = session?.opponent
        val profile = try { playerProfileDao.getProfile() } catch (e: Exception) { null }
        val surface = profile?.preferredSurfaces
        val ranking = profile?.currentSeries
        val axis = try {
            workAxisDao.getAllTitles().firstOrNull { it.isNotBlank() }
        } catch (e: Exception) { null }

        val parts = mutableListOf<String>()
        opponent?.let { parts.add("Adversaire : $it") }
        axis?.let { parts.add("Axe de travail : $it") }
        ranking?.let { parts.add("Classement : $it") }

        return when {
            parts.isNotEmpty() && surface != null ->
                "Rappel pré-match (${surface}) : ${parts.joinToString(" | ")}. Reste focus !"
            parts.isNotEmpty() ->
                "Rappel pré-match : ${parts.joinToString(" | ")}. Reste focus !"
            surface != null ->
                "Rappel pré-match : match sur $surface — sois prêt !"
            else -> "Rappel : tu as un match bientôt. Reste focus !"
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(content: String, notificationId: Int) {
        try {
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
                NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
            }
        } catch (e: Exception) {
            Timber.d("PreMatchReminderWorker: notification failed: %s", e.message)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val CHANNEL_ID = "coaching_notifications"
        private const val NOTIFICATION_ID_BASE = 2000
    }
}
