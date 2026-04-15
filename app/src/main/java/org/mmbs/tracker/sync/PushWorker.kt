package org.mmbs.tracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.mmbs.tracker.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * WorkManager job that runs a SyncEngine cycle. Scheduled after every local
 * write so that pending rows reach the sheet even if the user closes the app.
 *
 * Uses CoroutineWorker — SyncEngine already runs on Dispatchers.IO internally.
 */
class PushWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sl = ServiceLocator
        if (!sl.isReady) return Result.success()
        return try {
            val res = sl.syncEngine.syncNow()
            when {
                res.hasConflicts -> Result.success() // UI will surface on next open
                res.errors.isNotEmpty() && res.pushed == 0 -> Result.retry()
                else -> Result.success()
            }
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "mmbs-push"

        /** 2-second debounced enqueue — coalesces rapid edits (PRD SYNC-06). */
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<PushWorker>()
                .setInitialDelay(2, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
