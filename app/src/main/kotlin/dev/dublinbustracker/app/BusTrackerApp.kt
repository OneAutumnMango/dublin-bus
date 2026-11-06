package dev.dublinbustracker.app

import android.app.Application
import dev.dublinbustracker.app.map.MapCache
import dev.dublinbustracker.app.widget.WidgetRefresh

class BusTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching { MapCache.init(this) }
        container = AppContainer(this)
        scheduleWidgetRefresh()
    }

    // Opening the app is also a chance to restart the widgets' polling if the system had stopped it (e.g. after a force stop).
    private fun scheduleWidgetRefresh() {
        runCatching { WidgetRefresh.ensureScheduled(this) }
    }
}
