package dev.dublinbustracker.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.dublinbustracker.app.widget.WidgetRefreshWorker
import org.osmdroid.config.Configuration
import java.util.concurrent.TimeUnit

class BusTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // osmdroid requires a distinct user agent to avoid being throttled by tile servers.
        Configuration.getInstance().userAgentValue = packageName
        container = AppContainer(this)
        scheduleWidgetRefresh()
    }

    private fun scheduleWidgetRefresh() {
        // WorkManager won't run periodic work more often than 15 minutes; the widget's own
        // Refresh action and the 60s in-app polling cover the faster-moving case.
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("widget-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
