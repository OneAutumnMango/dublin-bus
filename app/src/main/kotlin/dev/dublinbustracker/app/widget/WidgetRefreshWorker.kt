package dev.dublinbustracker.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Refreshes every placed widget's arrivals. Android/WorkManager won't run this more often than ~15min. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val widget = BusStopWidget()
        GlanceAppWidgetManager(applicationContext).getGlanceIds(BusStopWidget::class.java).forEach {
            widget.update(applicationContext, it)
        }
        return Result.success()
    }
}
