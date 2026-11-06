package dev.dublinbustracker.app.map

import android.content.Context
import androidx.core.content.edit

enum class StartMode { CITY_CENTRE, MY_LOCATION, CHOSEN }

/** Where the map opens: the city centre, wherever the device is, or a point picked in Settings. */
data class MapStart(val mode: StartMode = StartMode.CITY_CENTRE, val lat: Double = 0.0, val lon: Double = 0.0)

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

    private companion object {
        const val MODE = "mode"
        const val LAT = "lat"
        const val LON = "lon"
    }
}
