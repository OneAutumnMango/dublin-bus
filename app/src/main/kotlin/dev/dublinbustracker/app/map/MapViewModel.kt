package dev.dublinbustracker.app.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.LatLon
import dev.dublinbustracker.core.MAX_TRACKED_ROUTES
import dev.dublinbustracker.core.Route
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.nextColourIndex
import dev.dublinbustracker.core.rankRouteSuggestions
import dev.dublinbustracker.core.VehiclePosition
import dev.dublinbustracker.data.TransitApiClient
import dev.dublinbustracker.data.TransitApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** Where each direction of each route ends up, by route then direction id: a label for a direction with no bus right now. */
    val destinations: Map<String, Map<String, String>> = emptyMap(),
    /** (route, direction) pairs switched off in the Directions dropdown. */
    val hidden: Set<Pair<String, String>> = emptySet(),
    val message: String? = null,
    val busy: Boolean = false,
)

private const val TRACK_REFRESH_MS = 30_000L
private const val SUGGEST_DEBOUNCE_MS = 250L
private const val PAN_DEBOUNCE_MS = 400L
private const val MAX_RADIUS_METERS = 5_000

class MapViewModel(private val apiClient: TransitApiClient) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _showBuses = MutableStateFlow(false)
    val showBuses: StateFlow<Boolean> = _showBuses.asStateFlow()

    private var loadJob: Job? = null
    private var lastViewport: Viewport? = null

    private val _tracking = MutableStateFlow(TrackingUiState())
    val tracking: StateFlow<TrackingUiState> = _tracking.asStateFlow()
    private var trackingJob: Job? = null

    private val _suggestions = MutableStateFlow<List<Route>>(emptyList())
    /** Routes matching what's been typed in the add-a-route box. */
    val suggestions: StateFlow<List<Route>> = _suggestions.asStateFlow()
    private var suggestJob: Job? = null

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

    fun toggleDirection(route: String, directionId: String) {
        val key = route to directionId
        _tracking.update { it.copy(hidden = if (key in it.hidden) it.hidden - key else it.hidden + key) }
    }

    /** Turns route-following mode on or off. While on, the followed routes' buses refresh every 30s. */
    fun setTracking(on: Boolean) {
        _tracking.update { it.copy(enabled = on, message = null) }
        trackingJob?.cancel()
        if (on) {
            trackingJob = viewModelScope.launch {
                while (true) {
                    if (_tracking.value.routes.isNotEmpty()) refreshTrackedBuses()
                    delay(TRACK_REFRESH_MS)
                }
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
                        // A route's path is a nicety: if it can't be fetched the buses still show.
                        val shapes = listOf("0", "1").mapNotNull { direction ->
                            runCatching { apiClient.routeShape(name, direction) }.getOrNull()?.let { direction to it }
                        }.toMap()
                        // Likewise the end of each direction's stops, which names a direction that has no bus yet.
                        val destinations = listOf("0", "1").mapNotNull { direction ->
                            runCatching { apiClient.routeStops(name, direction) }.getOrNull()?.lastOrNull()?.let { direction to it.name }
                        }.toMap()
                        _tracking.update { state ->
                            state.copy(
                                routes = state.routes + TrackedRoute(name, nextColourIndex(state.routes.map { it.colourIndex })),
                                vehicles = state.vehicles + (name to vehicles),
                                shapes = state.shapes + (name to shapes),
                                destinations = state.destinations + (name to destinations),
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
                destinations = it.destinations - name,
                hidden = it.hidden.filterNot { (route, _) -> route == name }.toSet(),
                message = null,
            )
        }
    }

    private suspend fun refreshTrackedBuses() {
        val fresh = _tracking.value.routes.mapNotNull { route ->
            runCatching { apiClient.vehiclesForRoute(route.name) }.getOrNull()?.let { route.name to it }
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
            lastViewport?.let { viewport -> viewModelScope.launch { load(viewport) } }
        }
    }

    fun refresh() {
        lastViewport?.let { viewport -> viewModelScope.launch { load(viewport) } }
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

        _uiState.value = MapUiState(
            stops = stops.getOrDefault(_uiState.value.stops),
            vehicles = vehicles.getOrDefault(emptyList()),
            loading = false,
            message = stops.exceptionOrNull()?.message ?: vehicles.exceptionOrNull()?.message,
        )
    }
}
