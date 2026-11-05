package dev.dublinbustracker.core

/** Distinct hues for tracked routes; white text stays readable on every one. */
private val ROUTE_COLOURS = intArrayOf(
    0xFFD81B60.toInt(), // pink
    0xFF1E88E5.toInt(), // blue
    0xFF43A047.toInt(), // green
    0xFFFB8C00.toInt(), // orange
    0xFF8E24AA.toInt(), // purple
    0xFF00897B.toInt(), // teal
    0xFF6D4C41.toInt(), // brown
    0xFF546E7A.toInt(), // slate
)

val MAX_TRACKED_ROUTES = ROUTE_COLOURS.size

/** The lowest colour slot not already used, so removing a route frees its colour for the next one. */
fun nextColourIndex(used: Collection<Int>): Int = (0 until MAX_TRACKED_ROUTES).firstOrNull { it !in used } ?: 0

/** A route's colour for its [colourIndex]; direction "0" gets the full colour and the other a lighter tint of it. */
fun trackingColour(colourIndex: Int, directionId: String): Int {
    val base = ROUTE_COLOURS[colourIndex.mod(MAX_TRACKED_ROUTES)]
    return if (directionId == "0") base else lighten(base, 0.5f)
}

/** Dark text on the light direction, white on the full one. */
fun trackingTextColour(directionId: String): Int = if (directionId == "0") 0xFFFFFFFF.toInt() else 0xFF212121.toInt()

fun Int.toHexColour(): String = "#%06X".format(this and 0xFFFFFF)

private fun lighten(colour: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
        val value = (colour shr shift) and 0xFF
        return (value + (255 - value) * amount).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

data class LegendEntry(val route: String, val directionId: String, val headsign: String)

/**
 * One entry per direction of each route, in route order, labelled with where that direction is
 * heading: the most common headsign among its live buses, else the [destinations] known for it
 * (the end of its stop pattern) so a direction with no bus right now can still be switched on or off.
 */
fun legendFor(
    routes: List<String>,
    vehicles: Map<String, List<VehiclePosition>>,
    destinations: Map<String, Map<String, String>> = emptyMap(),
): List<LegendEntry> =
    routes.flatMap { route ->
        val live = vehicles[route].orEmpty().groupBy { it.directionId }
        val known = destinations[route].orEmpty()
        (live.keys + known.keys).toSortedSet().map { direction ->
            // The most common headsign wins, in case a short working or two disagrees.
            val headsign = live[direction]?.groupingBy { it.headsign }?.eachCount()?.maxByOrNull { it.value }?.key
            LegendEntry(route, direction, headsign?.takeIf { it.isNotBlank() } ?: known[direction].orEmpty())
        }
    }

/**
 * Routes to offer while typing: an exact bus number first, then ones starting with the text, then
 * ones that just contain it (or match on the name), one per bus number.
 */
fun rankRouteSuggestions(query: String, routes: List<Route>, limit: Int = 8): List<Route> {
    val typed = query.trim()
    if (typed.isEmpty()) return emptyList()
    fun rank(route: Route) = when {
        route.shortName.equals(typed, ignoreCase = true) -> 0
        route.shortName.startsWith(typed, ignoreCase = true) -> 1
        route.shortName.contains(typed, ignoreCase = true) -> 2
        else -> 3
    }
    return routes
        .distinctBy { it.shortName.uppercase() }
        .sortedWith(compareBy({ rank(it) }, { it.shortName.length }, { it.shortName }))
        .take(limit)
}
