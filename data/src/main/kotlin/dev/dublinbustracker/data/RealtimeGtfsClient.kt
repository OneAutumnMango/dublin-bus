package dev.dublinbustracker.data

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TRIP_UPDATES_URL = "https://api.nationaltransport.ie/gtfsr/v2/TripUpdates"

class RealtimeGtfsClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val feedUrl: String = TRIP_UPDATES_URL,
) {
    fun fetchTripUpdates(): FeedMessage {
        val request = Request.Builder().url(feedUrl).header("x-api-key", apiKey).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GTFS-R request failed: ${response.code}" }
            return FeedMessage.parseFrom(response.body!!.byteStream())
        }
    }
}
