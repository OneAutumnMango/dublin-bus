package dev.dublinbustracker.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A route in a profile, with the colour slot it was drawn in so reloading looks just as it did. */
@Serializable
data class ProfileRoute(val name: String, val colourIndex: Int)

/** A direction of a route that was switched off. */
@Serializable
data class HiddenDirection(val route: String, val direction: String)

/** A named selection of routes to follow on the map, with the directions that were switched off. */
@Serializable
data class RouteProfile(
    val name: String,
    val routes: List<ProfileRoute>,
    val hidden: List<HiddenDirection> = emptyList(),
)

/** The user's saved route selections, kept in the order they were first saved. */
class RouteProfilesRepository(private val storage: FavouritesStorage) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<RouteProfile>> = _profiles.asStateFlow()

    /** Saves [profile] under its name, replacing one already called that (ignoring case). Returns false for a blank name or no routes. */
    fun save(profile: RouteProfile): Boolean {
        val named = profile.copy(name = profile.name.trim())
        if (named.name.isEmpty() || named.routes.isEmpty()) return false
        val current = _profiles.value
        val updated = if (current.any { it.name.equals(named.name, ignoreCase = true) }) {
            current.map { if (it.name.equals(named.name, ignoreCase = true)) named else it }
        } else {
            current + named
        }
        publish(updated)
        return true
    }

    fun delete(name: String) = publish(_profiles.value.filterNot { it.name == name })

    private fun publish(updated: List<RouteProfile>) {
        _profiles.value = updated
        storage.saveRaw(json.encodeToString(updated))
    }

    private fun load(): List<RouteProfile> {
        val raw = storage.loadRaw() ?: return emptyList()
        return runCatching { json.decodeFromString<List<RouteProfile>>(raw) }.getOrDefault(emptyList())
    }
}
