package dev.dublinbustracker.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransitApiClientTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun client(headers: List<HttpHeader> = listOf(HttpHeader("X-Api-Key", "secret"))) =
        TransitApiClient(settingsProvider = { ServerSettings(server.url("/").toString(), headers) })

    @Test
    fun `stop search hits the v1 endpoint and sends configured headers`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"s1","code":"1001","name":"Trinity College","lat":53.34,"lon":-6.25,"routes":["15","27"]}]""",
            ),
        )

        val stops = client(
            listOf(HttpHeader("X-Api-Key", "secret"), HttpHeader("CF-Access-Client-Id", "abc")),
        ).searchStops("trinity")

        assertEquals(1, stops.size)
        assertEquals(listOf("15", "27"), stops.single().routes)

        val request = server.takeRequest()
        assertEquals("/v1/stops?query=trinity&limit=50", request.path)
        assertEquals("secret", request.getHeader("X-Api-Key"))
        assertEquals("abc", request.getHeader("CF-Access-Client-Id"))
    }

    @Test
    fun `nearby stops pass coordinates and radius`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        client().nearbyStops(53.34, -6.25, radiusMeters = 400, limit = 10)

        assertEquals("/v1/stops/nearby?lat=53.34&lon=-6.25&radius=400&limit=10", server.takeRequest().path)
    }

    @Test
    fun `arrivals map every field the server returns`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"routeShortName":"15","destination":"Clongriffin","dueMinutes":4,"isRealtime":true,
                   "tripId":"t1","routeId":"r1","expectedEpochSeconds":1700,"delaySeconds":120,"vehicleId":"bus-1"}]""",
            ),
        )

        val arrival = client().arrivals("s1").single()

        assertEquals("15", arrival.routeShortName)
        assertEquals(4, arrival.dueMinutes)
        assertEquals("bus-1", arrival.vehicleId)
        assertEquals(120, arrival.delaySeconds)
        assertTrue(arrival.isRealtime)
    }

    @Test
    fun `a route filter is only sent when present`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        client().arrivals("s1", route = "15", limit = 5)
        assertEquals("/v1/stops/s1/arrivals?route=15&limit=5", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("[]"))
        client().arrivals("s1", route = null, limit = 5)
        assertEquals("/v1/stops/s1/arrivals?limit=5", server.takeRequest().path)
    }

    @Test
    fun `status surfaces the bundle counts used by the settings test button`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"pollIntervalMs":60000,"historyDepth":30,"configuredFeeds":["TRIP_UPDATES"],
                   "staticData":{"stops":9909,"routes":382}}""",
            ),
        )

        val status = client().status()

        assertEquals(60_000, status.pollIntervalMs)
        assertEquals(9909, status.stopCount)
        assertEquals(382, status.routeCount)
        assertEquals(listOf("TRIP_UPDATES"), status.configuredFeeds)
    }

    @Test
    fun `vehicles in a bounding box are requested as bbox`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"vehicleId":"bus-1","routeShortName":"15","headsign":"Clongriffin","lat":53.3,"lon":-6.2,"bearing":90.0}]""",
            ),
        )

        val vehicles = client().vehiclesInBox(53.0, -6.5, 53.5, -6.0)

        assertEquals("bus-1", vehicles.single().vehicleId)
        assertEquals(90.0f, vehicles.single().bearing)
        assertTrue(server.takeRequest().path!!.contains("bbox=53.0%2C-6.5%2C53.5%2C-6.0"))
    }

    @Test
    fun `an unconfigured server fails before any request is made`() = runTest {
        val client = TransitApiClient(settingsProvider = { ServerSettings.EMPTY })
        val error = assertFailsWith<TransitApiException> { client.searchStops("x") }
        assertTrue(error.message!!.contains("No server configured"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a 401 explains that the auth headers are wrong`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"missing or invalid X-Api-Key"}"""))

        val error = assertFailsWith<TransitApiException> { client().searchStops("x") }

        assertEquals(401, error.statusCode)
        assertTrue(error.message!!.contains("Settings"), error.message!!)
    }

    @Test
    fun `a server error message is surfaced to the caller`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"unknown stop"}"""))

        val error = assertFailsWith<TransitApiException> { client().stop("nope") }

        assertEquals("unknown stop", error.message)
    }

    @Test
    fun `an unparseable body is reported as an unexpected response`() = runTest {
        server.enqueue(MockResponse().setBody("<html>not json</html>"))

        val error = assertFailsWith<TransitApiException> { client().searchStops("x") }

        assertTrue(error.message!!.contains("Unexpected response"), error.message!!)
    }

    @Test
    fun `an unreachable host is reported without leaking a stack trace`() = runTest {
        val client = TransitApiClient(settingsProvider = { ServerSettings("http://127.0.0.1:1/") })

        val error = assertFailsWith<TransitApiException> { client.searchStops("x") }

        assertTrue(error.message!!.startsWith("Cannot reach"), error.message!!)
    }

    @Test
    fun `settings changes take effect without rebuilding the client`() = runTest {
        var settings = ServerSettings(server.url("/").toString(), listOf(HttpHeader("X-Api-Key", "first")))
        val client = TransitApiClient(settingsProvider = { settings })

        server.enqueue(MockResponse().setBody("[]"))
        client.searchStops("a")
        assertEquals("first", server.takeRequest().getHeader("X-Api-Key"))

        settings = settings.copy(headers = listOf(HttpHeader("X-Api-Key", "second")))
        server.enqueue(MockResponse().setBody("[]"))
        client.searchStops("b")
        assertEquals("second", server.takeRequest().getHeader("X-Api-Key"))
    }
}
