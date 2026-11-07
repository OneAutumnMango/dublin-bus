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

/**
 * One line naming the routes, such as "4 (NW), 11": a route with both directions on is just its number, and one with only
 * one on says which way it heads ([headingOf] gives a compass point for a route and direction id, or null if not known yet).
 */
fun RouteProfile.summary(headingOf: (route: String, direction: String) -> String?): String =
    routes.joinToString(", ") { route ->
        val off = hidden.filter { it.route == route.name }.map { it.direction }.toSet()
        val on = listOf("0", "1").filterNot { it in off }
        when (on.size) {
            2 -> route.name
            0 -> "${route.name} (off)"
            else -> "${route.name} (${headingOf(route.name, on.single()) ?: "direction ${on.single()}"})"
        }
    }

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

    /**
     * Gives the profile called [name] a new name, keeping its place in the list. Returns false for a blank name, an unknown
     * profile, or a name another profile already has (changing only the capitals of its own name is fine).
     */
    fun rename(name: String, newName: String): Boolean {
        val trimmed = newName.trim()
        val current = _profiles.value
        if (trimmed.isEmpty() || current.none { it.name == name }) return false
        if (current.any { it.name != name && it.name.equals(trimmed, ignoreCase = true) }) return false
        publish(current.map { if (it.name == name) it.copy(name = trimmed) else it })
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
