package dev.dublinbustracker.data

import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.LatLon
import dev.dublinbustracker.core.Route
import dev.dublinbustracker.core.RouteHeading
import dev.dublinbustracker.core.ServerStatus
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.TripDetails
import dev.dublinbustracker.core.TripInfo
import dev.dublinbustracker.core.TripStopCall
import dev.dublinbustracker.core.VehicleDetails
import dev.dublinbustracker.core.VehiclePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Keeps OkHttp an implementation detail of this module rather than the caller's problem. */
private fun defaultHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        // The server never redirects, but a login wall in front of it (Cloudflare Access) does when
        // the headers are wrong. Following it would hand back an HTML page and a baffling parse error.
        .followRedirects(false)
        .build()

@Serializable
private data class StopDto(
    val id: String,
    val code: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val routes: List<String> = emptyList(),
    val headings: List<HeadingDto> = emptyList(),
)

@Serializable
private data class HeadingDto(val route: String, val compass: String)

@Serializable
private data class ArrivalDto(
    val routeShortName: String = "",
    val destination: String = "",
    val dueMinutes: Int = 0,
    val isRealtime: Boolean = false,
    val tripId: String = "",
    val routeId: String = "",
    val expectedEpochSeconds: Long = 0,
    val delaySeconds: Int? = null,
    val vehicleId: String? = null,
    val possiblyPassed: Boolean = false,
)

@Serializable
private data class RouteDto(
    val id: String,
    val shortName: String = "",
    val longName: String = "",
    val agencyId: String = "",
)

@Serializable
private data class VehicleDto(
    val vehicleId: String,
    val routeShortName: String = "",
    val headsign: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val bearing: Float? = null,
    val tripId: String = "",
    val directionId: String = "",
)

@Serializable
private data class TripVehicleDto(
    val vehicleId: String,
    val label: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val timestampEpochSeconds: Long = 0,
)

@Serializable
private data class TripStopCallDto(
    val stopId: String,
    val stopName: String = "",
    val expectedEpochSeconds: Long = 0,
    val delaySeconds: Int? = null,
)

@Serializable
private data class TripDetailsDto(
    val tripId: String,
    val routeId: String = "",
    val routeShortName: String = "",
    val headsign: String = "",
    val directionId: String = "",
    val vehicle: TripVehicleDto? = null,
    val stopCalls: List<TripStopCallDto> = emptyList(),
)

@Serializable
private data class ShapeDto(val encodedPolyline: String = "")

@Serializable
private data class RouteStopsDto(val directions: Map<String, List<StopDto>> = emptyMap())

@Serializable
private data class StatusDto(
    val pollIntervalMs: Long = 0,
    val historyDepth: Int = 0,
    val configuredFeeds: List<String> = emptyList(),
    val staticData: Map<String, Int> = emptyMap(),
)

@Serializable
private data class ErrorDto(val error: String = "")

/** Thrown for any non-2xx response, carrying a message fit to show in the UI. */
class TransitApiException(message: String, val statusCode: Int? = null) : Exception(message)

/**
 * Talks to the Dublin Bus Server. Settings are read per request rather than captured at
 * construction, so changing the endpoint in Settings takes effect immediately.
 */
