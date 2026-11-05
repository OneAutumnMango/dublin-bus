package dev.dublinbustracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineAreaTest {
    private val dublin = AREA_PRESETS.first { it.name == "Dublin" }.bounds

    @Test
    fun `the Dublin preset needs about as many tiles as the real download did`() {
        // The actual pack downloaded for these bounds came to 687 tiles.
        assertTrue(tileCount(dublin) in 620..760, "was ${tileCount(dublin)}")
    }

    @Test
    fun `the Dublin estimate lands near the real 25MB`() {
        assertTrue(estimatedMegabytes(tileCount(dublin)) in 22..32)
    }

    @Test
    fun `a bigger area needs more tiles and a bigger download`() {
        val wider = dublin.copy(west = -7.0, east = -5.5)

        assertTrue(tileCount(wider) > tileCount(dublin))
    }

    @Test
    fun `fewer zoom levels need fewer tiles`() {
        assertTrue(tileCount(dublin, maxZoom = 12) < tileCount(dublin, maxZoom = 14))
    }

    @Test
    fun `the whole world at zoom 0 is a single tile`() {
        assertEquals(1L, tileCount(GeoBounds(-80.0, -179.0, 80.0, 179.0), maxZoom = 0))
    }

    @Test
    fun `every preset is a sensible size, and a whole county is too much`() {
        AREA_PRESETS.forEach { assertTrue(tileCount(it.bounds) < MAX_AREA_TILES, "${it.name} was ${tileCount(it.bounds)}") }
        assertTrue(tileCount(GeoBounds(52.0, -7.5, 54.0, -5.5)) > MAX_AREA_TILES)
    }

    @Test
    fun `sizes round up`() {
        assertEquals(1, estimatedMegabytes(1))
        assertEquals(0, estimatedMegabytes(0))
    }
}
