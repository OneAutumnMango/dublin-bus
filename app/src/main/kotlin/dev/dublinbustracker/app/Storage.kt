package dev.dublinbustracker.app

import android.content.Context
import dev.dublinbustracker.data.FavouritesStorage
import dev.dublinbustracker.data.ServerSettingsStorage

class SharedPreferencesFavouritesStorage(context: Context) : FavouritesStorage {
    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    override fun loadRaw(): String? = prefs.getString(KEY_FAVOURITES, null) ?: migrateLegacyIds()

    override fun saveRaw(json: String) {
        prefs.edit().putString(KEY_FAVOURITES, json).remove(LEGACY_KEY_STOP_IDS).apply()
    }

    /** Earlier builds stored bare stop ids; keep them, and let the UI backfill the details. */
    private fun migrateLegacyIds(): String? {
        val legacy = runCatching { prefs.getStringSet(LEGACY_KEY_STOP_IDS, null) }.getOrNull()
        if (legacy.isNullOrEmpty()) return null
        return legacy.joinToString(prefix = "[", postfix = "]") { """{"id":"$it"}""" }
    }

    private companion object {
        const val KEY_FAVOURITES = "favourites_json"
        const val LEGACY_KEY_STOP_IDS = "stop_ids"
    }
}

class SharedPreferencesServerSettingsStorage(context: Context) : ServerSettingsStorage {
    private val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)

    override fun loadRaw(): String? = prefs.getString(KEY_SETTINGS, null)

    override fun saveRaw(json: String) {
        prefs.edit().putString(KEY_SETTINGS, json).apply()
    }

    private companion object {
        const val KEY_SETTINGS = "settings_json"
    }
}

/** One JSON string in its own preferences file, for lists the app keeps that aren't favourites. */
class SharedPreferencesRawStorage(context: Context, file: String) : FavouritesStorage {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    override fun loadRaw(): String? = prefs.getString("json", null)

    override fun saveRaw(json: String) {
        prefs.edit().putString("json", json).apply()
    }
}
