package dev.dublinbustracker.app.map

import android.content.Context
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Keeps the map quick to open. Anything viewed is cached on disk (the default cap is small, so
 * it's raised), and Dublin itself is downloaded once up front so the base map is on screen at
 * once - even on first launch, in a new area of the city, or without a signal.
 */
object MapCache {
    private const val AMBIENT_CACHE_BYTES = 300L * 1024 * 1024

    // Dublin and its surrounds, down to zoom 14 (about 600 tiles). Finer detail is cached as it's viewed.
    private val DUBLIN = LatLngBounds.Builder().include(LatLng(53.20, -6.50)).include(LatLng(53.47, -6.00)).build()
    private const val PACK_MAX_ZOOM = 14.0

    fun init(context: Context) {
        MapLibre.getInstance(context)
        val manager = OfflineManager.getInstance(context)
        manager.setMaximumAmbientCacheSize(
            AMBIENT_CACHE_BYTES,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = Unit
                override fun onError(message: String) = Unit
            },
        )
        manager.listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (offlineRegions.isNullOrEmpty()) {
                        createDublinPack(context, manager)
                    } else {
                        // An interrupted download picks up where it left off; a finished one is a no-op.
                        offlineRegions.forEach { it.setDownloadState(OfflineRegion.STATE_ACTIVE) }
                    }
                }

                override fun onError(error: String) = Unit
            },
        )
    }

    private fun createDublinPack(context: Context, manager: OfflineManager) {
        val definition = OfflineTilePyramidRegionDefinition(
            MAP_STYLE_URL,
            DUBLIN,
            0.0,
            PACK_MAX_ZOOM,
            context.resources.displayMetrics.density,
        )
        manager.createOfflineRegion(
            definition,
            "dublin".toByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(
                        object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) = Unit
                            override fun onError(error: OfflineRegionError) = Unit
                            override fun mapboxTileCountLimitExceeded(limit: Long) = Unit
                        },
                    )
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) = Unit
            },
        )
    }
}
