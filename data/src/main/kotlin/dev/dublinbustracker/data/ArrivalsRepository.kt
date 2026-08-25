package dev.dublinbustracker.data

import dev.dublinbustracker.core.Arrival

interface ArrivalsRepository {
    val pollIntervalMs: Long
    suspend fun arrivalsForStop(stopId: String): List<Arrival>
}
