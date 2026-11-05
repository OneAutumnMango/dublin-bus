package dev.dublinbustracker.app.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArrivalsUiState {
    data object Loading : ArrivalsUiState
    data class Loaded(val arrivals: List<Arrival>) : ArrivalsUiState
    data class Error(val message: String) : ArrivalsUiState
}

/** The server refreshes upstream at most every 30-60s, so polling faster than this gains nothing. */
const val ARRIVALS_REFRESH_INTERVAL_MS = 30_000L

class StopDetailViewModel(
    initial: Stop,
    private val apiClient: TransitApiClient,
) : ViewModel() {
    private val stopId = initial.id

    /** A favourite is saved without its route list, so the full stop is fetched when that's missing. */
    private val _stop = MutableStateFlow(initial)
    val stop: StateFlow<Stop> = _stop.asStateFlow()

    private val _pulling = MutableStateFlow(false)
    val pulling: StateFlow<Boolean> = _pulling.asStateFlow()

    private val _uiState = MutableStateFlow<ArrivalsUiState>(ArrivalsUiState.Loading)
    val uiState: StateFlow<ArrivalsUiState> = _uiState.asStateFlow()

    private val _routeFilter = MutableStateFlow<String?>(null)
    val routeFilter: StateFlow<String?> = _routeFilter.asStateFlow()

    init {
        if (initial.routes.isEmpty()) {
            viewModelScope.launch { runCatching { apiClient.stop(stopId) }.onSuccess { _stop.value = it } }
        }
        refresh()
    }

    fun setRouteFilter(route: String?) {
        _routeFilter.value = route
        _uiState.value = ArrivalsUiState.Loading
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** A pull-to-refresh, which shows its own spinner for as long as the reload takes. */
    fun pullToRefresh() {
        viewModelScope.launch {
            _pulling.value = true
            load()
            _pulling.value = false
        }
    }

    private suspend fun load() {
        // Keep showing the current list while a refresh is in flight, rather than flashing a spinner.
        if (_uiState.value !is ArrivalsUiState.Loaded) _uiState.value = ArrivalsUiState.Loading
        _uiState.value = runCatching { apiClient.arrivals(stopId, _routeFilter.value) }.fold(
            onSuccess = { ArrivalsUiState.Loaded(it) },
            onFailure = { ArrivalsUiState.Error(it.message ?: "Failed to load arrivals") },
        )
    }
}
