package dev.dublinbustracker.app.widget

import android.content.Context
import androidx.core.content.edit
import dev.dublinbustracker.core.Arrival
import org.json.JSONArray
import org.json.JSONObject
import dev.dublinbustracker.data.WidgetConfig

/** What each placed widget instance (appWidgetId) shows. */
class WidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)
    private val lastGood = context.getSharedPreferences("widget_last_good", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("widget_stops", Context.MODE_PRIVATE)

    fun load(appWidgetId: Int): WidgetConfig? =
        WidgetConfig.decode(prefs.getString(key(appWidgetId), null))
            // Widgets placed by earlier builds only remember a stop.
            ?: legacy.getString(key(appWidgetId), null)?.let { WidgetConfig(stopId = it) }

    fun save(appWidgetId: Int, config: WidgetConfig) {
        prefs.edit { putString(key(appWidgetId), config.encode()) }
    }

    /** The last arrivals a widget showed, kept on disk so a refresh that fails after the app was idle (process gone) still has something to show. */
    fun saveLastGood(appWidgetId: Int, view: WidgetView.Arrivals) {
        val json = JSONObject()
            .put("title", view.title)
            .put("subtitle", view.subtitle)
            .put("refreshedAt", view.refreshedAt)
            .put("arrivals", JSONArray(view.arrivals.map { JSONObject().put("r", it.routeShortName).put("d", it.destination).put("m", it.dueMinutes) }))
        lastGood.edit { putString(key(appWidgetId), json.toString()) }
    }

    fun loadLastGood(appWidgetId: Int): WidgetView.Arrivals? = runCatching {
        val json = JSONObject(lastGood.getString(key(appWidgetId), null) ?: return null)
        val arrivals = json.getJSONArray("arrivals")
        WidgetView.Arrivals(
            title = json.getString("title"),
            subtitle = json.optString("subtitle").ifEmpty { null },
            arrivals = (0 until arrivals.length()).map { arrivals.getJSONObject(it) }
                .map { Arrival(it.getString("r"), it.getString("d"), it.getInt("m"), isRealtime = true) },
            refreshedAt = json.getString("refreshedAt"),
        )
    }.getOrNull()

    fun remove(appWidgetId: Int) {
        lastGood.edit { remove(key(appWidgetId)) }
        prefs.edit { remove(key(appWidgetId)) }
        legacy.edit { remove(key(appWidgetId)) }
    }

    private fun key(appWidgetId: Int) = "stop_$appWidgetId"
}
