package dev.dublinbustracker.app.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.BearingTracker
import dev.dublinbustracker.core.LatLon
import dev.dublinbustracker.core.MAX_TRACKED_ROUTES
import dev.dublinbustracker.core.Route
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.destinationsOf
import dev.dublinbustracker.core.distanceMeters
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.dropShortHooks
import dev.dublinbustracker.core.stopTimesLabel
import dev.dublinbustracker.core.nextColourIndex
import dev.dublinbustracker.core.rankRouteSuggestions
import dev.dublinbustracker.core.VehiclePosition
import dev.dublinbustracker.data.HiddenDirection
import dev.dublinbustracker.data.ProfileRoute
import dev.dublinbustracker.data.RouteProfile
import dev.dublinbustracker.data.TransitApiClient
import dev.dublinbustracker.data.TransitApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class MapUiState(
    val stops: List<Stop> = emptyList(),
    val vehicles: List<VehiclePosition> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
)

data class Viewport(
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Int,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    /** Camera zoom, 0 when unknown. */
    val zoom: Double = 0.0,
)

/** A route the user is following, with the colour slot its buses and path are drawn in. */
data class TrackedRoute(val name: String, val colourIndex: Int)

data class TrackingUiState(
    val enabled: Boolean = false,
    val routes: List<TrackedRoute> = emptyList(),
    /** Live buses by route name. */
    val vehicles: Map<String, List<VehiclePosition>> = emptyMap(),
    /** Each route's path, by route name then direction id. */
    val shapes: Map<String, Map<String, List<LatLon>>> = emptyMap(),
    /** Each route's stops in order, by route name then direction id. */
    val stops: Map<String, Map<String, List<Stop>>> = emptyMap(),
    val showStops: Boolean = true,
    /** Label the stops in view with their next arrivals once zoomed in far enough. */
    val showStopTimes: Boolean = false,
    /** Those labels, by stop id. */
    val stopTimes: Map<String, String> = emptyMap(),
    /** (route, direction) pairs switched off in the Directions dropdown. */
    val hidden: Set<Pair<String, String>> = emptySet(),
    val message: String? = null,
    val busy: Boolean = false,
) {
    /** Where each direction ends up: a label for a direction with no bus right now. */
    val destinations: Map<String, Map<String, String>> get() = destinationsOf(stops)
}

/** The server takes in new positions about once a minute; asking this often keeps the map within a few seconds of it. */
const val BUS_REFRESH_MS = 15_000L
private const val SUGGEST_DEBOUNCE_MS = 250L
private const val PAN_DEBOUNCE_MS = 400L
private const val MAX_RADIUS_METERS = 5_000

/** Below this the whole city is a few screens wide and a label per stop would be noise; that is roughly a followed route fitted to the screen. */
const val STOP_TIMES_MIN_ZOOM = 10.5
private const val STOP_TIMES_MAX_AGE_MS = 60_000L

