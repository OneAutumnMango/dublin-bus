package dev.dublinbustracker.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
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

private val COMPASS_POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** The compass bearing in degrees (0 = north, 90 = east) you'd head in to get from [from] to [to]. */
fun bearingDegrees(from: LatLon, to: LatLon): Double {
    val dLon = (to.lon - from.lon) * PI / 180
    val lat1 = from.lat * PI / 180
    val lat2 = to.lat * PI / 180
    val bearing = Math.toDegrees(atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)))
    return (bearing + 360) % 360
}

/** Which of the eight compass points you'd head in to get from [from] to [to], e.g. "NW". */
fun compassPoint(from: LatLon, to: LatLon): String =
    COMPASS_POINTS[((bearingDegrees(from, to) + 22.5) / 45).toInt() % 8]

/** The overall way a direction of a route heads: from its first stop to its last. Null if it has fewer than two. */
fun directionCompass(stops: List<Stop>): String? {
    val first = stops.firstOrNull() ?: return null
    val last = stops.lastOrNull()?.takeIf { stops.size > 1 } ?: return null
    return compassPoint(LatLon(first.lat, first.lon), LatLon(last.lat, last.lon))
}

/** A vehicle further than this from a route's path isn't on it, so the path can't say which way it is facing. */
private const val MAX_OFF_PATH_METERS = 150.0

/**
 * Which way a vehicle at [here] faces if it is driving along [path]: the bearing along the segment of it
 * closest to the vehicle. Null when there's no path or the vehicle isn't near it.
 */
fun headingAlongPath(path: List<LatLon>, here: LatLon): Float? {
    if (path.size < 2) return null
    // Work in flat metres around [here]: accurate enough over a few hundred metres, and cheap enough to try every segment.
    val metersPerLat = 110_540.0
    val metersPerLon = 111_320.0 * cos(here.lat * PI / 180)
    fun x(point: LatLon) = (point.lon - here.lon) * metersPerLon
    fun y(point: LatLon) = (point.lat - here.lat) * metersPerLat

    var nearest = -1
    var nearestSquared = Double.MAX_VALUE
    for (index in 0 until path.lastIndex) {
        val ax = x(path[index])
        val ay = y(path[index])
        val dx = x(path[index + 1]) - ax
        val dy = y(path[index + 1]) - ay
        val lengthSquared = dx * dx + dy * dy
        // How far along this segment the closest point to the vehicle (the origin) is, kept within the segment.
        val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        val squared = px * px + py * py
        if (squared < nearestSquared) {
            nearestSquared = squared
            nearest = index
        }
    }
    if (nearest < 0 || nearestSquared > MAX_OFF_PATH_METERS * MAX_OFF_PATH_METERS) return null
    return bearingDegrees(path[nearest], path[nearest + 1]).toFloat()
}
