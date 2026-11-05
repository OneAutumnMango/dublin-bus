package dev.dublinbustracker.app.map

import android.content.Context
import dev.dublinbustracker.core.AREA_PRESETS
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion

/**
 * Keeps the map quick to open. Anything viewed is cached on disk (the default cap is small, so
 * it's raised), and on the very first launch Dublin is downloaded so the base map is on screen
 * at once. More areas can be added or removed from Settings.
 */
object MapCache {
    private const val AMBIENT_CACHE_BYTES = 300L * 1024 * 1024
    private const val PREFS = "map"
    private const val DEFAULT_AREA_REQUESTED = "default_area_requested"

    fun init(context: Context) {
        MapLibre.getInstance(context)
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(
            AMBIENT_CACHE_BYTES,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = Unit
                override fun onError(message: String) = Unit
            },
        )

        MainScope().launch {
            val existing = OfflineMaps.regions(context)
            // An interrupted download picks up where it left off; a finished one is a no-op.
            existing.forEach { it.setDownloadState(OfflineRegion.STATE_ACTIVE) }

            // Only ever once - otherwise deleting it in Settings would just bring it back.
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(DEFAULT_AREA_REQUESTED, false)) {
                prefs.edit().putBoolean(DEFAULT_AREA_REQUESTED, true).apply()
                if (existing.isEmpty()) {
                    val dublin = AREA_PRESETS.first { it.name == "Dublin" }
                    OfflineMaps.download(context, dublin.name, dublin.bounds)
                }
            }
        }
    }
}
