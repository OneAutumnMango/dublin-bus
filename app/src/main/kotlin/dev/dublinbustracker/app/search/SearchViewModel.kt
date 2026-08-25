package dev.dublinbustracker.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.StopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(private val stopRepository: StopRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Stop>>(emptyList())
    val results: StateFlow<List<Stop>> = _results.asStateFlow()

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        viewModelScope.launch {
            _results.value = withContext(Dispatchers.Default) { stopRepository.search(newQuery) }
        }
    }
}
