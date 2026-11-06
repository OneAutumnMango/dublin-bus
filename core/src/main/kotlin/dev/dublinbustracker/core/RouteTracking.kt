package dev.dublinbustracker.core

/**
 * The Okabe-Ito palette: eight colours chosen to stay distinguishable for the common kinds of colour
 * blindness (and everyone else). Used for every colour on the maps that carries meaning.
 */
object OkabeIto {
    const val ORANGE = 0xFFE69F00.toInt()
    const val SKY_BLUE = 0xFF56B4E9.toInt()
    const val BLUISH_GREEN = 0xFF009E73.toInt()
    const val YELLOW = 0xFFF0E442.toInt()
    const val BLUE = 0xFF0072B2.toInt()
    const val VERMILLION = 0xFFD55E00.toInt()
    const val REDDISH_PURPLE = 0xFFCC79A7.toInt()
    const val BLACK = 0xFF000000.toInt()
}

/** Route colours, in the order they are handed out: the first few are the most distinct from one another. */
private val ROUTE_COLOURS = intArrayOf(
    OkabeIto.BLUE,
    OkabeIto.VERMILLION,
    OkabeIto.BLUISH_GREEN,
    OkabeIto.ORANGE,
    OkabeIto.REDDISH_PURPLE,
    OkabeIto.SKY_BLUE,
    OkabeIto.BLACK,
    OkabeIto.YELLOW,
)

val MAX_TRACKED_ROUTES = ROUTE_COLOURS.size

/** The lowest colour slot not already used, so removing a route frees its colour for the next one. */
fun nextColourIndex(used: Collection<Int>): Int = (0 until MAX_TRACKED_ROUTES).firstOrNull { it !in used } ?: 0

/** A route's colour for its [colourIndex]; direction "0" gets the full colour and the other a lighter tint of it. */
fun trackingColour(colourIndex: Int, directionId: String): Int {
    val base = ROUTE_COLOURS[colourIndex.mod(MAX_TRACKED_ROUTES)]
    return if (directionId == "0") base else lighten(base, 0.5f)
}

/** Black or white text, whichever is easier to read on a [fill] colour (yellow needs black, blue needs white). */
fun readableTextOn(fill: Int): Int {
    fun channel(shift: Int): Double {
        val c = ((fill shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    val contrastWithWhite = 1.05 / (luminance + 0.05)
    val contrastWithBlack = (luminance + 0.05) / 0.05
    return if (contrastWithWhite >= contrastWithBlack) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
}

/** The label colour for a bus drawn in [trackingColour]. */
fun trackingTextColour(colourIndex: Int, directionId: String): Int = readableTextOn(trackingColour(colourIndex, directionId))

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

/** Where each direction of each route ends up (its last stop), by route then direction id - for labelling a direction with no bus. */
fun destinationsOf(stops: Map<String, Map<String, List<Stop>>>): Map<String, Map<String, String>> =
    stops.mapValues { (_, byDirection) ->
        byDirection.mapNotNull { (direction, list) -> list.lastOrNull()?.name?.let { direction to it } }.toMap()
    }
