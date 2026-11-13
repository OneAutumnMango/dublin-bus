package dev.dublinbustracker.app

import dev.dublinbustracker.app.detail.ArrivalsUiState
import dev.dublinbustracker.app.detail.StopDetailViewModel
import dev.dublinbustracker.app.map.MapViewModel
import dev.dublinbustracker.app.map.SavedTracking
import dev.dublinbustracker.app.map.TrackedRoute
import dev.dublinbustracker.app.map.TrackingStore
import dev.dublinbustracker.app.map.Viewport
import dev.dublinbustracker.app.route.RouteMapViewModel
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.HiddenDirection
import dev.dublinbustracker.data.ProfileRoute
import dev.dublinbustracker.data.RouteProfile
import dev.dublinbustracker.data.ServerSettings
import dev.dublinbustracker.data.TransitApiClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The view models talk to the server only through [TransitApiClient], so these run it against a
 * canned HTTP layer. Virtual time drives the view models' own delays; the requests themselves
 * run on real IO threads, hence the short real waits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val requests = AtomicInteger()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun client(respond: (Request) -> String): TransitApiClient {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(respond(chain.request()).toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return TransitApiClient({ ServerSettings("http://test/") }, http)
    }

    private fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(condition(), "timed out waiting")
    }

    private fun arrivalsJson(destination: String) =
        """[{"routeShortName":"15","destination":"$destination","dueMinutes":3,"isRealtime":true}]"""

    private val stop = Stop("s1", "1", "Stop", 0.0, 0.0, routes = listOf("15"))

    @Test
    fun `an older slow reply does not overwrite a newer filter's arrivals`() {
        val release = CountDownLatch(1)
        val vm = StopDetailViewModel(
            stop,
            client { request ->
                if (request.url.queryParameter("route") == null) {
                    release.await(5, TimeUnit.SECONDS) // the unfiltered request is the slow one
                    arrivalsJson("Unfiltered")
                } else {
                    arrivalsJson("Filtered")
                }
            },
        )

        vm.toggleRoute("15")
        waitFor { (vm.uiState.value as? ArrivalsUiState.Loaded)?.arrivals?.firstOrNull()?.destination == "Filtered" }
        release.countDown()
        Thread.sleep(300) // time for the stale reply to arrive, if it were going to be used

        val shown = (vm.uiState.value as ArrivalsUiState.Loaded).arrivals.single().destination
        assertEquals("Filtered", shown)
    }

    @Test
    fun `the route map view model makes no requests of its own after loading`() {
        val vm = RouteMapViewModel(
            "t1",
            client { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/shape") -> """{"encodedPolyline":""}"""
                    path.endsWith("/stops") -> """{"directions":{"0":[]}}"""
                    path.endsWith("/vehicles") -> "[]"
                    else -> """{"tripId":"t1","routeId":"r1","routeShortName":"15","directionId":"0"}"""
                }
            },
        )
        waitFor { vm.uiState.value.data != null }
        val afterLoad = requests.get()

        scheduler.advanceTimeBy(10 * 60_000L) // ten minutes: the old loop would have polled twenty times
        Thread.sleep(300)

        assertEquals(afterLoad, requests.get())
        vm.refreshVehicles() // refreshing is the screen's job, one call per tick
        waitFor { requests.get() == afterLoad + 1 }
    }

    @Test
    fun `the map view model polls only when the screen asks it to`() {
        val vm = MapViewModel(client { "[]" })
        vm.onViewportChanged(Viewport(53.34, -6.26, 1_000, 53.33, -6.27, 53.35, -6.25))
        scheduler.advanceTimeBy(1_000)
        vm.toggleBuses()
        waitFor { requests.get() >= 2 } // the stops and the buses
        Thread.sleep(300)
        val settled = requests.get()

        scheduler.advanceTimeBy(10 * 60_000L)
        Thread.sleep(300)
        assertEquals(settled, requests.get())

        vm.poll()
        waitFor { requests.get() == settled + 1 }
    }

    @Test
    fun `followed routes and hidden directions come back after a restart, and changes are saved`() {
        val store = object : TrackingStore {
            var saved = SavedTracking(true, listOf(TrackedRoute("S8", 2)), setOf("S8" to "1"), showStops = false)
            override fun load() = saved
            override fun save(saved: SavedTracking) {
                this.saved = saved
            }
        }
        val vm = MapViewModel(client { "[]" }, store)

        val state = vm.tracking.value
        assertEquals(listOf("S8"), state.routes.map { it.name })
        assertTrue(state.enabled)
        assertEquals(setOf("S8" to "1"), state.hidden)
        assertEquals(false, state.showStops)

        vm.toggleDirection("S8", "0")
        assertEquals(setOf("S8" to "1", "S8" to "0"), store.saved.hidden)
        vm.removeRoute("S8")
        assertEquals(emptyList(), store.saved.routes)
        assertEquals(emptySet(), store.saved.hidden)
    }

    @Test
    fun `several buses can be picked at once and their arrivals are merged by when they are due`() {
        val vm = StopDetailViewModel(
            Stop("s1", "1", "Stop", 0.0, 0.0, routes = listOf("15", "4", "9")),
            client { request ->
                when (request.url.queryParameter("route")) {
                    "15" -> """[{"routeShortName":"15","destination":"A","dueMinutes":9,"isRealtime":true,"expectedEpochSeconds":900}]"""
                    "4" -> """[{"routeShortName":"4","destination":"B","dueMinutes":2,"isRealtime":true,"expectedEpochSeconds":200}]"""
                    else -> "[]"
                }
            },
        )

        vm.toggleRoute("15")
        vm.toggleRoute("4")
        waitFor { (vm.uiState.value as? ArrivalsUiState.Loaded)?.arrivals?.size == 2 }

        assertEquals(setOf("15", "4"), vm.routeFilter.value)
        assertEquals(listOf("4", "15"), (vm.uiState.value as ArrivalsUiState.Loaded).arrivals.map { it.routeShortName })

        vm.toggleRoute("4") // taking one off leaves the other
        waitFor { (vm.uiState.value as? ArrivalsUiState.Loaded)?.arrivals?.map { it.routeShortName } == listOf("15") }
        vm.showAllRoutes()
        assertEquals(emptySet(), vm.routeFilter.value)
    }

    @Test
    fun `loading a profile replaces what is followed, keeping its colours and switched-off directions, and it can be saved again`() {
        val vm = MapViewModel(client { "[]" })
        val profile = RouteProfile(
            "Commute",
            listOf(ProfileRoute("4", 2), ProfileRoute("S8", 0)),
            listOf(HiddenDirection("S8", "1")),
        )

        vm.loadProfile(profile)

        val state = vm.tracking.value
        assertEquals(listOf("4" to 2, "S8" to 0), state.routes.map { it.name to it.colourIndex })
        assertTrue(state.enabled)
        assertEquals(setOf("S8" to "1"), state.hidden)
        assertEquals(profile.copy(name = "Again"), vm.currentProfile("Again"))
    }

    @Test
    fun `the map refresh spinner is on while a refresh is pending, through two quick taps, and off when they finish`() {
        val hold = CountDownLatch(1)
        val vm = MapViewModel(client { hold.await(5, TimeUnit.SECONDS); "[]" })
        vm.onViewportChanged(Viewport(53.34, -6.26, 1_000, 53.33, -6.27, 53.35, -6.25))
        scheduler.advanceTimeBy(1_000)
        val spinner = AtomicBoolean(false)
        val job = CoroutineScope(Dispatchers.Unconfined).launch { vm.refreshing.collect { spinner.set(it) } }

        vm.refreshNow()
        assertTrue(spinner.get(), "on while the request is pending")
        vm.refreshNow() // the second tap restarts the load; the counter now holds two
        assertTrue(spinner.get(), "still on after the second tap")

        hold.countDown()
        waitFor { !spinner.get() }
        assertFalse(spinner.get())
        job.cancel()
    }
}
