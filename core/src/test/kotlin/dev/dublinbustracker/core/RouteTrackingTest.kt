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
}
