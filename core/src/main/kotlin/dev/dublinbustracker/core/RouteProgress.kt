package dev.dublinbustracker.core

import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sqrt

/** A bus further than this from the line joining a route's stops can't be placed along the route. */
private const val MAX_OFF_ROUTE_METERS = 500.0

/** A bus within this fraction of a stop-to-stop leg beyond a stop still counts as being at it. */
private const val AT_STOP_SLACK = 0.05

/**
 * How far along a route (a list of points in order) [here] is, as the index of the leg it is on plus how far
 * through that leg (3.5 = halfway between the fourth and fifth point). Null when it isn't near the route.
 */
private fun progressAlong(points: List<LatLon>, here: LatLon): Double? {
    if (points.size < 2) return null
    // Flat metres around [here]: accurate enough over a route's legs, and it lets every leg be tried cheaply.
    val metersPerLat = 110_540.0
    val metersPerLon = 111_320.0 * cos(here.lat * Math.PI / 180)
    fun x(p: LatLon) = (p.lon - here.lon) * metersPerLon
    fun y(p: LatLon) = (p.lat - here.lat) * metersPerLat

    var best = Double.MAX_VALUE
    var progress = 0.0
    for (i in 0 until points.lastIndex) {
        val ax = x(points[i])
        val ay = y(points[i])
        val dx = x(points[i + 1]) - ax
        val dy = y(points[i + 1]) - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        val squared = px * px + py * py
        if (squared < best) {
            best = squared
            progress = i + t
        }
    }
    return progress.takeIf { sqrt(best) <= MAX_OFF_ROUTE_METERS }
}

/**
 * The buses to show on a route map that hides what has already gone by: those at or before the stop being viewed,
 * plus only the nearest one beyond it (the one that has just left). [stops] are in route order. A bus that can't
 * be placed on the route is kept, and so is everything when [viewedStopId] isn't on the route.
 */
fun busesUpToStop(stops: List<Stop>, viewedStopId: String, buses: List<VehiclePosition>): List<VehiclePosition> {
    val viewed = stops.indexOfFirst { it.id == viewedStopId }
    if (viewed < 0) return buses
    val route = stops.map { LatLon(it.lat, it.lon) }
    val progress = buses.map { progressAlong(route, it.position) }
    val nearestPast = progress.withIndex()
        .filter { (_, p) -> p != null && p > viewed + AT_STOP_SLACK }
        .minByOrNull { it.value!! }
        ?.index
    return buses.filterIndexed { index, _ ->
        val p = progress[index]
        index == nearestPast || p == null || p <= viewed + AT_STOP_SLACK
    }
}

/**
 * The small print on a stop: for each route, its next arrivals as they would read on the stop's own screen
 * ("5 min", or the timetable time for a scheduled one). Routes with nothing coming are left out; null if none have.
 */
fun stopTimesLabel(perRoute: List<Pair<String, List<Arrival>>>, nowEpochSeconds: Long, zone: ZoneId): String? =
    perRoute
        .filter { (_, arrivals) -> arrivals.isNotEmpty() }
        .joinToString("\n") { (route, arrivals) ->
            "$route: " + arrivals.take(2).joinToString(", ") { it.display(nowEpochSeconds, zone).headline }
        }
        .ifEmpty { null }
