package dev.dublinbustracker.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

data class AreaPreset(val name: String, val bounds: GeoBounds)

/** The map's vector tiles stop at this zoom; anything closer in is the same data stretched. */
const val MAP_SOURCE_MAX_ZOOM = 14

/** Measured: the 687-tile Dublin download came to 25.4MB. Rural tiles are smaller, so this errs high. */
private const val AVERAGE_TILE_BYTES = 37_000L

/** Roughly 220MB - past this an area is more likely a mis-drag than something wanted. */
const val MAX_AREA_TILES = 6_000L

/** The parts of Ireland the bus feeds cover, as a quick way in. */
val AREA_PRESETS = listOf(
    AreaPreset("Dublin", GeoBounds(53.20, -6.50, 53.47, -6.00)),
    AreaPreset("Cork", GeoBounds(51.82, -8.62, 51.95, -8.35)),
    AreaPreset("Galway", GeoBounds(53.24, -9.15, 53.32, -8.95)),
    AreaPreset("Limerick", GeoBounds(52.62, -8.72, 52.70, -8.55)),
    AreaPreset("Waterford", GeoBounds(52.23, -7.18, 52.29, -7.05)),
)

/** How many tiles covering [bounds] a download down to [maxZoom] needs (standard web-mercator tiling). */
fun tileCount(bounds: GeoBounds, maxZoom: Int = MAP_SOURCE_MAX_ZOOM): Long =
    (0..maxZoom).sumOf { zoom ->
        val n = 1 shl zoom
        val columns = tileX(bounds.east, n) - tileX(bounds.west, n) + 1L
        val rows = tileY(bounds.south, n) - tileY(bounds.north, n) + 1L
        columns * rows
    }

fun estimatedMegabytes(tiles: Long): Int = ((tiles * AVERAGE_TILE_BYTES + 999_999) / 1_000_000).toInt()

private fun tileX(longitude: Double, n: Int): Int = floor((longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

private fun tileY(latitude: Double, n: Int): Int {
    val radians = Math.toRadians(latitude.coerceIn(-85.0511, 85.0511))
    return floor((1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
}
