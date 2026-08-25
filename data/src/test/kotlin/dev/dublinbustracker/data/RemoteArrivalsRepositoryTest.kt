package dev.dublinbustracker.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteArrivalsRepositoryTest {
    @Test
    fun `requests arrivals for the stop and maps the response, sending configured headers`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """[{"routeShortName":"15","destination":"Clongriffin","dueMinutes":4,"isRealtime":true}]""",
            ),
        )
        server.start()

        val repository = RemoteArrivalsRepository(
            baseUrl = server.url("/").toString(),
            headers = mapOf("X-Api-Key" to "secret", "CF-Access-Client-Id" to "client-id"),
        )
        val arrivals = repository.arrivalsForStop("s1")

        assertEquals(1, arrivals.size)
        assertEquals("15", arrivals[0].routeShortName)
        assertEquals(4, arrivals[0].dueMinutes)

        val request: RecordedRequest = server.takeRequest()
        assertEquals("secret", request.getHeader("X-Api-Key"))
        assertEquals("client-id", request.getHeader("CF-Access-Client-Id"))
        assertEquals("/arrivals?stopId=s1", request.path)

        server.shutdown()
    }

    @Test
    fun `parseHeaders splits name-value pairs on comma then first equals`() {
        val headers = parseHeaders("X-Api-Key=secret,CF-Access-Client-Id=abc=def")
        assertEquals(mapOf("X-Api-Key" to "secret", "CF-Access-Client-Id" to "abc=def"), headers)
    }

    @Test
    fun `parseHeaders on a blank string is an empty map`() {
        assertEquals(emptyMap(), parseHeaders(""))
    }
}
