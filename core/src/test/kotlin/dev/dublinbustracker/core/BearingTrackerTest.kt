package dev.dublinbustracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BearingTrackerTest {
    private fun bus(lat: Double, lon: Double, bearing: Float? = null) =
        VehiclePosition("v1", "4", "Heuston", LatLon(lat, lon), bearing)

    @Test
    fun `a bus seen for the first time has no heading until it moves`() {
        assertNull(BearingTracker().withBearings(listOf(bus(53.0, -6.0))).single().bearing)
    }

    @Test
    fun `a heading in the feed is used as it is`() {
        assertEquals(123f, BearingTracker().withBearings(listOf(bus(53.0, -6.0, 123f))).single().bearing)
    }

    @Test
    fun `heading is worked out from the move since the last refresh, and held while stopped`() {
        val tracker = BearingTracker()
        tracker.withBearings(listOf(bus(53.000, -6.0)))

        val north = tracker.withBearings(listOf(bus(53.002, -6.0))).single() // about 220 m north
        val stopped = tracker.withBearings(listOf(bus(53.00201, -6.0))).single()

        val heading = north.bearing!!
        assertTrue(heading < 1f || heading > 359f, "was $heading")
        assertEquals(north.bearing, stopped.bearing)
    }

    @Test
    fun `a jump too big to be driving doesn't set a heading`() {
        val tracker = BearingTracker()
        tracker.withBearings(listOf(bus(53.0, -6.0)))

        assertNull(tracker.withBearings(listOf(bus(53.5, -6.0))).single().bearing)
    }
}
