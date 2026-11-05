package dev.dublinbustracker.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrivalDisplayTest {
    private val zone = ZoneId.of("Europe/Dublin")
    private val now = 1_700_000_000L // 2023-11-14 22:13:20 UTC, and Dublin is on GMT then

    private fun arrival(
        dueMinutes: Int = 17,
        isRealtime: Boolean = true,
        delaySeconds: Int? = null,
        expected: Long = now + dueMinutes * 60L,
        possiblyPassed: Boolean = false,
    ) = Arrival("4", "Heuston Station", dueMinutes, isRealtime, expectedEpochSeconds = expected, delaySeconds = delaySeconds, possiblyPassed = possiblyPassed)

    @Test
    fun `a live bus leads with minutes and keeps where it goes and lateness smaller`() {
        val shown = arrival(dueMinutes = 17, delaySeconds = 150).display(now, zone)

        assertEquals("17 min", shown.headline)
        assertEquals("4 · Heuston Station", shown.destinationLine)
        assertEquals("2 min late", shown.detailLine)
        assertFalse(shown.muted)
    }

    @Test
    fun `early, on time and unknown delays`() {
        assertEquals("3 min early", arrival(delaySeconds = -200).display(now, zone).detailLine)
        assertEquals("on time", arrival(delaySeconds = 20).display(now, zone).detailLine)
        assertEquals("", arrival(delaySeconds = null).display(now, zone).detailLine)
    }

    @Test
    fun `a bus due now says Due`() {
        assertEquals("Due", arrival(dueMinutes = 0).display(now, zone).headline)
    }

    @Test
    fun `long waits read in hours`() {
        assertEquals("1 h 5 min", arrival(dueMinutes = 65).display(now, zone).headline)
        assertEquals("2 h", arrival(dueMinutes = 120).display(now, zone).headline)
    }

    @Test
    fun `a scheduled bus leads with its timetable time and puts the wait in smaller print`() {
        // 40 min after 22:13 GMT.
        val shown = arrival(dueMinutes = 40, isRealtime = false).display(now, zone)

        assertEquals("22:53", shown.headline)
        assertEquals("Due in 40 min · scheduled", shown.detailLine)
        assertFalse(shown.muted)
    }

    @Test
    fun `the timetable time is shown in the given zone`() {
        val summer = 1_720_000_000L // 2024-07-03 09:46:40 UTC = 10:46 in Dublin (IST)
        val shown = arrival(dueMinutes = 0, isRealtime = false, expected = summer).display(summer, zone)

        assertEquals("10:46", shown.headline)
        assertEquals("Due now · scheduled", shown.detailLine)
    }

    @Test
    fun `a possibly passed bus is muted and says when it was due`() {
        val shown = arrival(dueMinutes = 0, delaySeconds = 240, expected = now - 300, possiblyPassed = true).display(now, zone)

        assertTrue(shown.muted)
        assertEquals("Due", shown.headline)
        assertEquals("May have left · was due 5 min ago", shown.detailLine)
    }
}
