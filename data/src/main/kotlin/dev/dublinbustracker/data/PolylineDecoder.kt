package dev.dublinbustracker.data

import dev.dublinbustracker.core.LatLon

/** Decodes the server's Google-style encoded polyline (1e5 precision) into map points. */
fun decodePolyline(encoded: String): List<LatLon> {
    val points = mutableListOf<LatLon>()
    var index = 0
    var lat = 0
    var lon = 0

    fun nextDelta(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f) shl shift)
            shift += 5
            if (chunk < 0x20) break
        }
        return if (result and 1 != 0) (result shr 1).inv() else result shr 1
    }

    while (index < encoded.length) {
        lat += nextDelta()
        lon += nextDelta()
        points += LatLon(lat / 1e5, lon / 1e5)
    }
    return points
}
