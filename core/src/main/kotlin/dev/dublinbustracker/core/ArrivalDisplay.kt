package dev.dublinbustracker.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What one arrival row says. The headline is when the bus is due (the timetable time for a
 * scheduled one); where it's going and how late it is sit below in smaller print.
 */
data class ArrivalDisplay(
    val headline: String,
    val destinationLine: String,
    val detailLine: String,
    /** Greyed out: the bus may already have gone. */
    val muted: Boolean,
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
        destinationLine = "$routeShortName · $destination",
        detailLine = details.joinToString(" · "),
        muted = possiblyPassed,
    )
}

private fun minutesText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun latenessText(delaySeconds: Int): String {
    val minutes = delaySeconds / 60
    return when {
        minutes >= 1 -> "$minutes min late"
        minutes <= -1 -> "${-minutes} min early"
        else -> "on time"
    }
}
