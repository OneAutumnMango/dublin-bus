package dev.dublinbustracker.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the active server settings. Saved settings win over the build-time defaults, so a user
 * can repoint the app at a different server without rebuilding it.
 */
class ServerSettingsRepository(
    private val storage: ServerSettingsStorage,
    defaults: ServerSettings = ServerSettings.EMPTY,
) {
    private val _settings = MutableStateFlow(storage.loadRaw()?.let(ServerSettings::fromJson) ?: defaults)
    val settings: StateFlow<ServerSettings> = _settings.asStateFlow()

    fun current(): ServerSettings = _settings.value

    fun update(settings: ServerSettings) {
        val normalised = settings.copy(baseUrl = ServerSettings.normaliseBaseUrl(settings.baseUrl))
        _settings.value = normalised
        storage.saveRaw(normalised.toJson())
    }
}
