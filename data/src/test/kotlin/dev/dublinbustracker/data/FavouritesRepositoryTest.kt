package dev.dublinbustracker.data

import dev.dublinbustracker.core.Stop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavouritesRepositoryTest {
    private class FakeStorage(var raw: String? = null) : FavouritesStorage {
        override fun loadRaw(): String? = raw
        override fun saveRaw(json: String) {
            raw = json
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
}
