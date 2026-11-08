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
import androidx.lifecycle.lifecycleScope
import dev.dublinbustracker.app.BusTrackerApp
import dev.dublinbustracker.app.ui.BusTrackerTheme
import dev.dublinbustracker.data.WidgetConfig
import kotlinx.coroutines.launch

/** Shown when a widget is added (or reconfigured): the user chooses what it shows. */
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
            BusTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                    WidgetSetup(container, onDone = ::onConfigured, onCancel = ::finish)
                }
            }
        }
    }

    private fun onConfigured(config: WidgetConfig) {
        WidgetConfigStore(this).save(appWidgetId, config)
        lifecycleScope.launch {
            // The widget may already have drawn itself once, before it was set up: redraw it now, and have a job fetch its data
            // (here would hold the setup screen open for the network).
            runCatching { redrawAllWidgets(this@WidgetConfigActivity) }
            WidgetRefresh.refreshNow(this@WidgetConfigActivity)
            WidgetRefresh.ensureScheduled(this@WidgetConfigActivity)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
