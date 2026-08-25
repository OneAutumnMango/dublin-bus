package dev.dublinbustracker.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteArrivalsRepositoryTest {
    @Test
    fun `requests arrivals for the stop and maps the response, sending the api key`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """[{"routeShortName":"15","destination":"Clongriffin","dueMinutes":4,"isRealtime":true}]""",
            ),
        )
        server.start()

        val repository = RemoteArrivalsRepository(baseUrl = server.url("/").toString(), apiKey = "secret")
        val arrivals = repository.arrivalsForStop("s1")

        assertEquals(1, arrivals.size)
        assertEquals("15", arrivals[0].routeShortName)
        assertEquals(4, arrivals[0].dueMinutes)

        val request: RecordedRequest = server.takeRequest()
        assertEquals("secret", request.getHeader("X-Api-Key"))
        assertEquals("/arrivals?stopId=s1", request.path)

        server.shutdown()
    }
}
