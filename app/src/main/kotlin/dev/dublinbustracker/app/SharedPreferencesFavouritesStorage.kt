package dev.dublinbustracker.app

import android.content.Context
import dev.dublinbustracker.data.FavouritesStorage

class SharedPreferencesFavouritesStorage(context: Context) : FavouritesStorage {
    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    override fun load(): Set<String> = prefs.getStringSet(KEY_STOP_IDS, emptySet()).orEmpty()

    override fun save(stopIds: Set<String>) {
        prefs.edit().putStringSet(KEY_STOP_IDS, stopIds).apply()
    }

    private companion object {
        const val KEY_STOP_IDS = "stop_ids"
    }
}
