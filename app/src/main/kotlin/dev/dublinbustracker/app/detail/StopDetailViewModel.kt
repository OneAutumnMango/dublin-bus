package dev.dublinbustracker.app.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.data.ArrivalsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArrivalsUiState {
    data object Loading : ArrivalsUiState
    data class Loaded(val arrivals: List<Arrival>) : ArrivalsUiState
    data class Error(val message: String) : ArrivalsUiState
}

class StopDetailViewModel(private val stopId: String, private val arrivalsRepository: ArrivalsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<ArrivalsUiState>(ArrivalsUiState.Loading)
    val uiState: StateFlow<ArrivalsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = ArrivalsUiState.Loading
            _uiState.value = try {
                ArrivalsUiState.Loaded(arrivalsRepository.arrivalsForStop(stopId))
            } catch (e: Exception) {
                ArrivalsUiState.Error(e.message ?: "Failed to load arrivals")
            }
        }
    }
}
