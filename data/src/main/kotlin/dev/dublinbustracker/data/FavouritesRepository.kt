package dev.dublinbustracker.data

import dev.dublinbustracker.core.Stop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Stop details are denormalised so favourites and the widget render without a network call. */
@Serializable
data class FavouriteStop(
    val id: String,
    val code: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
) {
    /** True for entries migrated from the old id-only format, which the UI backfills. */
    val needsDetails: Boolean get() = name.isBlank()

    fun toStop() = Stop(id, code, name, lat, lon)
}

interface FavouritesStorage {
    fun loadRaw(): String?
    fun saveRaw(json: String)
}

class FavouritesRepository(private val storage: FavouritesStorage) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _favourites = MutableStateFlow(load())
    val favourites: StateFlow<List<FavouriteStop>> = _favourites.asStateFlow()

    private val _favouriteIds = MutableStateFlow(_favourites.value.map { it.id }.toSet())
    val favouriteIds: StateFlow<Set<String>> = _favouriteIds.asStateFlow()

    fun isFavourite(stopId: String): Boolean = _favourites.value.any { it.id == stopId }

    fun toggle(stop: Stop) {
        val current = _favourites.value
        publish(
            if (current.any { it.id == stop.id }) {
                current.filterNot { it.id == stop.id }
            } else {
                current + FavouriteStop(stop.id, stop.code, stop.name, stop.lat, stop.lon)
            },
        )
    }

    fun remove(stopId: String) = publish(_favourites.value.filterNot { it.id == stopId })

    /** Fills in or refreshes a favourite once the server has supplied its details. */
    fun updateDetails(stop: Stop) {
        publish(
            _favourites.value.map {
                if (it.id == stop.id) FavouriteStop(stop.id, stop.code, stop.name, stop.lat, stop.lon) else it
            },
        )
    }

    private fun publish(updated: List<FavouriteStop>) {
        _favourites.value = updated
        _favouriteIds.value = updated.map { it.id }.toSet()
        storage.saveRaw(json.encodeToString(updated))
    }

    private fun load(): List<FavouriteStop> {
        val raw = storage.loadRaw() ?: return emptyList()
        return runCatching { json.decodeFromString<List<FavouriteStop>>(raw) }.getOrDefault(emptyList())
    }
}
