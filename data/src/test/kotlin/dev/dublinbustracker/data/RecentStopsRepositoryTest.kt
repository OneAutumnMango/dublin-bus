package dev.dublinbustracker.data

import dev.dublinbustracker.core.Stop
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentStopsRepositoryTest {
    private class FakeStorage(var raw: String? = null) : FavouritesStorage {
        override fun loadRaw(): String? = raw
        override fun saveRaw(json: String) {
            raw = json
        }
    }

    private fun stop(id: String) = Stop(id, "c$id", "Stop $id", 53.0, -6.0)

    @Test
    fun `newest first, and opening a stop again moves it to the front without a duplicate`() {
        val repository = RecentStopsRepository(FakeStorage())

        repository.add(stop("a"))
        repository.add(stop("b"))
        repository.add(stop("a"))

        assertEquals(listOf("a", "b"), repository.recent.value.map { it.id })
    }

    @Test
    fun `only the most recent few are kept, and they survive a restart`() {
        val storage = FakeStorage()
        val repository = RecentStopsRepository(storage, limit = 2)

        listOf("a", "b", "c").forEach { repository.add(stop(it)) }

        assertEquals(listOf("c", "b"), RecentStopsRepository(storage, limit = 2).recent.value.map { it.id })
    }

    @Test
    fun `clearing empties the list for good, and corrupt storage reads as empty`() {
        val storage = FakeStorage()
        RecentStopsRepository(storage).apply { add(stop("a")); clear() }

        assertEquals(emptyList(), RecentStopsRepository(storage).recent.value)
        assertEquals(emptyList(), RecentStopsRepository(FakeStorage("nonsense")).recent.value)
    }
}
