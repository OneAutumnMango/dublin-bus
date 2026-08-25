package dev.dublinbustracker.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Loaded(val stops: List<Stop>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

private const val DEBOUNCE_MS = 300L

class SearchViewModel(private val apiClient: TransitApiClient) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            // Let typing settle so each keystroke doesn't become a request.
            delay(DEBOUNCE_MS)
            _uiState.value = SearchUiState.Searching
            _uiState.value = runCatching { apiClient.searchStops(newQuery) }.fold(
                onSuccess = { SearchUiState.Loaded(it) },
                onFailure = { SearchUiState.Error(it.message ?: "Search failed") },
            )
        }
    }

    fun retry() = onQueryChanged(_query.value)
}
