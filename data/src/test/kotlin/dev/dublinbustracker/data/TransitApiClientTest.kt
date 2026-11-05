package dev.dublinbustracker.data

import dev.dublinbustracker.core.RouteHeading
import dev.dublinbustracker.core.TripInfo
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
                   "tripId":"t1","routeId":"r1","expectedEpochSeconds":1700,"delaySeconds":120,"vehicleId":"bus-1",
                   "possiblyPassed":true}]""",
            ),
        )

        val arrival = client().arrivals("s1").single()

        assertEquals("15", arrival.routeShortName)
        assertEquals(4, arrival.dueMinutes)
        assertEquals("bus-1", arrival.vehicleId)
        assertEquals(120, arrival.delaySeconds)
        assertTrue(arrival.isRealtime)
        assertTrue(arrival.possiblyPassed)
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

    @Test
    fun `trip info maps route, direction and headsign`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tripId":"t1","routeId":"3 S8 a","routeShortName":"S8","headsign":"Citywest",
                   "directionId":"1","shapeId":"6004_190","stopCalls":[]}""",
            ),
        )

        val trip = client().trip("t1")

        assertEquals("/v1/trips/t1", server.takeRequest().path)
        assertEquals(TripInfo("t1", "3 S8 a", "S8", "Citywest", "1"), trip)
    }

    @Test
    fun `trip shape is decoded from the encoded polyline`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"shapeId":"s","pointCount":3,"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}"""),
        )

        val shape = client().tripShape("t1")

        assertEquals("/v1/trips/t1/shape", server.takeRequest().path)
        assertEquals(3, shape.size)
        assertEquals(38.5, shape[0].lat, 1e-9)
        assertEquals(-120.2, shape[0].lon, 1e-9)
        assertEquals(43.252, shape[2].lat, 1e-9)
        assertEquals(-126.453, shape[2].lon, 1e-9)
    }

    @Test
    fun `route stops returns the requested direction in order`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"routeId":"3 S8 a","shortName":"S8","directions":{
                   "1":[{"id":"a","code":"1","name":"First","lat":1.0,"lon":2.0},{"id":"b","code":"2","name":"Second","lat":1.1,"lon":2.1}]}}""",
            ),
        )

        val stops = client().routeStops("3 S8 a", "1")

        assertEquals("/v1/routes/3%20S8%20a/stops?direction=1", server.takeRequest().path)
        assertEquals(listOf("First", "Second"), stops.map { it.name })
    }

    @Test
    fun `route stops for a direction the route lacks is empty`() = runTest {
        server.enqueue(MockResponse().setBody("""{"routeId":"r","shortName":"1","directions":{"0":[]}}"""))

        assertEquals(emptyList(), client().routeStops("r", "1"))
    }

    @Test
    fun `route vehicles can be limited to one direction`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"vehicleId":"v1","routeShortName":"S8","headsign":"Citywest","lat":1.0,"lon":2.0,"tripId":"t1","directionId":"1"},
                   {"vehicleId":"v2","routeShortName":"S8","headsign":"Dun Laoghaire","lat":1.0,"lon":2.0,"tripId":"t2","directionId":"0"}]""",
            ),
        )
        server.enqueue(MockResponse().setBody("""[{"vehicleId":"v2","directionId":"0"}]"""))

        assertEquals(listOf("v1"), client().vehiclesForRoute("3 S8 a", directionId = "1").map { it.vehicleId })
        assertEquals(listOf("v2"), client().vehiclesForRoute("3 S8 a").map { it.vehicleId })
    }

    @Test
    fun `trip details carry the bus position and upcoming stops`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tripId":"t1","routeId":"r1","routeShortName":"S2","headsign":"Irishtown","directionId":"1","shapeId":"sh",
                   "vehicle":{"vehicleId":"245","label":"","tripId":"t1","routeId":"r1","routeShortName":"S2","headsign":"Irishtown",
                              "directionId":"1","lat":53.29,"lon":-6.14,"timestampEpochSeconds":1700000000},
                   "stopCalls":[{"stopSequence":2,"stopId":"a","stopName":"First","expectedEpochSeconds":1700000100,"delaySeconds":20},
                                {"stopSequence":3,"stopId":"b","stopName":"Second","expectedEpochSeconds":1700000200}]}""",
            ),
        )

        val details = client().tripDetails("t1")

        assertEquals("/v1/trips/t1", server.takeRequest().path)
        assertEquals("S2", details.trip.routeShortName)
        assertEquals("245", details.vehicle?.vehicleId)
        assertEquals(1700000000L, details.vehicle?.timestampEpochSeconds)
        assertEquals(listOf("First", "Second"), details.stopCalls.map { it.stopName })
        assertEquals(listOf<Int?>(20, null), details.stopCalls.map { it.delaySeconds })
    }

    @Test
    fun `a trip with no bus yet has no vehicle`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tripId":"t1","routeId":"r1","routeShortName":"S2","headsign":"X","directionId":"0","shapeId":""}"""))

        val details = client().tripDetails("t1")

        assertEquals(null, details.vehicle)
        assertEquals(emptyList(), details.stopCalls)
    }

    @Test
    fun `a route's shape for a direction is decoded from its polyline`() = runTest {
        server.enqueue(MockResponse().setBody("""{"shapeId":"s","pointCount":3,"encodedPolyline":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}"""))

        val shape = client().routeShape("3 S8 a", "1")

        assertEquals("/v1/routes/3%20S8%20a/shape?direction=1", server.takeRequest().path)
        assertEquals(3, shape.size)
    }

    @Test
    fun `a stop carries which way each route heads there`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"s1","code":"2051","name":"Lanesville","lat":53.28,"lon":-6.16,"routes":["4","L25"],
                   "headings":[{"route":"4","compass":"W"},{"route":"L25","compass":"SW"}]}""",
            ),
        )

        val stop = client().stop("2051")

        assertEquals(listOf(RouteHeading("4", "W"), RouteHeading("L25", "SW")), stop.headings)
    }

    @Test
    fun `an older server with no headings still parses`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"s1","code":"1","name":"X","lat":1.0,"lon":2.0,"routes":["4"]}"""))

        assertEquals(emptyList(), client().stop("1").headings)
    }
}
