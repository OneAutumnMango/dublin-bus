package dev.dublinbustracker.app.map

import android.content.Context
import androidx.core.content.edit

enum class StartMode { CITY_CENTRE, MY_LOCATION, CHOSEN, LAST_POSITION }

/** Where the map opens: the city centre, wherever the device is, a point picked in Settings, or where it was left. */
data class MapStart(val mode: StartMode = StartMode.CITY_CENTRE, val lat: Double = 0.0, val lon: Double = 0.0)

/** Where the map camera last came to rest. */
data class LastPosition(val lat: Double, val lon: Double, val zoom: Double)

class MapStartStore(context: Context) {
    private val prefs = context.getSharedPreferences("map_start", Context.MODE_PRIVATE)

    fun load() = MapStart(
        mode = runCatching { StartMode.valueOf(prefs.getString(MODE, "").orEmpty()) }.getOrDefault(StartMode.CITY_CENTRE),
        lat = prefs.getFloat(LAT, 0f).toDouble(),
        lon = prefs.getFloat(LON, 0f).toDouble(),
    )

    fun save(start: MapStart) = prefs.edit {
        putString(MODE, start.mode.name)
        putFloat(LAT, start.lat.toFloat())
        putFloat(LON, start.lon.toFloat())
    }

    /** Remembers where the map was left, whatever the start mode, so choosing "where I left it" later has a place to open at. */
    fun saveLastPosition(position: LastPosition) = prefs.edit {
        putFloat(LAST_LAT, position.lat.toFloat())
        putFloat(LAST_LON, position.lon.toFloat())
        putFloat(LAST_ZOOM, position.zoom.toFloat())
    }

    /** Null until the map has been looked at once. */
    fun lastPosition(): LastPosition? =
        if (prefs.contains(LAST_ZOOM)) LastPosition(prefs.getFloat(LAST_LAT, 0f).toDouble(), prefs.getFloat(LAST_LON, 0f).toDouble(), prefs.getFloat(LAST_ZOOM, 0f).toDouble()) else null

    private companion object {
        const val LAST_LAT = "last_lat"
        const val LAST_LON = "last_lon"
        const val LAST_ZOOM = "last_zoom"
        const val MODE = "mode"
        const val LAT = "lat"
        const val LON = "lon"
    }
}
