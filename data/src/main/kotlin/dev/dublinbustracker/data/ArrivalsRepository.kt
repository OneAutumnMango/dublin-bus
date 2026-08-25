package dev.dublinbustracker.data

import dev.dublinbustracker.core.Arrival
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArrivalsRepository(
    private val client: RealtimeGtfsClient,
    private val gtfsStaticData: GtfsStaticData,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    suspend fun arrivalsForStop(stopId: String): List<Arrival> = withContext(Dispatchers.IO) {
        val feed = client.fetchTripUpdates()
        val now = nowEpochSeconds()
        feed.entityList
            .asSequence()
            .filter { it.hasTripUpdate() }
            .flatMap { entity ->
                entity.tripUpdate.stopTimeUpdateList.asSequence()
                    .filter { it.stopId == stopId }
                    .mapNotNull { stopTimeUpdate ->
                        val etaSeconds = when {
                            stopTimeUpdate.hasArrival() && stopTimeUpdate.arrival.hasTime() -> stopTimeUpdate.arrival.time
                            stopTimeUpdate.hasDeparture() && stopTimeUpdate.departure.hasTime() -> stopTimeUpdate.departure.time
                            else -> null
                        } ?: return@mapNotNull null
                        val tripInfo = gtfsStaticData.tripInfo(entity.tripUpdate.trip.tripId)
                        Arrival(
                            routeShortName = tripInfo?.routeShortName ?: entity.tripUpdate.trip.routeId,
                            destination = tripInfo?.headsign.orEmpty(),
                            dueMinutes = ((etaSeconds - now) / 60).toInt().coerceAtLeast(0),
                            isRealtime = true,
                        )
                    }
            }
            .sortedBy { it.dueMinutes }
            .toList()
    }
}
