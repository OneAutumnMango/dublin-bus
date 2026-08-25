package dev.dublinbustracker.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dublinbustracker.data.HttpHeader
import dev.dublinbustracker.data.ServerSettings
import dev.dublinbustracker.data.ServerSettingsRepository
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Testing : ConnectionTest
    data class Success(val summary: String) : ConnectionTest
    data class Failure(val message: String) : ConnectionTest
}

data class SettingsUiState(
    val baseUrl: String = "",
    val headers: List<HttpHeader> = emptyList(),
    val problems: List<String> = emptyList(),
    val savedMessage: String? = null,
    val connectionTest: ConnectionTest = ConnectionTest.Idle,
)

class SettingsViewModel(
    private val settingsRepository: ServerSettingsRepository,
    private val apiClient: TransitApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        settingsRepository.current().let { SettingsUiState(baseUrl = it.baseUrl, headers = it.headers) },
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onBaseUrlChanged(value: String) = edit { it.copy(baseUrl = value) }

    fun onHeaderNameChanged(index: Int, name: String) = editHeader(index) { it.copy(name = name) }

    fun onHeaderValueChanged(index: Int, value: String) = editHeader(index) { it.copy(value = value) }

    fun addHeader() = edit { it.copy(headers = it.headers + HttpHeader("", "")) }

    fun removeHeader(index: Int) = edit {
        it.copy(headers = it.headers.filterIndexed { position, _ -> position != index })
    }

    fun save(): Boolean {
        val candidate = currentSettings()
        val problems = candidate.validate()
        if (problems.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(problems = problems, savedMessage = null)
            return false
        }
        settingsRepository.update(candidate)
        _uiState.value = _uiState.value.copy(
            baseUrl = settingsRepository.current().baseUrl,
            problems = emptyList(),
            savedMessage = "Saved",
        )
        return true
    }

    /** Saves first so the probe uses exactly what the user typed. */
    fun testConnection() {
        if (!save()) return
        _uiState.value = _uiState.value.copy(connectionTest = ConnectionTest.Testing)
        viewModelScope.launch {
            val result = runCatching { apiClient.status() }
            _uiState.value = _uiState.value.copy(
                connectionTest = result.fold(
                    onSuccess = { status ->
                        ConnectionTest.Success(
                            "Connected. ${status.stopCount} stops, ${status.routeCount} routes, " +
                                "feeds: ${status.configuredFeeds.joinToString().ifEmpty { "none" }}",
                        )
                    },
                    onFailure = { ConnectionTest.Failure(it.message ?: "Connection failed") },
                ),
            )
        }
    }

    private fun currentSettings() = ServerSettings(
        baseUrl = _uiState.value.baseUrl.trim(),
        headers = _uiState.value.headers,
    )

    private fun editHeader(index: Int, transform: (HttpHeader) -> HttpHeader) = edit { state ->
        state.copy(headers = state.headers.mapIndexed { position, header ->
            if (position == index) transform(header) else header
        })
    }

    private fun edit(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.value = transform(_uiState.value).copy(
            savedMessage = null,
            connectionTest = ConnectionTest.Idle,
        )
    }
}
