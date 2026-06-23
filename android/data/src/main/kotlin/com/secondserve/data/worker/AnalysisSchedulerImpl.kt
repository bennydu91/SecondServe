package com.secondserve.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.secondserve.domain.analysis.AnalysisScheduler
import java.util.concurrent.TimeUnit

class AnalysisSchedulerImpl(private val context: Context) : AnalysisScheduler {
    override fun schedule(sessionId: Long) {
        val request = OneTimeWorkRequestBuilder<PostMatchAnalysisWorker>()
            .setInputData(workDataOf(PostMatchAnalysisWorker.KEY_SESSION_ID to sessionId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "post_match_analysis_$sessionId",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
