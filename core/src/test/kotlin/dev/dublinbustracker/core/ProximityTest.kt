package dev.dublinbustracker.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProximityTest {
    private val dublin = LatLon(53.3498, -6.2603)

    @Test
    fun `a point is no distance from itself`() {
        assertEquals(0.0, distanceMeters(dublin, dublin), 0.001)
    }

    @Test
    fun `a degree of longitude on the equator is about 111km`() {
        val meters = distanceMeters(LatLon(0.0, 0.0), LatLon(0.0, 1.0))

        assertTrue(abs(meters - 111_195) < 200, "was $meters")
    }

    @Test
    fun `Dublin to Dun Laoghaire is about 11km`() {
        val meters = distanceMeters(dublin, LatLon(53.2948, -6.1349))

        assertTrue(meters in 9_500.0..11_500.0, "was $meters")
    }

    @Test
    fun `distance is the same both ways`() {
        val other = LatLon(53.29, -6.13)

        assertEquals(distanceMeters(dublin, other), distanceMeters(other, dublin), 0.001)
    }

    @Test
    fun `distances read naturally`() {
        assertEquals("0 m", distanceText(3.0))
        assertEquals("80 m", distanceText(84.0))
        assertEquals("990 m", distanceText(994.0))
        assertEquals("1.4 km", distanceText(1_430.0))
        assertEquals("9.9 km", distanceText(9_940.0))
        assertEquals("12 km", distanceText(11_700.0))
    }

    private data class Place(val name: String, val position: LatLon?)

    @Test
    fun `places are sorted nearest first with their distances`() {
        val places = listOf(
            Place("far", LatLon(53.29, -6.13)),
            Place("near", LatLon(53.351, -6.26)),
            Place("middle", LatLon(53.32, -6.24)),
        )

        val sorted = sortedByDistance(places, dublin) { it.position }

        assertEquals(listOf("near", "middle", "far"), sorted.map { it.first.name })
        assertTrue(sorted.zipWithNext().all { (a, b) -> a.second!! <= b.second!! })
    }

    @Test
    fun `places with no known position go last in their original order`() {
        val places = listOf(Place("unknown a", null), Place("near", LatLon(53.351, -6.26)), Place("unknown b", null), Place("far", LatLon(53.29, -6.13)))

        val sorted = sortedByDistance(places, dublin) { it.position }

        assertEquals(listOf("near", "far", "unknown a", "unknown b"), sorted.map { it.first.name })
        assertEquals(listOf(true, true, false, false), sorted.map { it.second != null })
    }

    @Test
    fun `nothing to sort is nothing back`() {
        assertEquals(emptyList(), sortedByDistance(emptyList<Place>(), dublin) { it.position })
    }

    @Test
    fun `compass points follow north, east, south and west`() {
        assertEquals("N", compassPoint(dublin, LatLon(dublin.lat + 0.1, dublin.lon)))
        assertEquals("E", compassPoint(dublin, LatLon(dublin.lat, dublin.lon + 0.1)))
        assertEquals("S", compassPoint(dublin, LatLon(dublin.lat - 0.1, dublin.lon)))
        assertEquals("W", compassPoint(dublin, LatLon(dublin.lat, dublin.lon - 0.1)))
        assertEquals("NW", compassPoint(dublin, LatLon(dublin.lat + 0.1, dublin.lon - 0.15)))
    }

    @Test
    fun `a direction with fewer than two stops has no compass point`() {
        assertEquals(null, directionCompass(emptyList()))
        assertEquals(null, directionCompass(listOf(Stop("a", "1", "A", 53.0, -6.0))))
    }

    @Test
    fun `bearing is degrees clockwise from north`() {
        assertEquals(0.0, bearingDegrees(dublin, LatLon(dublin.lat + 0.1, dublin.lon)), 0.01)
        assertEquals(180.0, bearingDegrees(dublin, LatLon(dublin.lat - 0.1, dublin.lon)), 0.01)
        assertTrue(bearingDegrees(dublin, LatLon(dublin.lat, dublin.lon - 0.1)) in 269.0..271.0)
    }

    @Test
    fun `a vehicle on a path faces along it, and one far from it has no heading`() {
        val path = listOf(LatLon(53.0, -6.00), LatLon(53.0, -5.99), LatLon(53.0, -5.98), LatLon(53.01, -5.98))

        assertEquals(90f, headingAlongPath(path, LatLon(53.0, -5.9951))!!, 1f)
        assertEquals(0f, headingAlongPath(path, LatLon(53.005, -5.98))!!, 1f)
        assertEquals(null, headingAlongPath(path, LatLon(53.2, -6.5)))
        assertEquals(null, headingAlongPath(emptyList(), LatLon(53.0, -6.0)))
    }
}
