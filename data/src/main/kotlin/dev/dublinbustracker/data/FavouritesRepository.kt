package dev.dublinbustracker.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the set of favourited stop ids; implemented by the app layer (e.g. SharedPreferences). */
interface FavouritesStorage {
    fun load(): Set<String>
    fun save(stopIds: Set<String>)
}

class FavouritesRepository(private val storage: FavouritesStorage) {
    private val _favouriteIds = MutableStateFlow(storage.load())
    val favouriteIds: StateFlow<Set<String>> = _favouriteIds.asStateFlow()

    fun isFavourite(stopId: String): Boolean = _favouriteIds.value.contains(stopId)

    fun toggle(stopId: String) {
        val updated = _favouriteIds.value.let {
            if (stopId in it) it - stopId else it + stopId
        }
        _favouriteIds.value = updated
        storage.save(updated)
    }
}