class TransitApiClient(
    private val settingsProvider: () -> ServerSettings,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun status(): ServerStatus = get("v1/status") { body ->
        val dto = json.decodeFromString<StatusDto>(body)
        ServerStatus(
            pollIntervalMs = dto.pollIntervalMs,
            historyDepth = dto.historyDepth,
            configuredFeeds = dto.configuredFeeds,
            stopCount = dto.staticData["stops"] ?: 0,
            routeCount = dto.staticData["routes"] ?: 0,
        )
    }

    suspend fun searchStops(query: String, limit: Int = 50): List<Stop> =
        get("v1/stops", "query" to query, "limit" to limit.toString()) { body ->
            json.decodeFromString<List<StopDto>>(body).map { it.toStop() }
        }

    suspend fun nearbyStops(lat: Double, lon: Double, radiusMeters: Int, limit: Int = 50): List<Stop> =
        get(
            "v1/stops/nearby",
            "lat" to lat.toString(),
            "lon" to lon.toString(),
            "radius" to radiusMeters.toString(),
            "limit" to limit.toString(),
        ) { body -> json.decodeFromString<List<StopDto>>(body).map { it.toStop() } }

    suspend fun stop(stopIdOrCode: String): Stop =
        get("v1/stops/${seg(stopIdOrCode)}") { body -> json.decodeFromString<StopDto>(body).toStop() }

    suspend fun arrivals(stopId: String, route: String? = null, limit: Int = 20): List<Arrival> =
        get(
            "v1/stops/${seg(stopId)}/arrivals",
            "route" to route,
            "limit" to limit.toString(),
        ) { body -> json.decodeFromString<List<ArrivalDto>>(body).map { it.toArrival() } }

    /** Arrivals at every stop on [route] in one request, by stop id; stops with nothing coming are absent. */
    suspend fun routeArrivals(route: String, limit: Int = 2): Map<String, List<Arrival>> =
        get("v1/routes/${seg(route)}/arrivals", "limit" to limit.toString()) { body ->
            json.decodeFromString<Map<String, List<ArrivalDto>>>(body).mapValues { (_, arrivals) -> arrivals.map { it.toArrival() } }
        }

    suspend fun routes(query: String = "", limit: Int = 100): List<Route> =
        get("v1/routes", "query" to query, "limit" to limit.toString()) { body ->
            json.decodeFromString<List<RouteDto>>(body).map { Route(it.id, it.shortName, it.longName, it.agencyId) }
        }

    /** Live buses inside a map viewport, as `minLat,minLon,maxLat,maxLon`. */
    suspend fun vehiclesInBox(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        limit: Int = 200,
    ): List<VehiclePosition> =
        get(
            "v1/vehicles",
            "bbox" to "$minLat,$minLon,$maxLat,$maxLon",
            "limit" to limit.toString(),
        ) { body -> json.decodeFromString<List<VehicleDto>>(body).map { it.toVehicle() } }

    /** Live buses on a route, optionally only those running in one [directionId]. */
    suspend fun vehiclesForRoute(route: String, directionId: String? = null): List<VehiclePosition> =
        get("v1/routes/${seg(route)}/vehicles") { body ->
            json.decodeFromString<List<VehicleDto>>(body).map { it.toVehicle() }
                .filter { directionId == null || it.directionId == directionId }
        }

    suspend fun trip(tripId: String): TripInfo = tripDetails(tripId).trip

    /** A trip's route, its bus's last position and the stops it has still to make. */
    suspend fun tripDetails(tripId: String): TripDetails =
        get("v1/trips/${seg(tripId)}") { body ->
            val dto = json.decodeFromString<TripDetailsDto>(body)
            TripDetails(
                trip = TripInfo(dto.tripId, dto.routeId, dto.routeShortName, dto.headsign, dto.directionId),
                vehicle = dto.vehicle?.let { VehicleDetails(it.vehicleId, it.label, it.lat, it.lon, it.timestampEpochSeconds) },
                stopCalls = dto.stopCalls.map { TripStopCall(it.stopId, it.stopName, it.expectedEpochSeconds, it.delaySeconds) },
            )
        }

    /** The path a trip drives, as map points. */
    suspend fun tripShape(tripId: String): List<LatLon> =
        get("v1/trips/${seg(tripId)}/shape") { body -> decodePolyline(json.decodeFromString<ShapeDto>(body).encodedPolyline) }

    /** The path a route drives in one direction. */
    suspend fun routeShape(route: String, directionId: String): List<LatLon> =
        get("v1/routes/${seg(route)}/shape", "direction" to directionId) { body ->
            decodePolyline(json.decodeFromString<ShapeDto>(body).encodedPolyline)
        }

    /** A route's stops in running order for one direction. */
    suspend fun routeStops(route: String, directionId: String): List<Stop> =
        get("v1/routes/${seg(route)}/stops", "direction" to directionId) { body ->
            json.decodeFromString<RouteStopsDto>(body).directions[directionId].orEmpty().map { it.toStop() }
        }

    /** One path segment from text a person typed: "/" and "?" stay data, and "." or ".." can't climb out of it. */
    private fun seg(value: String): String {
        if (value == "." || value == "..") throw TransitApiException("'$value' is not a valid name")
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private suspend fun <T> get(
        path: String,
        vararg query: Pair<String, String?>,
        parse: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        if (!settings.isConfigured) {
            throw TransitApiException("No server configured - set the address in Settings")
        }
        val base = ServerSettings.normaliseBaseUrl(settings.baseUrl).toHttpUrlOrNull()
            ?: throw TransitApiException("Server address is not a valid URL: ${settings.baseUrl}")

        val url: HttpUrl = base.newBuilder()
            .addEncodedPathSegments(path)
            .apply {
                query.forEach { (name, value) ->
                    if (!value.isNullOrBlank()) addQueryParameter(name, value)
                }
            }
            .build()

        val request = Request.Builder().url(url).apply {
            settings.headerMap().forEach { (name, value) -> header(name, value) }
        }.build()

        val response = runCatching { httpClient.newCall(request).execute() }
            .getOrElse { throw TransitApiException("Cannot reach ${base.host}: ${it.message}") }

        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw TransitApiException(describe(it.code, body), it.code)
            runCatching { parse(body) }
                .getOrElse { error -> throw TransitApiException("Unexpected response from server: ${error.message}") }
        }
    }

    private fun describe(code: Int, body: String): String {
        val serverMessage = runCatching { json.decodeFromString<ErrorDto>(body).error }.getOrNull()
        return when {
            code == 401 || code == 403 -> "Rejected by the server ($code) - check the auth headers in Settings"
            code in 300..399 -> "Sent to a login page ($code) - check the auth headers in Settings"
            code == 404 -> serverMessage?.takeIf { it.isNotBlank() } ?: "Not found (404)"
            !serverMessage.isNullOrBlank() -> "$serverMessage ($code)"
            else -> "Server returned HTTP $code"
        }
    }

    private fun StopDto.toStop() = Stop(id, code, name, lat, lon, routes, headings.map { RouteHeading(it.route, it.compass) })

    private fun ArrivalDto.toArrival() = Arrival(
        routeShortName = routeShortName,
        destination = destination,
        dueMinutes = dueMinutes,
        isRealtime = isRealtime,
        tripId = tripId,
        routeId = routeId,
        expectedEpochSeconds = expectedEpochSeconds,
        delaySeconds = delaySeconds,
        vehicleId = vehicleId,
        possiblyPassed = possiblyPassed,
    )

    private fun VehicleDto.toVehicle() =
        VehiclePosition(vehicleId, routeShortName, headsign, LatLon(lat, lon), bearing, tripId, directionId)
}
