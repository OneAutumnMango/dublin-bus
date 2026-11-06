package dev.dublinbustracker.app.map

import android.content.Context
import androidx.core.content.edit

/** What of the follow-routes mode survives closing the app: the routes, the directions switched off, and the toggles. */
data class SavedTracking(
    val enabled: Boolean = false,
    val routes: List<TrackedRoute> = emptyList(),
    val hidden: Set<Pair<String, String>> = emptySet(),
    val showStops: Boolean = true,
    val showStopTimes: Boolean = false,
)

interface TrackingStore {
    fun load(): SavedTracking
    fun save(saved: SavedTracking)
}

object NoTrackingStore : TrackingStore {
    override fun load() = SavedTracking()
    override fun save(saved: SavedTracking) = Unit
}

/** Route names are short bus numbers, so plain separators are enough: "S8:0;4:1" and "4|0;S8|1". */
class SharedPreferencesTrackingStore(context: Context) : TrackingStore {
    private val prefs = context.getSharedPreferences("tracking", Context.MODE_PRIVATE)

    override fun load() = SavedTracking(
        enabled = prefs.getBoolean(ENABLED, false),
        routes = prefs.getString(ROUTES, "").orEmpty().split(";").mapNotNull { entry ->
            val (name, colour) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            colour.toIntOrNull()?.let { TrackedRoute(name, it) }
        },
        hidden = prefs.getString(HIDDEN, "").orEmpty().split(";").mapNotNull { entry ->
            entry.split("|").takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toSet(),
        showStops = prefs.getBoolean(SHOW_STOPS, true),
        showStopTimes = prefs.getBoolean(SHOW_STOP_TIMES, false),
    )

    override fun save(saved: SavedTracking) = prefs.edit {
        putBoolean(ENABLED, saved.enabled)
        putString(ROUTES, saved.routes.joinToString(";") { "${it.name}:${it.colourIndex}" })
        putString(HIDDEN, saved.hidden.joinToString(";") { "${it.first}|${it.second}" })
        putBoolean(SHOW_STOPS, saved.showStops)
        putBoolean(SHOW_STOP_TIMES, saved.showStopTimes)
    }

    private companion object {
        const val ENABLED = "enabled"
        const val ROUTES = "routes"
        const val HIDDEN = "hidden"
        const val SHOW_STOPS = "show_stops"
        const val SHOW_STOP_TIMES = "show_stop_times"
    }
}
