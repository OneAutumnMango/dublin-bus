package dev.dublinbustracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RouteTrackingTest {
    @Test
    fun `every route colour slot is different`() {
        val colours = (0 until MAX_TRACKED_ROUTES).map { trackingColour(it, "0") }

        assertEquals(MAX_TRACKED_ROUTES, colours.toSet().size)
    }

    @Test
    fun `the two directions of a route differ but share its hue`() {
        val out = trackingColour(1, "0")
        val back = trackingColour(1, "1")

        assertNotEquals(out, back)
        // Lightened toward white, so every channel is at least as bright.
        listOf(16, 8, 0).forEach { shift -> assertTrue(((back shr shift) and 0xFF) >= ((out shr shift) and 0xFF)) }
    }

    @Test
    fun `the light direction gets dark text and the full one white`() {
        assertEquals(0xFFFFFFFF.toInt(), trackingTextColour("0"))
        assertEquals(0xFF212121.toInt(), trackingTextColour("1"))
    }

    @Test
    fun `colours format as hex for the map`() {
        assertEquals("#1E88E5", trackingColour(1, "0").toHexColour())
    }

    @Test
    fun `a freed colour slot is reused before a new one`() {
        assertEquals(0, nextColourIndex(emptyList()))
        assertEquals(2, nextColourIndex(listOf(0, 1, 3)))
        assertEquals(1, nextColourIndex(listOf(0, 2)))
    }

    @Test
    fun `when every slot is used it wraps rather than failing`() {
        assertEquals(0, nextColourIndex((0 until MAX_TRACKED_ROUTES).toList()))
    }

    private fun bus(direction: String, headsign: String) =
        VehiclePosition("v$headsign$direction", "S8", headsign, LatLon(0.0, 0.0), directionId = direction)

    @Test
    fun `the legend has an entry per route and direction with its headsign`() {
        val vehicles = mapOf(
            "S8" to listOf(bus("1", "Citywest"), bus("0", "Dun Laoghaire"), bus("1", "Citywest")),
            "4" to listOf(bus("0", "Monkstown")),
        )

        val legend = legendFor(listOf("S8", "4"), vehicles)

        assertEquals(
            listOf(
                LegendEntry("S8", "0", "Dun Laoghaire"),
                LegendEntry("S8", "1", "Citywest"),
                LegendEntry("4", "0", "Monkstown"),
            ),
            legend,
        )
    }

    @Test
    fun `a route with no buses has no legend entries and the order follows the routes`() {
        assertEquals(emptyList(), legendFor(listOf("S8"), mapOf("S8" to emptyList())))
        assertEquals(emptyList(), legendFor(listOf("S8"), emptyMap()))
    }

    @Test
    fun `the most common headsign names a direction`() {
        val vehicles = mapOf("S8" to listOf(bus("1", "Citywest"), bus("1", "Citywest"), bus("1", "Kingswood")))

        assertEquals("Citywest", legendFor(listOf("S8"), vehicles).single().headsign)
    }

    @Test
    fun `a direction with no bus right now is still listed, named by where it ends`() {
        val vehicles = mapOf("S8" to listOf(bus("1", "Citywest")))
        val destinations = mapOf("S8" to mapOf("0" to "Dun Laoghaire Stn", "1" to "Kingswood Avenue"))

        val legend = legendFor(listOf("S8"), vehicles, destinations)

        assertEquals(
            listOf(LegendEntry("S8", "0", "Dun Laoghaire Stn"), LegendEntry("S8", "1", "Citywest")),
            legend,
        )
    }

    private fun route(short: String, long: String = "") = Route("id-$short-$long", short, long)

    @Test
    fun `suggestions put the exact bus number first, then ones starting with it`() {
        val routes = listOf(route("145"), route("14"), route("46A", "via 4th Ave"), route("4"), route("40"), route("4A"))

        // Exact first, then starts-with (shorter then alphabetical), then merely contains.
        assertEquals(listOf("4", "40", "4A", "46A", "14", "145"), rankRouteSuggestions("4", routes).map { it.shortName })
    }

    @Test
    fun `suggestions are one per bus number and capped`() {
        val routes = (1..20).map { route("4$it") } + route("4", "one") + route("4", "two")

        val suggestions = rankRouteSuggestions("4", routes, limit = 5)

        assertEquals(5, suggestions.size)
        assertEquals(1, suggestions.count { it.shortName == "4" })
        assertEquals("4", suggestions.first().shortName)
    }

    @Test
    fun `suggestions are case-insensitive and nothing is suggested for blank text`() {
        assertEquals("S8", rankRouteSuggestions("s8", listOf(route("S8"), route("S2"))).first().shortName)
        assertEquals(emptyList(), rankRouteSuggestions("  ", listOf(route("S8"))))
    }

    @Test
    fun `a name-only match is offered after the number matches`() {
        val routes = listOf(route("S8", "Kingswood to 4 Park"), route("14"))

        assertEquals(listOf("14", "S8"), rankRouteSuggestions("4", routes).map { it.shortName })
    }

    @Test
    fun `a direction is named by its last stop`() {
        fun stop(name: String) = Stop("id-$name", "1", name, 0.0, 0.0)
        val stops = mapOf(
            "S8" to mapOf("0" to listOf(stop("Kingswood"), stop("Dun Laoghaire Stn")), "1" to listOf(stop("Dun Laoghaire Stn"), stop("Kingswood Avenue"))),
            "4" to mapOf("0" to emptyList()),
        )

        val destinations = destinationsOf(stops)

        assertEquals(mapOf("0" to "Dun Laoghaire Stn", "1" to "Kingswood Avenue"), destinations["S8"])
        assertEquals(emptyMap(), destinations["4"])
    }
}
