package dev.dublinbustracker.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What one arrival row says, left to right: the bus number, where it's going, then when it's due
 * (the timetable time for a scheduled one) with how late or early it is beneath.
 */
data class ArrivalDisplay(
    val route: String,
    val destination: String,
    val headline: String,
    val detailLine: String,
    /** Greyed out: the bus may already have gone. */
    val muted: Boolean,
    val hasGps: Boolean = false,
    val started: Boolean = false,
)

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

fun Arrival.display(nowEpochSeconds: Long, zone: ZoneId): ArrivalDisplay {
    val lateness = delaySeconds?.let(::latenessText)
    val details = when {
        possiblyPassed -> listOf(
            "May have left",
            "was due ${minutesText(((nowEpochSeconds - expectedEpochSeconds) / 60).toInt().coerceAtLeast(1))} ago",
        )
        !isRealtime -> listOf(if (dueMinutes <= 0) "Due now" else "Due in ${minutesText(dueMinutes)}", "scheduled")
        else -> listOfNotNull(lateness)
    }
    return ArrivalDisplay(
        headline = when {
            possiblyPassed -> "Due"
            !isRealtime -> CLOCK_FORMAT.format(Instant.ofEpochSecond(expectedEpochSeconds).atZone(zone))
            dueMinutes <= 0 -> "Due"
            else -> minutesText(dueMinutes)
        },
        route = routeShortName,
        destination = destination,
        detailLine = details.joinToString(" · "),
        muted = possiblyPassed,
        hasGps = isRealtime && hasGps,
        started = isRealtime && started && !possiblyPassed,
    )
}

internal fun minutesText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

internal fun latenessText(delaySeconds: Int): String {
    val minutes = delaySeconds / 60
    return when {
        minutes >= 1 -> "$minutes min late"
        minutes <= -1 -> "${-minutes} min early"
        else -> "on time"
    }
}

/** The routes at a stop with the way each goes, as small print: "4 (W)  L25 (W)  L27 (SW)". */
fun headingsText(headings: List<RouteHeading>): String =
    headings.joinToString("  ") { "${it.route} (${it.compass})" }
