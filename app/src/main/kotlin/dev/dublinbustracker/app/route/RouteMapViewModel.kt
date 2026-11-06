package dev.dublinbustracker.app.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.LatLon
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.TripInfo
import dev.dublinbustracker.core.VehiclePosition
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The route itself, which doesn't change while the screen is open. */
data class RouteMapData(val trip: TripInfo, val shape: List<LatLon>, val stops: List<Stop>)

data class RouteMapUiState(
    val data: RouteMapData? = null,
    val vehicles: List<VehiclePosition> = emptyList(),
    val error: String? = null,
)

/** The server only refreshes bus positions about once a minute, so polling faster gains nothing. */
const val VEHICLE_REFRESH_INTERVAL_MS = 30_000L

class RouteMapViewModel(
    private val tripId: String,
    private val apiClient: TransitApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RouteMapUiState())
    val uiState: StateFlow<RouteMapUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RouteMapUiState()
            runCatching {
                val trip = apiClient.trip(tripId)
                coroutineScope {
                    val shape = async { apiClient.tripShape(tripId) }
                    val stops = async { apiClient.routeStops(trip.routeId, trip.directionId) }
                    val vehicles = async { apiClient.vehiclesForRoute(trip.routeId, trip.directionId) }
                    RouteMapData(trip, shape.await(), stops.await()) to vehicles.await()
                }
            }.onSuccess { (data, vehicles) ->
                _uiState.value = RouteMapUiState(data = data, vehicles = vehicles)
            }.onFailure {
                _uiState.value = RouteMapUiState(error = it.message ?: "Failed to load the route")
            }
        }
    }

    /** One refresh of the buses on this trip's route; the screen calls it on a timer while it is showing. */
    fun refreshVehicles() {
        val trip = _uiState.value.data?.trip ?: return
        viewModelScope.launch {
            // A failed refresh just keeps the last positions on screen rather than blanking the map.
            runCatching { apiClient.vehiclesForRoute(trip.routeId, trip.directionId) }
                .onSuccess { vehicles -> _uiState.value = _uiState.value.copy(vehicles = vehicles) }
        }
    }
}
