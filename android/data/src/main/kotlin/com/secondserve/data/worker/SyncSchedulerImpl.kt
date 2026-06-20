package com.secondserve.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.secondserve.domain.sync.SyncScheduler
import java.util.concurrent.TimeUnit

class SyncSchedulerImpl(private val context: Context) : SyncScheduler {
    override fun scheduleImmediate() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("secondserve_sync", ExistingWorkPolicy.KEEP, request)
    }
}
