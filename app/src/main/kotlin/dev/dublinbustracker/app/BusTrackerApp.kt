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
        runCatching {
            Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = packageName
        }
        container = AppContainer(this)
        scheduleWidgetRefresh()
    }

    private fun scheduleWidgetRefresh() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork("widget-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
