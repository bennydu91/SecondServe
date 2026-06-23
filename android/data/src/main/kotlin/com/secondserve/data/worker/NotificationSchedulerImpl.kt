package com.secondserve.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secondserve.domain.notification.NotificationScheduler
import java.util.concurrent.TimeUnit

class NotificationSchedulerImpl(private val context: Context) : NotificationScheduler {
    override fun scheduleDaily() = schedule(1, TimeUnit.DAYS)
    override fun scheduleEvery2Days() = schedule(2, TimeUnit.DAYS)
    override fun scheduleWeekly() = schedule(7, TimeUnit.DAYS)
    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
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
