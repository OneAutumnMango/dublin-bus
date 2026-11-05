package dev.dublinbustracker.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class InfoRow(val label: String, val value: String)

data class UpcomingStop(val time: String, val name: String, val note: String)

/** What the bus popup shows, already worded. */
data class BusInfo(val title: String, val rows: List<InfoRow>, val upcoming: List<UpcomingStop>)

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private const val MAX_UPCOMING = 8
private const val PAST_GRACE_SECONDS = 120L

/**
 * Words everything known about one bus. [contextStop] is the stop being looked at, and [arrival]
 * (when the bus was tapped from that stop's list) supplies the figures the list already showed.
 */
fun TripDetails.describe(contextStop: Stop?, arrival: Arrival?, now: Long, zone: ZoneId): BusInfo {
    fun clock(epoch: Long) = CLOCK.format(Instant.ofEpochSecond(epoch).atZone(zone))

    val rows = mutableListOf<InfoRow>()

    if (contextStop != null) {
        val call = stopCalls.firstOrNull { it.stopId == contextStop.id }
        val expected = arrival?.expectedEpochSeconds ?: call?.expectedEpochSeconds
        val delay = arrival?.delaySeconds ?: call?.delaySeconds
        if (expected != null && expected > 0) {
            val wait = ((expected - now) / 60).toInt()
            rows += InfoRow(
                "At ${contextStop.name}",
                listOfNotNull(
                    clock(expected),
                    when {
                        arrival?.possiblyPassed == true -> "may have left"
                        wait <= 0 -> "due now"
                        else -> "in ${minutesText(wait)}"
                    },
                    delay?.let(::latenessText),
                    if (arrival != null && !arrival.isRealtime) "timetable only - no live data yet" else null,
                ).joinToString(" · "),
            )
            // Only worth a row when the bus is noticeably off it, and we actually know by how much.
            if (delay != null && kotlin.math.abs(delay) >= 60 && arrival?.isRealtime != false) {
                rows += InfoRow("Timetable said", clock(expected - delay))
            }
        }
    }

    rows += InfoRow("Bus", vehicle?.let { v -> "No. ${v.vehicleId}" + v.label.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty() }
        ?: "Not tracked yet - it may not have left its first stop")
    vehicle?.let { rows += InfoRow("Last seen", agoText(now - it.timestampEpochSeconds)) }
    rows += InfoRow("Trip", trip.tripId)

    return BusInfo(
        title = "${trip.routeShortName} · ${trip.headsign}",
        rows = rows,
        upcoming = stopCalls
            .filter { it.expectedEpochSeconds >= now - PAST_GRACE_SECONDS }
            .sortedBy { it.expectedEpochSeconds }
            .take(MAX_UPCOMING)
            .map { UpcomingStop(clock(it.expectedEpochSeconds), it.stopName, it.delaySeconds?.let(::latenessText).orEmpty()) },
    )
}

private fun agoText(seconds: Long): String = when {
    seconds < 60 -> "${seconds.coerceAtLeast(0)} s ago"
    seconds < 3600 -> "${seconds / 60} min ago"
    else -> "over an hour ago"
}