class MapViewModel(
    private val apiClient: TransitApiClient,
    private val store: TrackingStore = NoTrackingStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _showBuses = MutableStateFlow(false)
    val showBuses: StateFlow<Boolean> = _showBuses.asStateFlow()

    private val browseBearings = BearingTracker()
    private val trackedBearings = BearingTracker()

    private var loadJob: Job? = null

    // A count, not a flag: a second tap while the first is still waiting must not clear the spinner early.
    private val refreshesRunning = MutableStateFlow(0)
    /** True while a tap on the refresh button is still waiting for replies. */
    val refreshing = refreshesRunning.map { it > 0 }.distinctUntilChanged()
    private var lastViewport: Viewport? = null

    private val saved = store.load()
    private val _tracking = MutableStateFlow(
        TrackingUiState(
            enabled = saved.enabled,
            routes = saved.routes,
            hidden = saved.hidden,
            showStops = saved.showStops,
            showStopTimes = saved.showStopTimes,
        ),
    )
    val tracking: StateFlow<TrackingUiState> = _tracking.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Route>>(emptyList())
    /** Routes matching what's been typed in the add-a-route box. */
    val suggestions: StateFlow<List<Route>> = _suggestions.asStateFlow()
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            _tracking.map { SavedTracking(it.enabled, it.routes, it.hidden, it.showStops, it.showStopTimes) }.distinctUntilChanged().collect(store::save)
        }
        saved.routes.forEach { route -> viewModelScope.launch { restore(route.name) } }
    }

    /** Fetches a route remembered from last time. Unlike adding one, a failure keeps it listed; its buses come with the next refresh. */
    private suspend fun restore(name: String) {
        val vehicles = runCatching { apiClient.vehiclesForRoute(name) }.getOrNull()
        val (shapes, stops) = fetchPaths(name)
        _tracking.update { state ->
            if (state.routes.none { it.name == name }) {
                state
            } else {
                state.copy(
                    vehicles = if (vehicles != null) state.vehicles + (name to trackedBearings.withBearings(vehicles)) else state.vehicles,
                    shapes = state.shapes + (name to shapes),
                    stops = state.stops + (name to stops),
                )
            }
        }
    }

    /** A route's path and stops in both directions. They're a nicety: if they can't be fetched the buses still show. */
    private suspend fun fetchPaths(name: String): Pair<Map<String, List<LatLon>>, Map<String, List<Stop>>> = coroutineScope {
        val directions = listOf("0", "1")
        val shapeCalls = directions.map { direction ->
            async { runCatching { apiClient.routeShape(name, direction) }.getOrNull()?.let { direction to it } }
        }
        val stopCalls = directions.map { direction ->
            async { runCatching { apiClient.routeStops(name, direction) }.getOrNull()?.let { direction to it } }
        }
        shapeCalls.awaitAll().filterNotNull().toMap().mapValues { (_, path) -> dropShortHooks(path) } to stopCalls.awaitAll().filterNotNull().toMap()
    }

    fun onRouteQueryChanged(query: String) {
        suggestJob?.cancel()
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS) // typing is fast; wait for a pause before asking the server
            _suggestions.value = runCatching { apiClient.routes(query.trim(), limit = 200) }
                .map { rankRouteSuggestions(query, it) }
                .getOrDefault(emptyList())
        }
    }

    /** Replaces what is being followed with a saved profile: its routes, their colours and the directions that were off. */
    fun loadProfile(profile: RouteProfile) {
        routeFetchedAt.clear()
        routeArrivals.clear()
        _tracking.update {
            it.copy(
                enabled = true,
                routes = profile.routes.map { route -> TrackedRoute(route.name, route.colourIndex) },
                hidden = profile.hidden.map { hidden -> hidden.route to hidden.direction }.toSet(),
                vehicles = emptyMap(),
                shapes = emptyMap(),
                stops = emptyMap(),
                stopTimes = emptyMap(),
                message = null,
            )
        }
        profile.routes.forEach { route -> viewModelScope.launch { restore(route.name) } }
    }

    /** The routes and switched-off directions as they stand now, to save as a profile called [name]. */
    fun currentProfile(name: String): RouteProfile = _tracking.value.let { state ->
        RouteProfile(
            name,
            state.routes.map { ProfileRoute(it.name, it.colourIndex) },
            state.hidden.map { (route, direction) -> HiddenDirection(route, direction) },
        )
    }

    private var timesJob: Job? = null
    private val routeFetchedAt = HashMap<String, Long>()
    /** Each followed route's next arrivals, by route then stop id, as last fetched. */
    private val routeArrivals = HashMap<String, Map<String, List<Arrival>>>()

    fun setShowStopTimes(on: Boolean) {
        _tracking.update { it.copy(showStopTimes = on, stopTimes = if (on) it.stopTimes else emptyMap()) }
        if (on) refreshStopTimes(force = true)
    }

    /**
     * Keeps the next-arrivals labels for every stop on the followed routes up to date, wherever the map is looking,
     * so panning and zooming never waits on the network. One request per route (not per stop) a minute, and the
     * labels are rebuilt from what was fetched each time so their "min" counts keep ticking down.
     */
    private fun refreshStopTimes(force: Boolean = false) {
        val state = _tracking.value
        if (!state.enabled || !state.showStopTimes || state.routes.isEmpty()) {
            timesJob?.cancel()
            if (state.stopTimes.isNotEmpty()) _tracking.update { it.copy(stopTimes = emptyMap()) }
            return
        }
        rebuildStopTimes()
        val now = System.currentTimeMillis()
        val due = state.routes.filter { force || now - (routeFetchedAt[it.name] ?: 0L) > STOP_TIMES_MAX_AGE_MS }
        if (due.isEmpty() || (timesJob?.isActive == true && !force)) return

        timesJob?.cancel()
        timesJob = viewModelScope.launch {
            coroutineScope {
                due.forEach { route ->
                    launch {
                        // A failed fetch keeps the times it has; only an answer replaces them.
                        val fetched = runCatching { apiClient.routeArrivals(route.name, limit = 2) }.getOrNull() ?: return@launch
                        routeArrivals[route.name] = fetched
                        routeFetchedAt[route.name] = now
                        rebuildStopTimes()
                    }
                }
            }
        }
    }

    /** Builds each stop's label from the fetched arrivals of the followed routes calling at it, skipping switched-off directions. */
    private fun rebuildStopTimes() {
        val state = _tracking.value
        val nowSeconds = System.currentTimeMillis() / 1000
        val routesAt = HashMap<String, MutableSet<String>>()
        val stopIds = HashSet<String>()
        state.routes.forEach { route ->
            state.stops[route.name].orEmpty().filterKeys { (route.name to it) !in state.hidden }.values.flatten().forEach {
                routesAt.getOrPut(it.id) { sortedSetOf() }.add(route.name)
            }
        }
        val labels = routesAt.mapNotNull { (stopId, routes) ->
            stopTimesLabel(routes.map { it to routeArrivals[it]?.get(stopId).orEmpty() }, nowSeconds, ZoneId.systemDefault())?.let { stopId to it }
        }.toMap()
        _tracking.update { it.copy(stopTimes = labels) }
    }

    fun setShowStops(on: Boolean) {
        _tracking.update { it.copy(showStops = on) }
    }

    fun toggleDirection(route: String, directionId: String) {
        val key = route to directionId
        _tracking.update { it.copy(hidden = if (key in it.hidden) it.hidden - key else it.hidden + key) }
        refreshStopTimes()
    }

    /** Turns route-following mode on or off. While on, the screen calls [poll] to keep the followed buses fresh. */
    fun setTracking(on: Boolean) {
        _tracking.update { it.copy(enabled = on, message = null) }
        if (on && _tracking.value.routes.isNotEmpty()) viewModelScope.launch { refreshTrackedBuses() }
        refreshStopTimes(force = on) // labels appear with follow mode, and go with it
    }

    /** One refresh of whatever is live on the map; the screen calls it on a timer while it is showing. */
    fun poll() {
        viewModelScope.launch {
            if (_tracking.value.enabled && _tracking.value.routes.isNotEmpty()) refreshTrackedBuses()
            if (_showBuses.value) lastViewport?.let { refreshVehicles(it) }
            refreshStopTimes()
        }
    }

    /** The refresh button: everything live on the map, now, in whichever mode it is in. */
    fun refreshNow() {
        refresh() // the stops (and nearby buses) in view
        val stopsLoad = loadJob
        viewModelScope.launch {
            refreshesRunning.update { it + 1 }
            try {
                if (_tracking.value.enabled && _tracking.value.routes.isNotEmpty()) refreshTrackedBuses()
                refreshStopTimes(force = true)
                stopsLoad?.join()
            } finally {
                refreshesRunning.update { it - 1 }
            }
        }
    }

    fun addRoute(input: String) {
        val name = input.trim().uppercase()
        val current = _tracking.value
        when {
            name.isEmpty() -> return
            current.routes.any { it.name == name } -> _tracking.update { it.copy(message = "$name is already shown") }
            current.routes.size >= MAX_TRACKED_ROUTES -> _tracking.update { it.copy(message = "Up to $MAX_TRACKED_ROUTES routes at once") }
            else -> viewModelScope.launch {
                _tracking.update { it.copy(busy = true, message = null) }
                runCatching { apiClient.vehiclesForRoute(name) }.fold(
                    onSuccess = { vehicles ->
                        val (shapes, stops) = fetchPaths(name)
                        _tracking.update { state ->
                            state.copy(
                                enabled = true, // adding a route means you want to see it
                                routes = state.routes + TrackedRoute(name, nextColourIndex(state.routes.map { it.colourIndex })),
                                vehicles = state.vehicles + (name to trackedBearings.withBearings(vehicles)),
                                shapes = state.shapes + (name to shapes),
                                stops = state.stops + (name to stops),
                                busy = false,
                            )
                        }
                        refreshStopTimes()
                    },
                    onFailure = { error ->
                        val notFound = (error as? TransitApiException)?.statusCode == 404
                        _tracking.update {
                            it.copy(busy = false, message = if (notFound) "No route called $name" else error.message ?: "Couldn't add $name")
                        }
                    },
                )
            }
        }
    }

    fun removeRoute(name: String) {
        _tracking.update {
            it.copy(
                routes = it.routes.filterNot { route -> route.name == name },
                vehicles = it.vehicles - name,
                shapes = it.shapes - name,
                stops = it.stops - name,
                hidden = it.hidden.filterNot { (route, _) -> route == name }.toSet(),
                message = null,
            )
        }
        routeArrivals.remove(name)
        routeFetchedAt.remove(name)
        refreshStopTimes()
    }

    private suspend fun refreshTrackedBuses() {
        val fresh = _tracking.value.routes.mapNotNull { route ->
            runCatching { apiClient.vehiclesForRoute(route.name) }.getOrNull()?.let { route.name to trackedBearings.withBearings(it) }
        }.toMap()
        // A route whose refresh failed keeps its last known buses.
        _tracking.update { it.copy(vehicles = it.vehicles + fresh.filterKeys { name -> it.routes.any { route -> route.name == name } }) }
    }

    fun onViewportChanged(viewport: Viewport) {
        lastViewport = viewport
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Panning fires continuously; wait for it to settle before querying.
            delay(PAN_DEBOUNCE_MS)
            load(viewport)
        }
    }

    fun toggleBuses() {
        _showBuses.value = !_showBuses.value
        if (!_showBuses.value) {
            _uiState.value = _uiState.value.copy(vehicles = emptyList())
        } else {
            refresh()
        }
    }

    private suspend fun refreshVehicles(viewport: Viewport) {
        runCatching { apiClient.vehiclesInBox(viewport.minLat, viewport.minLon, viewport.maxLat, viewport.maxLon) }
            // Switched off while this was in flight: the reply must not put the buses back.
            .onSuccess { vehicles -> if (_showBuses.value) _uiState.value = _uiState.value.copy(vehicles = browseBearings.withBearings(vehicles)) }
    }

    fun refresh() {
        val viewport = lastViewport ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(viewport) }
    }

    private suspend fun load(viewport: Viewport) {
        _uiState.value = _uiState.value.copy(loading = true, message = null)

        val stops = runCatching {
            apiClient.nearbyStops(
                lat = viewport.centerLat,
                lon = viewport.centerLon,
                radiusMeters = viewport.radiusMeters.coerceAtMost(MAX_RADIUS_METERS),
                limit = 200,
            )
        }

        val vehicles = if (_showBuses.value) {
            runCatching {
                apiClient.vehiclesInBox(viewport.minLat, viewport.minLon, viewport.maxLat, viewport.maxLon)
            }
        } else {
            Result.success(emptyList())
        }

        // runCatching also catches the cancellation of a superseded load, which must not show as an error.
        currentCoroutineContext().ensureActive()
        _uiState.value = MapUiState(
            stops = stops.getOrDefault(_uiState.value.stops),
            // Switched off while the request was in flight: don't put the buses back.
            vehicles = if (_showBuses.value) browseBearings.withBearings(vehicles.getOrDefault(emptyList())) else emptyList(),
            loading = false,
            message = stops.exceptionOrNull()?.message ?: vehicles.exceptionOrNull()?.message,
        )
    }
}
