package com.secondserve.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.secondserve.domain.notification.NotificationScheduler
import timber.log.Timber
import java.util.concurrent.TimeUnit

class NotificationSchedulerImpl(private val context: Context) : NotificationScheduler {
    override fun scheduleDaily() = schedule(1, TimeUnit.DAYS)
    override fun scheduleEvery2Days() = schedule(2, TimeUnit.DAYS)
    override fun scheduleWeekly() = schedule(7, TimeUnit.DAYS)
    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    override fun schedulePreMatchReminder(sessionId: Long, triggerAtMs: Long) {
        val delayMs = triggerAtMs - System.currentTimeMillis()
        if (delayMs <= 0L) {
            Timber.w("schedulePreMatchReminder: délai négatif pour session %d (triggerAt=%d) — reminder ignoré", sessionId, triggerAtMs)
            return
        }
        val data = workDataOf(PreMatchReminderWorker.KEY_SESSION_ID to sessionId)
        val request = OneTimeWorkRequestBuilder<PreMatchReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pre_match_reminder_$sessionId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelPreMatchReminder(sessionId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("pre_match_reminder_$sessionId")
    }

    private fun schedule(interval: Long, unit: TimeUnit) {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(interval, unit)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "daily_coaching_notification"
    }
}
