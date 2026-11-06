package dev.dublinbustracker.app.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    /** The buses being shown; empty means every bus. */
    private val _routeFilter = MutableStateFlow<Set<String>>(emptySet())
    val routeFilter: StateFlow<Set<String>> = _routeFilter.asStateFlow()

    init {
        if (initial.routes.isEmpty()) {
            viewModelScope.launch { runCatching { apiClient.stop(stopId) }.onSuccess { _stop.value = it } }
        }
        refresh()
    }

    /** Adds [route] to the buses shown, or takes it off if it already is. */
    fun toggleRoute(route: String) = setRoutes(_routeFilter.value.let { if (route in it) it - route else it + route })

    fun showAllRoutes() = setRoutes(emptySet())

    private fun setRoutes(routes: Set<String>) {
        _routeFilter.value = routes
        _uiState.value = ArrivalsUiState.Loading
        refresh()
    }

    private var loadJob: Job? = null

    /** Only the newest request may answer: an older, slower reply must not overwrite what the user asked for since. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    /** A pull-to-refresh, which shows its own spinner for as long as the reload takes. */
    fun pullToRefresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _pulling.value = true
            try {
                load()
            } finally {
                _pulling.value = false
            }
        }
    }

    private suspend fun load() {
        // Keep showing the current list while a refresh is in flight, rather than flashing a spinner.
        if (_uiState.value !is ArrivalsUiState.Loaded) _uiState.value = ArrivalsUiState.Loading
        val routes = _routeFilter.value
        val result = runCatching {
            if (routes.isEmpty()) {
                apiClient.arrivals(stopId, null)
            } else {
                // The server filters on one route at a time, so ask for each chosen one and merge by when they are due.
                coroutineScope { routes.map { route -> async { apiClient.arrivals(stopId, route) } }.awaitAll() }
                    .flatten()
                    .sortedBy { it.expectedEpochSeconds }
            }
        }
        // runCatching also catches the cancellation of a superseded request, which must not show as an error.
        currentCoroutineContext().ensureActive()
        _uiState.value = result.fold(
            onSuccess = { ArrivalsUiState.Loaded(it) },
            onFailure = { ArrivalsUiState.Error(it.message ?: "Failed to load arrivals") },
        )
    }
}
