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
)

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
)

/** The server's own health, shown when testing a connection from Settings. */
data class ServerStatus(
    val pollIntervalMs: Long,
    val historyDepth: Int,
    val configuredFeeds: List<String>,
    val stopCount: Int,
    val routeCount: Int,
)
