package dev.dublinbustracker.app.widget

import android.content.Context

/** Maps each placed widget instance (appWidgetId) to the stop it should show. */
class WidgetStopStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget_stops", Context.MODE_PRIVATE)

    fun stopIdFor(appWidgetId: Int): String? = prefs.getString(key(appWidgetId), null)

    fun save(appWidgetId: Int, stopId: String) {
        prefs.edit().putString(key(appWidgetId), stopId).apply()
    }

    fun remove(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "stop_$appWidgetId"
}
