package dev.dublinbustracker.app.map

import android.content.Context
import dev.dublinbustracker.core.GeoBounds
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

data class DownloadedArea(
    val id: Long,
    val name: String,
    val bytes: Long,
    val completed: Long,
    val required: Long,
    val complete: Boolean,
) {
    val percent: Int get() = if (required <= 0) 0 else (completed * 100 / required).toInt().coerceIn(0, 100)
}

/** The map areas stored on the device for offline use. */
object OfflineMaps {
    /** Tiles only exist to this zoom, so there is nothing to gain from asking for more. */
    private const val MAX_ZOOM = 14.0

    suspend fun list(context: Context): List<DownloadedArea> =
        regions(context).map { region ->
            val status = region.status()
            DownloadedArea(
                id = region.id,
                name = nameOf(region),
                bytes = status?.completedResourceSize ?: 0,
                completed = status?.completedResourceCount ?: 0,
                required = status?.requiredResourceCount ?: 0,
                complete = status?.isComplete ?: false,
            )
        }

    suspend fun regions(context: Context): List<OfflineRegion> = suspendCancellableCoroutine { continuation ->
        OfflineManager.getInstance(context).listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) = continuation.resume(offlineRegions?.toList().orEmpty())
                override fun onError(error: String) = continuation.resume(emptyList())
            },
        )
    }

    /** Starts downloading [bounds]; returns once it's registered, while the tiles arrive in the background. */
    suspend fun download(context: Context, name: String, bounds: GeoBounds) {
        val area = LatLngBounds.Builder()
            .include(LatLng(bounds.south, bounds.west))
            .include(LatLng(bounds.north, bounds.east))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(MAP_STYLE_URL, area, 0.0, MAX_ZOOM, context.resources.displayMetrics.density)
        suspendCancellableCoroutine { continuation ->
            OfflineManager.getInstance(context).createOfflineRegion(
                definition,
                JSONObject().put("name", name).toString().toByteArray(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                        continuation.resume(Unit)
                    }

                    override fun onError(error: String) = continuation.resume(Unit)
                },
            )
        }
    }

    suspend fun delete(context: Context, id: Long) {
        val region = regions(context).firstOrNull { it.id == id } ?: return
        suspendCancellableCoroutine { continuation ->
            region.delete(
                object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = continuation.resume(Unit)
                    override fun onError(error: String) = continuation.resume(Unit)
                },
            )
        }
    }

    private suspend fun OfflineRegion.status(): OfflineRegionStatus? = suspendCancellableCoroutine { continuation ->
        getStatus(
            object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) = continuation.resume(status)
                override fun onError(error: String?) = continuation.resume(null)
            },
        )
    }

    // The first launch's automatic download stored a bare name rather than JSON.
    private fun nameOf(region: OfflineRegion): String {
        val raw = String(region.metadata)
        return runCatching { JSONObject(raw).getString("name") }
            .getOrElse { raw.replaceFirstChar { it.uppercase() }.ifBlank { "Map area" } }
    }
}
