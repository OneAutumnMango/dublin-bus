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
    private val shown = context.getSharedPreferences("widget_last_good", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("widget_stops", Context.MODE_PRIVATE)

    fun load(appWidgetId: Int): WidgetConfig? =
        WidgetConfig.decode(prefs.getString(key(appWidgetId), null))
            // Widgets placed by earlier builds only remember a stop.
            ?: legacy.getString(key(appWidgetId), null)?.let { WidgetConfig(stopId = it) }

    fun save(appWidgetId: Int, config: WidgetConfig) {
        shown.edit { remove(key(appWidgetId)) } // it was for the old choice
        prefs.edit { putString(key(appWidgetId), config.encode()) }
    }

    /** What a widget last learned, kept on disk: drawing reads it, and a refresh that fails after the app sat idle (process gone) still has something to show. */
    fun saveShown(appWidgetId: Int, view: WidgetView) {
        val json = JSONObject()
        when (view) {
            is WidgetView.Arrivals -> {
                json.put("kind", ARRIVALS).put("title", view.title).put("subtitle", view.subtitle).put("refreshedAt", view.refreshedAt).put("stale", view.stale)
                    .put("arrivals", JSONArray(view.arrivals.map { JSONObject().put("r", it.routeShortName).put("d", it.destination).put("m", it.dueMinutes) }))
            }
            is WidgetView.Unreachable -> json.put("kind", UNREACHABLE).put("reason", view.reason)
            WidgetView.NoFavourites -> json.put("kind", NO_FAVOURITES)
            WidgetView.NoLocation -> json.put("kind", NO_LOCATION)
            WidgetView.NotConfigured -> json.put("kind", NOT_CONFIGURED)
            WidgetView.Loading -> return
        }
        shown.edit { putString(key(appWidgetId), json.toString()) }
    }

    fun loadShown(appWidgetId: Int): WidgetView? = runCatching {
        val json = JSONObject(shown.getString(key(appWidgetId), null) ?: return null)
        when (json.optString("kind", ARRIVALS)) { // earlier builds stored arrivals only, with no kind
            UNREACHABLE -> WidgetView.Unreachable(json.optString("reason").ifEmpty { null })
            NO_FAVOURITES -> WidgetView.NoFavourites
            NO_LOCATION -> WidgetView.NoLocation
            NOT_CONFIGURED -> WidgetView.NotConfigured
            else -> {
                val arrivals = json.getJSONArray("arrivals")
                WidgetView.Arrivals(
                    title = json.getString("title"),
                    subtitle = json.optString("subtitle").ifEmpty { null },
                    arrivals = (0 until arrivals.length()).map { arrivals.getJSONObject(it) }
                        .map { Arrival(it.getString("r"), it.getString("d"), it.getInt("m"), isRealtime = true) },
                    refreshedAt = json.getString("refreshedAt"),
                    stale = json.optBoolean("stale"),
                )
            }
        }
    }.getOrNull()

    fun remove(appWidgetId: Int) {
        shown.edit { remove(key(appWidgetId)) }
        prefs.edit { remove(key(appWidgetId)) }
        legacy.edit { remove(key(appWidgetId)) }
    }

    private fun key(appWidgetId: Int) = "stop_$appWidgetId"

    private companion object {
        const val ARRIVALS = "arrivals"
        const val UNREACHABLE = "unreachable"
        const val NO_FAVOURITES = "no_favourites"
        const val NO_LOCATION = "no_location"
        const val NOT_CONFIGURED = "not_configured"
    }
}
