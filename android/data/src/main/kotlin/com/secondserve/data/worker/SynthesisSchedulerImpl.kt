package com.secondserve.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.secondserve.domain.synthesis.SynthesisScheduler
import java.util.concurrent.TimeUnit

class SynthesisSchedulerImpl(private val context: Context) : SynthesisScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<SynthesisWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("synthesis_check", ExistingWorkPolicy.KEEP, request)
    }
}
