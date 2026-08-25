package dev.dublinbustracker.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeFavouritesStorage(initial: Set<String> = emptySet()) : FavouritesStorage {
    var saved: Set<String> = initial
    override fun load(): Set<String> = saved
    override fun save(stopIds: Set<String>) {
        saved = stopIds
    }
}

class FavouritesRepositoryTest {
    @Test
    fun `toggle adds and removes, persisting via storage`() {
        val storage = FakeFavouritesStorage()
        val repository = FavouritesRepository(storage)

        repository.toggle("s1")
        assertTrue(repository.isFavourite("s1"))
        assertEquals(setOf("s1"), storage.saved)

        repository.toggle("s1")
        assertFalse(repository.isFavourite("s1"))
        assertEquals(emptySet(), storage.saved)
    }

    @Test
    fun `loads initial favourites from storage`() {
        val repository = FavouritesRepository(FakeFavouritesStorage(setOf("s1", "s2")))
        assertEquals(setOf("s1", "s2"), repository.favouriteIds.value)
    }
}
