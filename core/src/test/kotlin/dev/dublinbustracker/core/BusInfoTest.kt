package dev.dublinbustracker.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BusInfoTest {
    private val zone = ZoneId.of("Europe/Dublin")
    private val now = 1_700_000_000L // 22:13:20 GMT
    private val stop = Stop("a", "4320", "Heuston Station", 0.0, 0.0)

    private fun details(
        vehicle: VehicleDetails? = VehicleDetails("245", "", 53.0, -6.0, now - 30),
        calls: List<TripStopCall> = emptyList(),
    ) = TripDetails(TripInfo("t1", "r1", "S2", "Irishtown", "1"), vehicle, calls)

    private fun arrival(delay: Int? = 240, realtime: Boolean = true, passed: Boolean = false) =
        Arrival("S2", "Irishtown", 17, realtime, expectedEpochSeconds = now + 17 * 60, delaySeconds = delay, possiblyPassed = passed)

    private fun BusInfo.row(label: String) = rows.firstOrNull { it.label == label }?.value

    @Test
    fun `the title is the route and where it is going`() {
        assertEquals("S2 · Irishtown", details().describe(null, null, now, zone).title)
    }

    @Test
    fun `a live bus at the viewed stop gives its time, wait, lateness and what the timetable said`() {
        val info = details().describe(stop, arrival(delay = 240), now, zone)

        assertEquals("22:30 · in 17 min · 4 min late", info.row("At Heuston Station"))
        assertEquals("22:26", info.row("Timetable said"))
    }

    @Test
    fun `an on-time bus has no timetable-said row`() {
        assertNull(details().describe(stop, arrival(delay = 20), now, zone).row("Timetable said"))
    }

    @Test
    fun `a timetable-only bus says so and has no timetable-versus-expected row`() {
        val info = details(vehicle = null).describe(stop, arrival(delay = null, realtime = false), now, zone)

        assertTrue(info.row("At Heuston Station")!!.endsWith("timetable only - no live data yet"))
        assertNull(info.row("Timetable said"))
    }

    @Test
    fun `a possibly passed bus says it may have left`() {
        val info = details().describe(stop, arrival(passed = true), now, zone)

        assertTrue(info.row("At Heuston Station")!!.contains("may have left"))
    }

    @Test
    fun `the viewed stop is looked up in the trip's calls when there is no arrival`() {
        val info = details(calls = listOf(TripStopCall("a", "Heuston Station", now + 300, 60))).describe(stop, null, now, zone)

        assertEquals("22:18 · in 5 min · 1 min late", info.row("At Heuston Station"))
    }

    @Test
    fun `the bus id, when it was last seen and the trip id are listed`() {
        val info = details(vehicle = VehicleDetails("245", "Bus 7", 53.0, -6.0, now - 150)).describe(null, null, now, zone)

        assertEquals("No. 245 (Bus 7)", info.row("Bus"))
        assertEquals("2 min ago", info.row("Last seen"))
        assertEquals("t1", info.row("Trip"))
    }

    @Test
    fun `an untracked bus says so instead of inventing a position`() {
        val info = details(vehicle = null).describe(null, null, now, zone)

        assertTrue(info.row("Bus")!!.startsWith("Not tracked yet"))
        assertNull(info.row("Last seen"))
    }

    @Test
    fun `upcoming stops skip ones already passed, stay in order and are capped`() {
        val calls = listOf(TripStopCall("old", "Passed", now - 900, 0)) +
            (1..12).map { TripStopCall("s$it", "Stop $it", now + it * 60L, if (it == 2) 120 else null) }

        val upcoming = details(calls = calls.reversed()).describe(null, null, now, zone).upcoming

        assertEquals(8, upcoming.size)
        assertEquals("Stop 1", upcoming.first().name)
        assertEquals("2 min late", upcoming[1].note)
        assertEquals("", upcoming[0].note)
    }
}
