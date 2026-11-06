package dev.dublinbustracker.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.dublinbustracker.app.BusTrackerApp
import dev.dublinbustracker.app.search.SearchScreen
import dev.dublinbustracker.core.Stop
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val container = (application as BusTrackerApp).container
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    SearchScreen(container.apiClient, container.favouritesRepository, container.recentStops, onStopSelected = ::onStopChosen)
                }
            }
        }
    }

    private fun onStopChosen(stop: Stop) {
        WidgetStopStore(this).save(appWidgetId, stop.id)
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                BusStopWidget().update(this@WidgetConfigActivity, glanceId)
            }
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
