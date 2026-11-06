package dev.dublinbustracker.data

import dev.dublinbustracker.core.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetConfigTest {
    private val here = LatLon(53.3498, -6.2603)
    private fun fav(id: String, lat: Double, lon: Double) = FavouriteStop(id = id, name = id, lat = lat, lon = lon)

    @Test
    fun `a config survives being stored and read back`() {
        val config = WidgetConfig(WidgetMode.NEAREST_FAVOURITE, "s1", setOf("4", "S8"), setOf("s2"))

        assertEquals(config, WidgetConfig.decode(config.encode()))
    }

    @Test
    fun `nothing stored, or something unreadable, is no config`() {
        assertNull(WidgetConfig.decode(null))
        assertNull(WidgetConfig.decode("nonsense"))
    }

    @Test
    fun `the nearest favourite wins, and an excluded one is skipped even when it is nearest`() {
        val near = fav("near", 53.3500, -6.2600)
        val far = fav("far", 53.4000, -6.3000)

        assertEquals("near", nearestFavourite(listOf(far, near), emptySet(), here)?.first?.id)
        assertEquals("far", nearestFavourite(listOf(far, near), setOf("near"), here)?.first?.id)
        assertNull(nearestFavourite(listOf(far, near), setOf("near", "far"), here))
    }

    @Test
    fun `a favourite whose position isn't known yet is never picked`() {
        assertNull(nearestFavourite(listOf(fav("unknown", 0.0, 0.0)), emptySet(), here))
    }

    @Test
    fun `only the chosen buses are kept, ignoring case, and none chosen keeps all`() {
        val buses = listOf("4", "S8", "15A")

        assertEquals(listOf("4", "S8"), keepRoutes(buses, setOf("s8", "4")) { it })
        assertEquals(buses, keepRoutes(buses, emptySet()) { it })
    }

    @Test
    fun `a favourite excluded from widgets is never picked, however near`() {
        val near = fav("near", 53.3500, -6.2600).copy(excludedFromWidgets = true)
        val far = fav("far", 53.4000, -6.3000)

        assertEquals("far", nearestFavourite(listOf(near, far), emptySet(), here)?.first?.id)
        assertNull(nearestFavourite(listOf(near), emptySet(), here))
    }

    @Test
    fun `hidden buses are dropped, ignoring case, and none hidden drops nothing`() {
        val buses = listOf("4", "S8", "15A")

        assertEquals(listOf("4"), dropRoutes(buses, setOf("s8", "15a")) { it })
        assertEquals(buses, dropRoutes(buses, emptySet()) { it })
    }
}
