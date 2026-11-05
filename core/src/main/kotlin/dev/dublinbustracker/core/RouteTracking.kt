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

/** One entry per route and direction that has a live bus, labelled with where that direction is heading. */
fun legendFor(routes: List<String>, vehicles: Map<String, List<VehiclePosition>>): List<LegendEntry> =
    routes.flatMap { route ->
        vehicles[route].orEmpty()
            .groupBy { it.directionId }
            .toSortedMap()
            .map { (direction, buses) ->
                // The most common headsign wins, in case a short working or two disagrees.
                val headsign = buses.groupingBy { it.headsign }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
                LegendEntry(route, direction, headsign)
            }
    }
