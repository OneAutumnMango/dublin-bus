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

/** Asks our own server for arrivals instead of calling NTA directly, so the app never has to care about its rate limit. */
class RemoteArrivalsRepository(
    private val baseUrl: String,
    private val apiKey: String,
    override val pollIntervalMs: Long = 15_000L,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : ArrivalsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun arrivalsForStop(stopId: String): List<Arrival> = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("arrivals")
            .addQueryParameter("stopId", stopId)
            .build()
        val request = Request.Builder().url(url).header("X-Api-Key", apiKey).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Server request failed: ${response.code}" }
            json.decodeFromString<List<ArrivalDto>>(response.body!!.string())
                .map { Arrival(it.routeShortName, it.destination, it.dueMinutes, it.isRealtime) }
        }
    }
}
