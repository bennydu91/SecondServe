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
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.VpsMistralEngine
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingSynthesisDao
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.domain.AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionDao: SessionDao,
    private val playerProfileDao: PlayerProfileDao,
    private val workAxisDao: WorkAxisDao,
    private val synthesisDao: CoachingSynthesisDao,
    private val analysisDao: CoachingAnalysisDao,
    private val playerDataStore: PlayerDataStore,
    @VpsMistralEngine private val vpsMistralEngine: InferenceEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()

        val silentUntil = playerDataStore.getSilentModeUntil()
        if (silentUntil > 0L) {
            if (now < silentUntil) {
                Timber.d("NotificationWorker: silent mode active until %d", silentUntil)
                return Result.success()
            }
            playerDataStore.saveSilentModeUntil(0L)
        }

        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val recentCount = try {
            sessionDao.countCompletedSince(thirtyDaysAgo)
        } catch (e: Exception) {
            Timber.e(e, "NotificationWorker: error checking sessions")
            return Result.retry()
        }
        if (recentCount == 0) {
            Timber.d("NotificationWorker: no sessions in 30 days — skipping")
            return Result.success()
        }

        val surface = try { playerProfileDao.getProfile()?.preferredSurfaces } catch (e: Exception) { null }
        val axes = try { workAxisDao.getAllTitles() } catch (e: Exception) { emptyList() }
        val recentResult = try {
            sessionDao.getCompletedSince(now - 7L * 24 * 60 * 60 * 1000)
                .firstOrNull()?.result
        } catch (e: Exception) { null }

        val content = generateContent(surface, axes, recentResult)
        if (content.isNullOrBlank()) {
            Timber.d("NotificationWorker: no content — skipping notification")
            return Result.success()
        }

        postNotification(content)
        return Result.success()
    }

    internal suspend fun generateContent(
        surface: String?,
        axes: List<String>,
        recentResult: String?
    ): String? {
        val synthesis = try { synthesisDao.getLatest()?.content } catch (e: Exception) { null }
        val analysis = try { analysisDao.getMostRecent()?.content } catch (e: Exception) { null }
        val sourceContent = synthesis ?: analysis

        if (sourceContent != null) {
            val prompt = buildPrompt(sourceContent, surface, axes)
            val result = try {
                vpsMistralEngine.generate(prompt)
            } catch (e: Exception) {
                Timber.e(e, "NotificationWorker: VPS call threw exception")
                null
            }
            if (result is AppResult.Success && result.data.isNotBlank()) {
                return result.data
            }
            Timber.d("NotificationWorker: VPS failed or empty — using fallback")
        }

        return buildFallbackContent(surface, axes, recentResult)
    }

    internal fun buildPrompt(source: String, surface: String?, axes: List<String>): String {
        val axesText = axes.joinToString(", ").ifEmpty { "aucun" }
        val surfaceText = surface?.ifBlank { null } ?: "non définie"
        return "Génère un conseil de coaching tennis bref (2-3 phrases max) personnalisé. " +
               "Surface de prédilection : $surfaceText. Axes de travail actifs : $axesText. " +
               "Contexte coaching : ${source.take(500)}"
    }

    internal fun buildFallbackContent(
        surface: String?,
        axes: List<String>,
        recentResult: String?
    ): String? {
        val parts = mutableListOf<String>()
        if (!surface.isNullOrBlank()) parts.add("Surface : $surface")
        val firstAxis = axes.firstOrNull { it.isNotBlank() }
        if (firstAxis != null) parts.add("Axe du moment : $firstAxis")
        if (!recentResult.isNullOrBlank()) parts.add("Résultat récent : $recentResult")
        return if (parts.isNotEmpty()) parts.joinToString(" | ") else null
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(content: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Conseil du jour")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val granted = ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            try {
                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
                Timber.d("NotificationWorker: notification posted")
            } catch (e: SecurityException) {
                Timber.w(e, "NotificationWorker: permission revoked between check and notify")
            }
        } else {
            Timber.d("NotificationWorker: POST_NOTIFICATIONS not granted")
        }
    }

    companion object {
        const val CHANNEL_ID = "coaching_notifications"
        const val NOTIFICATION_ID = 1001
    }
}
