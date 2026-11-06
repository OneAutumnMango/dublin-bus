package dev.dublinbustracker.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

private const val FIX_TIMEOUT_MS = 10_000L

/** A remembered fix older than this might be from somewhere else entirely, so a fresh one is asked for. */
private const val MAX_FIX_AGE_MS = 2 * 60_000L

/** Asked for together: the user may grant only the approximate one, and that is enough to find the nearest stops. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * Finds where the device is: a recent fix if there is one, otherwise a fresh one - and if that
 * doesn't arrive within a few seconds, the older fix is better than nothing. The caller must
 * already hold the fine or coarse location permission; with only coarse, GPS (which needs fine) is skipped.
 */
@SuppressLint("MissingPermission")
fun requestCurrentLocation(context: Context, onUnavailable: () -> Unit = {}, onFix: (Location) -> Unit) {
    val locationManager = context.getSystemService(LocationManager::class.java)
    if (locationManager == null) {
        onUnavailable()
        return
    }

    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val providers = listOfNotNull(
        LocationManager.GPS_PROVIDER.takeIf { hasFine },
        LocationManager.NETWORK_PROVIDER,
    ).filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
    val lastKnown = providers
        .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= MAX_FIX_AGE_MS) {
        onFix(lastKnown)
        return
    }

    val provider = providers.firstOrNull()
    if (provider == null) {
        if (lastKnown != null) onFix(lastKnown) else onUnavailable()
        return
    }

    val handler = Handler(Looper.getMainLooper())
    var done = false
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            locationManager.removeUpdates(this)
            onFix(location)
        }
    }
    val requested = runCatching { locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
    if (requested.isFailure) {
        if (lastKnown != null) onFix(lastKnown) else onUnavailable()
        return
    }
    handler.postDelayed(
        {
            if (!done) {
                done = true
                locationManager.removeUpdates(listener)
                if (lastKnown != null) onFix(lastKnown) else onUnavailable()
            }
        },
        FIX_TIMEOUT_MS,
    )
}
