package dev.dublinbustracker.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteProgressTest {
    // Four stops in a line heading east, about 670m apart.
    private val stops = listOf(-6.00, -5.99, -5.98, -5.97).mapIndexed { i, lon -> Stop("s$i", "$i", "Stop $i", 53.0, lon) }
    private fun bus(id: String, lon: Double, lat: Double = 53.0) = VehiclePosition(id, "4", "East", LatLon(lat, lon))
    private val ids = { buses: List<VehiclePosition> -> buses.map { it.vehicleId } }

    @Test
    fun `buses up to the viewed stop are kept, and only the nearest one beyond it`() {
        val buses = listOf(bus("behind", -6.005), bus("approaching", -5.995), bus("justLeft", -5.975), bus("farOn", -5.965))

        assertEquals(listOf("behind", "approaching", "justLeft"), ids(busesUpToStop(stops, "s1", buses)))
    }

    @Test
    fun `a bus standing at the viewed stop is not past it`() {
        val buses = listOf(bus("atStop", -5.99), bus("justLeft", -5.985), bus("farOn", -5.965))

        assertEquals(listOf("atStop", "justLeft"), ids(busesUpToStop(stops, "s1", buses)))
    }

    @Test
    fun `a bus off the route, or a stop not on it, changes nothing`() {
        val buses = listOf(bus("lost", -5.99, lat = 53.2), bus("farOn", -5.965))

        assertEquals(listOf("lost", "farOn"), ids(busesUpToStop(stops, "s1", buses)))
        assertEquals(ids(buses), ids(busesUpToStop(stops, "unknown", buses)))
    }

    @Test
    fun `a stop's label stacks each route's next two arrivals, one per line, and skips routes with none`() {
        val now = 1_700_000_000L
        fun arrival(minutes: Int) = Arrival("4", "Heuston", minutes, isRealtime = true, expectedEpochSeconds = now + minutes * 60L)

        val label = stopTimesLabel(
            listOf("4" to listOf(arrival(5), arrival(18), arrival(30)), "S8" to emptyList(), "15" to listOf(arrival(2))),
            now,
            ZoneId.of("Europe/Dublin"),
        )

        assertEquals("4: 5 min\n4: 18 min\n15: 2 min", label)
        assertNull(stopTimesLabel(listOf("S8" to emptyList()), now, ZoneId.of("Europe/Dublin")))
    }
}
