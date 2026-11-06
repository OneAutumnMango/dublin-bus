package dev.dublinbustracker.app.widget

import android.content.Context
import androidx.core.content.edit
import dev.dublinbustracker.data.WidgetConfig

/** What each placed widget instance (appWidgetId) shows. */
class WidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("widget_stops", Context.MODE_PRIVATE)

    fun load(appWidgetId: Int): WidgetConfig? =
        WidgetConfig.decode(prefs.getString(key(appWidgetId), null))
            // Widgets placed by earlier builds only remember a stop.
            ?: legacy.getString(key(appWidgetId), null)?.let { WidgetConfig(stopId = it) }

    fun save(appWidgetId: Int, config: WidgetConfig) {
        prefs.edit { putString(key(appWidgetId), config.encode()) }
    }

    fun remove(appWidgetId: Int) {
        prefs.edit { remove(key(appWidgetId)) }
        legacy.edit { remove(key(appWidgetId)) }
    }

    private fun key(appWidgetId: Int) = "stop_$appWidgetId"
}
