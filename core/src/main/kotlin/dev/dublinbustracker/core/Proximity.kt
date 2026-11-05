package dev.dublinbustracker.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Straight-line distance between two points on the Earth (haversine). */
fun distanceMeters(from: LatLon, to: LatLon): Double {
    val lat1 = from.lat * PI / 180
    val lat2 = to.lat * PI / 180
    val dLat = lat2 - lat1
    val dLon = (to.lon - from.lon) * PI / 180
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

/** A distance as people say it: "80 m", "1.4 km", "12 km". */
fun distanceText(meters: Double): String = when {
    meters < 1_000 -> "${(meters / 10).roundToInt() * 10} m"
    meters < 10_000 -> "%.1f km".format(meters / 1_000)
    else -> "${(meters / 1_000).roundToInt()} km"
}

/**
 * [items] nearest first, each with its distance from [from]. Anything without a known position
 * goes last (distance null), in its original order.
 */
fun <T> sortedByDistance(items: List<T>, from: LatLon, position: (T) -> LatLon?): List<Pair<T, Double?>> =
    items
        .map { item -> item to position(item)?.let { distanceMeters(from, it) } }
        .sortedWith(compareBy(nullsLast()) { it.second })
