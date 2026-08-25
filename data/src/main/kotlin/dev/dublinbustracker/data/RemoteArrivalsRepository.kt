package dev.dublinbustracker.data

import dev.dublinbustracker.core.Arrival
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class ArrivalDto(val routeShortName: String, val destination: String, val dueMinutes: Int, val isRealtime: Boolean)

/**
 * Asks our own server for arrivals instead of calling NTA directly, so the app never has to
 * care about its rate limit. [headers] are sent on every request as-is, so whatever the server
 * (or anything in front of it) needs for auth - an API key, a bearer token, multiple headers
 * for a proxy - can be configured without this class knowing about any particular scheme.
 */
class RemoteArrivalsRepository(
    private val baseUrl: String,
    private val headers: Map<String, String> = emptyMap(),
    override val pollIntervalMs: Long = 15_000L,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : ArrivalsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun arrivalsForStop(stopId: String): List<Arrival> = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("arrivals")
            .addQueryParameter("stopId", stopId)
            .build()
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            check(response.isSuccessful) { "Server request failed: ${response.code}" }
            json.decodeFromString<List<ArrivalDto>>(response.body!!.string())
                .map { Arrival(it.routeShortName, it.destination, it.dueMinutes, it.isRealtime) }
        }
    }
}

/** Parses "Name1=Value1,Name2=Value2" (as stored in local.properties) into a header map. */
fun parseHeaders(raw: String): Map<String, String> =
    raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val (name, value) = pair.split("=", limit = 2)
            name.trim() to value.trim()
        }

