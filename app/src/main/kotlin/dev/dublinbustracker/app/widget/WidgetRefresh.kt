package dev.dublinbustracker.app.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * How often a placed widget fetches fresh arrivals. The server itself only learns of new positions about
 * once a minute, and Android won't wake the app more often than this cheaply, so two minutes is the balance.
 */
private const val REFRESH_MINUTES = 2L
private const val WORK_NAME = "widget-refresh"

object WidgetRefresh {
    /** Makes sure a refresh is queued, without disturbing one that already is. */
    fun ensureScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

    internal fun scheduleNext(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

    /** Stops the polling: there are no widgets left to keep fresh. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(REFRESH_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}

/**
 * Refreshes every placed widget, then queues itself again. It lives in WorkManager, so it carries on after the app
 * is closed or the phone restarts, and it stops by itself once the last widget has been removed.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val widgets = runCatching { refreshAllWidgets(applicationContext) }.getOrDefault(0)
        if (widgets > 0) WidgetRefresh.scheduleNext(applicationContext)
        return Result.success()
    }
}
