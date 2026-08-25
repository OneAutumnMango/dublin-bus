package dev.dublinbustracker.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StopSearchTest {
    private val trinity = Stop("8220DB000001", "1", "Trinity College", 53.3441, -6.2578)
    private val heuston = Stop("8220DB000002", "747", "Heuston Station", 53.3468, -6.2951)
    private val stops = listOf(trinity, heuston)

    @Test
    fun `matches by name, case-insensitive`() {
        assertEquals(listOf(trinity), StopSearch.search(stops, "trinity"))
    }

    @Test
    fun `matches by code prefix`() {
        assertEquals(listOf(heuston), StopSearch.search(stops, "74"))
    }

    @Test
    fun `blank query returns nothing`() {
        assertEquals(emptyList(), StopSearch.search(stops, "  "))
    }

    @Test
    fun `nearest stops sorted by distance`() {
        val result = StopSearch.nearestStops(53.3441, -6.2578, stops, limit = 1)
        assertEquals(listOf(trinity), result)
    }
}
