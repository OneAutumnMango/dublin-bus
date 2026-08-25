package dev.dublinbustracker.app

import android.app.Application
import org.osmdroid.config.Configuration

class BusTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // osmdroid requires a distinct user agent to avoid being throttled by tile servers.
        Configuration.getInstance().userAgentValue = packageName
        container = AppContainer(this)
    }
}
