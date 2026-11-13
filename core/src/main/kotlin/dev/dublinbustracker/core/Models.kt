package dev.dublinbustracker.core

data class LatLon(val lat: Double, val lon: Double)

data class Stop(
    val id: String,
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Bus numbers serving this stop, as reported by the server. */
    val routes: List<String> = emptyList(),
    /** Which way each of those routes heads at this stop. */
    val headings: List<RouteHeading> = emptyList(),
)

/** A route's direction of travel at one stop, as a compass point (N, NE, E, SE, S, SW, W, NW) or two joined by "/". */
data class RouteHeading(val route: String, val compass: String)

data class Arrival(
    val routeShortName: String,
    val destination: String,
    val dueMinutes: Int,
    val isRealtime: Boolean,
    val tripId: String = "",
    val routeId: String = "",
    val expectedEpochSeconds: Long = 0,
    val delaySeconds: Int? = null,
    val vehicleId: String? = null,
    /** Its predicted time has gone by without the feed moving on, so it may already have left. */
    val possiblyPassed: Boolean = false,
    /** The vehicle has a GPS fix under 3 minutes old. */
    val hasGps: Boolean = false,
    /** The vehicle has passed the first stop of its trip. */
    val started: Boolean = false,
)

data class Route(
    val id: String,
    val shortName: String,
    val longName: String,
    val agencyId: String = "",
)

data class VehiclePosition(
    val vehicleId: String,
    val routeShortName: String,
    val headsign: String,
    val position: LatLon,
    val bearing: Float? = null,
    val tripId: String = "",
    val directionId: String = "",
)

/** Just enough of a trip to draw it: which route and direction, and where it's heading. */
data class TripInfo(
    val tripId: String,
    val routeId: String,
    val routeShortName: String,
    val headsign: String,
    val directionId: String,
)

/** A bus's last reported position, from the Vehicles feed. */
data class VehicleDetails(
    val vehicleId: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val timestampEpochSeconds: Long,
)

/** One stop a trip still has ahead of it, with when the bus is expected there. */
data class TripStopCall(
    val stopId: String,
    val stopName: String,
    val expectedEpochSeconds: Long,
    val delaySeconds: Int?,
)

/** Everything the server knows about one trip: its route, where its bus is, and its upcoming stops. */
data class TripDetails(
    val trip: TripInfo,
    val vehicle: VehicleDetails?,
    val stopCalls: List<TripStopCall>,
)

/** The server's own health, shown when testing a connection from Settings. */
data class ServerStatus(
    val pollIntervalMs: Long,
    val historyDepth: Int,
    val configuredFeeds: List<String>,
    val stopCount: Int,
    val routeCount: Int,
)
