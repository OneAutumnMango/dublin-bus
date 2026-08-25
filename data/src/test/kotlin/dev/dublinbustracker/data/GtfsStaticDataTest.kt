package dev.dublinbustracker.data

import kotlin.test.Test
import kotlin.test.assertEquals

class GtfsStaticDataTest {
    private val json = """
        {"stops":[{"id":"s1","code":"1","name":"Trinity College","lat":53.34,"lon":-6.25}],
         "routes":{"r1":"15"},
         "trips":{"t1":{"routeId":"r1","headsign":"Clongriffin"}}}
    """.trimIndent()

    @Test
    fun `parses stops`() {
        val data = GtfsStaticData.parse(json)
        assertEquals("Trinity College", data.allStops().single().name)
    }

    @Test
    fun `resolves trip to route short name and headsign`() {
        val data = GtfsStaticData.parse(json)
        assertEquals(TripInfo("15", "Clongriffin"), data.tripInfo("t1"))
    }

    @Test
    fun `unknown trip resolves to null`() {
        val data = GtfsStaticData.parse(json)
        assertEquals(null, data.tripInfo("missing"))
    }
}
