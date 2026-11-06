package dev.dublinbustracker.data

import dev.dublinbustracker.core.LatLon
import dev.dublinbustracker.core.distanceMeters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class WidgetMode {
    /** One chosen stop, optionally narrowed to some buses. */
    SINGLE_STOP,

    /** Whichever favourite stop is closest right now, skipping any the user excluded. */
    NEAREST_FAVOURITE,
}

/** What one placed widget shows. */
@Serializable
data class WidgetConfig(
    val mode: WidgetMode = WidgetMode.SINGLE_STOP,
    val stopId: String? = null,
    /** Only these bus numbers are listed; empty means every bus. */
    val routes: Set<String> = emptySet(),
    /** In [WidgetMode.NEAREST_FAVOURITE]: favourites that are never picked. */
    val excludedStopIds: Set<String> = emptySet(),
) {
    fun encode(): String = JSON.encodeToString(this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        fun decode(raw: String?): WidgetConfig? = raw?.let { runCatching { JSON.decodeFromString<WidgetConfig>(it) }.getOrNull() }
    }
}

/** The favourite closest to [here] that isn't excluded, with its distance in metres. A favourite with no known position is never picked. */
fun nearestFavourite(
    favourites: List<FavouriteStop>,
    excludedStopIds: Set<String>,
    here: LatLon,
): Pair<FavouriteStop, Double>? =
    favourites
        .filter { it.id !in excludedStopIds && !(it.lat == 0.0 && it.lon == 0.0) }
        .map { it to distanceMeters(here, LatLon(it.lat, it.lon)) }
        .minByOrNull { it.second }

/** [arrivals] limited to the buses in [routes] (all of them if it's empty), compared ignoring case. */
fun <T> keepRoutes(arrivals: List<T>, routes: Set<String>, routeOf: (T) -> String): List<T> =
    if (routes.isEmpty()) arrivals else arrivals.filter { arrival -> routes.any { it.equals(routeOf(arrival), ignoreCase = true) } }
