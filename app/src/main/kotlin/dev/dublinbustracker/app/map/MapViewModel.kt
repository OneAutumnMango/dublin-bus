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
import dev.dublinbustracker.core.stopTimesLabel
import dev.dublinbustracker.core.nextColourIndex
import dev.dublinbustracker.core.rankRouteSuggestions
import dev.dublinbustracker.core.VehiclePosition
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

/** Below this zoom stops are too close together to label. */
private const val STOP_TIMES_MIN_ZOOM = 15.0
private const val STOP_TIMES_MAX_STOPS = 10
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
        shapeCalls.awaitAll().filterNotNull().toMap() to stopCalls.awaitAll().filterNotNull().toMap()
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

    private var timesJob: Job? = null
    private val timesFetchedAt = HashMap<String, Long>()

    fun setShowStopTimes(on: Boolean) {
        _tracking.update { it.copy(showStopTimes = on, stopTimes = if (on) it.stopTimes else emptyMap()) }
        if (on) lastViewport?.let { refreshStopTimes(it, force = true) }
    }

    /**
     * Keeps the next-arrivals labels for the stops in [viewport] up to date: only when following routes, with the
     * option on and zoomed in; only the stops nearest the middle; and a stop only asked again once its times are a minute old.
     */
    private fun refreshStopTimes(viewport: Viewport, force: Boolean = false) {
        val state = _tracking.value
        if (!state.enabled || !state.showStopTimes || state.routes.isEmpty() || viewport.zoom < STOP_TIMES_MIN_ZOOM) {
            timesJob?.cancel()
            if (state.stopTimes.isNotEmpty()) _tracking.update { it.copy(stopTimes = emptyMap()) }
            return
        }
        val centre = LatLon(viewport.centerLat, viewport.centerLon)
        // Each stop in view with the followed routes that call at it.
        val visible = state.routes.flatMap { route ->
            state.stops[route.name].orEmpty()
                .filterKeys { (route.name to it) !in state.hidden }
                .values.flatten()
                .filter { it.lat in viewport.minLat..viewport.maxLat && it.lon in viewport.minLon..viewport.maxLon }
                .map { it to route.name }
        }
            .groupBy({ it.first.id }, { it })
            .values
            .map { entries -> entries.first().first to entries.map { it.second }.distinct() }
            .sortedBy { (stop, _) -> distanceMeters(centre, LatLon(stop.lat, stop.lon)) }
            .take(STOP_TIMES_MAX_STOPS)
        val now = System.currentTimeMillis()
        val due = visible.filter { (stop, _) -> force || now - (timesFetchedAt[stop.id] ?: 0L) > STOP_TIMES_MAX_AGE_MS }
        val visibleIds = visible.map { it.first.id }.toSet()
        // Labels for stops that have scrolled out of view go; the rest stay while new ones load.
        _tracking.update { it.copy(stopTimes = it.stopTimes.filterKeys { id -> id in visibleIds }) }
        if (due.isEmpty()) return

        timesJob?.cancel()
        timesJob = viewModelScope.launch {
            val fetched = coroutineScope {
                due.map { (stop, routes) ->
                    async {
                        val perRoute = routes.map { route -> route to (runCatching { apiClient.arrivals(stop.id, route, limit = 2) }.getOrNull() ?: emptyList()) }
                        stop.id to stopTimesLabel(perRoute, System.currentTimeMillis() / 1000, ZoneId.systemDefault())
                    }
                }.awaitAll()
            }
            // runCatching also catches the cancellation of a superseded request, which must not be taken for an empty answer.
            currentCoroutineContext().ensureActive()
            fetched.forEach { (id, _) -> timesFetchedAt[id] = now }
            _tracking.update { s ->
                s.copy(stopTimes = s.stopTimes + fetched.mapNotNull { (id, label) -> label?.let { id to it } } - fetched.filter { it.second == null }.map { it.first }.toSet())
            }
        }
    }

    fun setShowStops(on: Boolean) {
        _tracking.update { it.copy(showStops = on) }
    }

    fun toggleDirection(route: String, directionId: String) {
        val key = route to directionId
        _tracking.update { it.copy(hidden = if (key in it.hidden) it.hidden - key else it.hidden + key) }
    }

    /** Turns route-following mode on or off. While on, the screen calls [poll] to keep the followed buses fresh. */
    fun setTracking(on: Boolean) {
        _tracking.update { it.copy(enabled = on, message = null) }
        if (on && _tracking.value.routes.isNotEmpty()) viewModelScope.launch { refreshTrackedBuses() }
        lastViewport?.let { refreshStopTimes(it, force = on) } // labels appear with follow mode, and go with it
    }

    /** One refresh of whatever is live on the map; the screen calls it on a timer while it is showing. */
    fun poll() {
        viewModelScope.launch {
            if (_tracking.value.enabled && _tracking.value.routes.isNotEmpty()) refreshTrackedBuses()
            if (_showBuses.value) lastViewport?.let { refreshVehicles(it) }
            lastViewport?.let { refreshStopTimes(it) }
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
                                routes = state.routes + TrackedRoute(name, nextColourIndex(state.routes.map { it.colourIndex })),
                                vehicles = state.vehicles + (name to trackedBearings.withBearings(vehicles)),
                                shapes = state.shapes + (name to shapes),
                                stops = state.stops + (name to stops),
                                busy = false,
                            )
                        }
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
            refreshStopTimes(viewport)
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
