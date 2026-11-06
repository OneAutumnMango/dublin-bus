package dev.dublinbustracker.data

import dev.dublinbustracker.core.Stop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** The stops last opened from search, newest first, kept so the search screen can offer them again. */
class RecentStopsRepository(private val storage: FavouritesStorage, private val limit: Int = DEFAULT_LIMIT) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<FavouriteStop>> = _recent.asStateFlow()

    /** Puts [stop] at the front, moving it up if it was already there. */
    fun add(stop: Stop) = publish((listOf(FavouriteStop.of(stop)) + _recent.value.filterNot { it.id == stop.id }).take(limit))

    fun clear() = publish(emptyList())

    private fun publish(updated: List<FavouriteStop>) {
        _recent.value = updated
        storage.saveRaw(json.encodeToString(updated))
    }

    private fun load(): List<FavouriteStop> {
        val raw = storage.loadRaw() ?: return emptyList()
        return runCatching { json.decodeFromString<List<FavouriteStop>>(raw) }.getOrDefault(emptyList()).take(limit)
    }

    private companion object {
        const val DEFAULT_LIMIT = 10
    }
}
