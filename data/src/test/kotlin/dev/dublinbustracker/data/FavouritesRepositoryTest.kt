package dev.dublinbustracker.data

import dev.dublinbustracker.core.RouteHeading
import dev.dublinbustracker.core.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavouritesRepositoryTest {
    private class FakeStorage(var raw: String? = null) : FavouritesStorage {
        var saves = 0
        override fun loadRaw(): String? = raw
        override fun saveRaw(json: String) {
            raw = json
            saves++
        }
    }

    private val trinity = Stop("s1", "1001", "Trinity College", 53.3441, -6.2578)
    private val heuston = Stop("s2", "1002", "Heuston Station", 53.3468, -6.2951)

    @Test
    fun `toggling stores the stop details so the list renders offline`() {
        val storage = FakeStorage()
        val repository = FavouritesRepository(storage)

        repository.toggle(trinity)

        val stored = repository.favourites.value.single()
        assertEquals("Trinity College", stored.name)
        assertEquals("1001", stored.code)
        assertTrue(repository.isFavourite("s1"))
        assertTrue(storage.raw!!.contains("Trinity College"))
    }

    @Test
    fun `toggling twice removes the favourite`() {
        val repository = FavouritesRepository(FakeStorage())

        repository.toggle(trinity)
        repository.toggle(trinity)

        assertEquals(emptyList(), repository.favourites.value)
        assertFalse(repository.isFavourite("s1"))
    }

    @Test
    fun `ids flow stays in step with the stored favourites`() {
        val repository = FavouritesRepository(FakeStorage())

        repository.toggle(trinity)
        repository.toggle(heuston)

        assertEquals(setOf("s1", "s2"), repository.favouriteIds.value)

        repository.remove("s1")
        assertEquals(setOf("s2"), repository.favouriteIds.value)
    }

    @Test
    fun `favourites are reloaded from storage`() {
        val storage = FakeStorage()
        FavouritesRepository(storage).toggle(trinity)

        assertEquals(listOf("s1"), FavouritesRepository(storage).favourites.value.map { it.id })
    }

    @Test
    fun `entries migrated from the old id-only format are flagged for backfill`() {
        val repository = FavouritesRepository(FakeStorage("""[{"id":"s1"}]"""))

        val favourite = repository.favourites.value.single()
        assertTrue(favourite.needsDetails)

        repository.updateDetails(trinity)
        assertFalse(repository.favourites.value.single().needsDetails)
        assertEquals("Trinity College", repository.favourites.value.single().name)
    }

    @Test
    fun `updating details of an unknown stop changes nothing`() {
        val repository = FavouritesRepository(FakeStorage())
        repository.toggle(trinity)

        repository.updateDetails(heuston)

        assertEquals(listOf("s1"), repository.favourites.value.map { it.id })
    }

    @Test
    fun `corrupt storage falls back to an empty list`() {
        assertEquals(emptyList(), FavouritesRepository(FakeStorage("nonsense")).favourites.value)
    }

    @Test
    fun `a favourite keeps which way its routes head, across a restart`() {
        val storage = FakeStorage()
        val withHeadings = trinity.copy(headings = listOf(RouteHeading("15", "W"), RouteHeading("27", "NE")))
        FavouritesRepository(storage).toggle(withHeadings)

        val reloaded = FavouritesRepository(storage).favourites.value.single().toStop()

        assertEquals(listOf(RouteHeading("15", "W"), RouteHeading("27", "NE")), reloaded.headings)
    }

    @Test
    fun `updating details fills in the headings`() {
        val repository = FavouritesRepository(FakeStorage())
        repository.toggle(trinity) // saved before the server said which way its routes go
        assertEquals(emptyList(), repository.favourites.value.single().headings)

        repository.updateDetails(trinity.copy(headings = listOf(RouteHeading("15", "W"))))

        assertEquals(listOf(FavouriteHeading("15", "W")), repository.favourites.value.single().headings)
    }

    @Test
    fun `favourites saved before headings existed still load`() {
        val storage = FakeStorage("""[{"id":"s1","code":"1001","name":"Trinity College","lat":53.3441,"lon":-6.2578}]""")

        val favourite = FavouritesRepository(storage).favourites.value.single()

        assertEquals("Trinity College", favourite.name)
        assertEquals(emptyList(), favourite.headings)
    }

    @Test
    fun `refreshing a favourite with nothing new does not rewrite storage`() {
        val storage = FakeStorage()
        val repository = FavouritesRepository(storage)
        repository.toggle(trinity)
        val savesAfterToggle = storage.saves

        repository.updateDetails(trinity)

        assertEquals(savesAfterToggle, storage.saves)
    }

    @Test
    fun `refreshing replaces out-of-date headings`() {
        val repository = FavouritesRepository(FakeStorage())
        repository.toggle(trinity.copy(headings = listOf(RouteHeading("15", "W"))))

        repository.updateDetails(trinity.copy(headings = listOf(RouteHeading("15", "NW"))))

        assertEquals(listOf(FavouriteHeading("15", "NW")), repository.favourites.value.single().headings)
    }

    @Test
    fun `a nickname survives a refresh of the stop details and a restart`() {
        val storage = FakeStorage()
        val repository = FavouritesRepository(storage)
        repository.toggle(trinity)

        repository.rename("s1", "  Work  ")
        repository.updateDetails(trinity.copy(name = "Trinity College East"))

        assertEquals("Work", repository.favourites.value.single().nickname)
        assertEquals("Work", FavouritesRepository(storage).favourites.value.single().nickname)
    }

    @Test
    fun `widget exclusions survive a refresh of the stop details and a restart`() {
        val storage = FakeStorage()
        val repository = FavouritesRepository(storage)
        repository.toggle(trinity)

        repository.setExcludedFromWidgets("s1", true)
        repository.setHiddenRoutes("s1", setOf("15", "4"))
        repository.updateDetails(trinity.copy(name = "Trinity College East"))

        val stored = FavouritesRepository(storage).favourites.value.single()
        assertTrue(stored.excludedFromWidgets)
        assertEquals(setOf("15", "4"), stored.hiddenRoutes)
        assertEquals("Trinity College East", stored.name)
    }
}
