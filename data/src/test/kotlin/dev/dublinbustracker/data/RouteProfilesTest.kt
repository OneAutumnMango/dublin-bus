package dev.dublinbustracker.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteProfilesTest {
    private class FakeStorage(var raw: String? = null) : FavouritesStorage {
        override fun loadRaw(): String? = raw
        override fun saveRaw(json: String) {
            raw = json
        }
    }

    private fun profile(name: String, vararg routes: String, hidden: List<HiddenDirection> = emptyList()) =
        RouteProfile(name, routes.mapIndexed { i, r -> ProfileRoute(r, i) }, hidden)

    @Test
    fun `a saved profile keeps its routes, colours and switched-off directions across a restart`() {
        val storage = FakeStorage()
        RouteProfilesRepository(storage).save(profile("Commute", "4", "S8", hidden = listOf(HiddenDirection("S8", "1"))))

        val loaded = RouteProfilesRepository(storage).profiles.value.single()

        assertEquals("Commute", loaded.name)
        assertEquals(listOf(ProfileRoute("4", 0), ProfileRoute("S8", 1)), loaded.routes)
        assertEquals(listOf(HiddenDirection("S8", "1")), loaded.hidden)
    }

    @Test
    fun `saving under an existing name replaces it in place, ignoring case`() {
        val repository = RouteProfilesRepository(FakeStorage())
        repository.save(profile("Home", "4"))
        repository.save(profile("Work", "15"))

        repository.save(profile("home", "4", "S8"))

        assertEquals(listOf("home", "Work"), repository.profiles.value.map { it.name })
        assertEquals(2, repository.profiles.value.first().routes.size)
    }

    @Test
    fun `a blank name or an empty selection is not saved`() {
        val repository = RouteProfilesRepository(FakeStorage())

        assertFalse(repository.save(profile("  ", "4")))
        assertFalse(repository.save(RouteProfile("Empty", emptyList())))
        assertTrue(repository.profiles.value.isEmpty())
    }

    @Test
    fun `deleting removes it for good, and corrupt storage reads as empty`() {
        val storage = FakeStorage()
        RouteProfilesRepository(storage).apply { save(profile("A", "4")); delete("A") }

        assertEquals(emptyList(), RouteProfilesRepository(storage).profiles.value)
        assertEquals(emptyList(), RouteProfilesRepository(FakeStorage("nonsense")).profiles.value)
    }

    @Test
    fun `the summary names a direction only where just one of a route's two is on`() {
        val headings = mapOf(("4" to "1") to "NW", ("11" to "1") to "NW")
        val both = profile("A", "4", "11")
        val one = profile("B", "4", "11", hidden = listOf(HiddenDirection("4", "0"), HiddenDirection("11", "0")))
        val mixed = profile("C", "4", "11", hidden = listOf(HiddenDirection("4", "1")))
        val none = profile("D", "4", hidden = listOf(HiddenDirection("4", "0"), HiddenDirection("4", "1")))

        assertEquals("4, 11", both.summary { r, d -> headings[r to d] })
        assertEquals("4 (NW), 11 (NW)", one.summary { r, d -> headings[r to d] })
        assertEquals("4 (direction 0), 11", mixed.summary { r, d -> headings[r to d] })
        assertEquals("4 (off)", none.summary { _, _ -> null })
    }
}
