package dev.dublinbustracker.app.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.VehiclePosition
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

private const val PAN_DEBOUNCE_MS = 400L
private const val MAX_RADIUS_METERS = 5_000

class MapViewModel(private val apiClient: TransitApiClient) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _showBuses = MutableStateFlow(false)
    val showBuses: StateFlow<Boolean> = _showBuses.asStateFlow()

    private var loadJob: Job? = null
    private var lastViewport: Viewport? = null

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
