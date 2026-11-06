package dev.dublinbustracker.core

/** A bus that has moved less than this since it was last measured is treated as standing still. */
private const val MIN_MOVE_METERS = 20.0

/** Further than a bus can drive between two refreshes: the earlier fix was stale, so it can't say which way it went. */
private const val MAX_MOVE_METERS = 1_500.0

/**
 * The feed gives a heading for only some buses. For the rest this works one out from where the bus
 * was at the last refresh, and holds the last heading while it is stopped. A bus seen for the first
 * time has no heading until it has moved.
 */
class BearingTracker {
    private val lastFix = HashMap<String, LatLon>()
    private val bearings = HashMap<String, Float>()

    fun withBearings(vehicles: List<VehiclePosition>): List<VehiclePosition> = vehicles.map { vehicle ->
        val key = vehicle.vehicleId
        val here = vehicle.position
        vehicle.bearing?.let {
            lastFix[key] = here
            bearings[key] = it
            return@map vehicle
        }
        val before = lastFix[key]
        if (before == null) {
            lastFix[key] = here
        } else {
            val moved = distanceMeters(before, here)
            if (moved > MAX_MOVE_METERS) {
                lastFix[key] = here
            } else if (moved >= MIN_MOVE_METERS) {
                bearings[key] = bearingDegrees(before, here).toFloat()
                lastFix[key] = here
            }
        }
        vehicle.copy(bearing = bearings[key])
    }
}
