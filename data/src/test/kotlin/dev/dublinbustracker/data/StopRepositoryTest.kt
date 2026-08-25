package dev.dublinbustracker.data

import kotlin.test.Test
import kotlin.test.assertEquals

class StopRepositoryTest {
    private val json = """
        {"stops":[
          {"id":"s1","code":"1","name":"Trinity College","lat":53.3441,"lon":-6.2578},
          {"id":"s2","code":"747","name":"Heuston Station","lat":53.3468,"lon":-6.2951}
        ],"routes":{},"trips":{}}
    """.trimIndent()
    private val repository = StopRepository(GtfsStaticData.parse(json))

    @Test
    fun `search matches by name`() {
        assertEquals(listOf("Trinity College"), repository.search("trinity").map { it.name })
    }

    @Test
    fun `search matches by code`() {
        assertEquals(listOf("Heuston Station"), repository.search("747").map { it.name })
    }

    @Test
    fun `nearest orders by distance`() {
        val nearest = repository.nearest(53.3441, -6.2578, limit = 1)
        assertEquals("Trinity College", nearest.single().name)
    }
}
