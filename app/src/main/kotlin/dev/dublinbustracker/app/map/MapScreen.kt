package dev.dublinbustracker.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.TransitApiClient
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

private val DUBLIN_CITY_CENTRE = GeoPoint(53.3498, -6.2603)

// Stops at city-wide zoom otherwise render as 100+ overlapping pins. Markers whose screen
// positions land within this many pixels of each other are merged into one numbered pin.
private const val STOP_CLUSTER_RADIUS_PX = 60

private const val MY_LOCATION_ZOOM = 17.0

@Composable
fun MapScreen(apiClient: TransitApiClient, onStopSelected: (Stop) -> Unit) {
    val viewModel = viewModel<MapViewModel>(
        factory = viewModelFactory { initializer { MapViewModel(apiClient) } },
    )
    val state by viewModel.uiState.collectAsState()
    val showBuses by viewModel.showBuses.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            // Every free "nicer-looking" raster provider (CARTO, Wikimedia, Stadia/Stamen,
            // Thunderforest, MapTiler) now gates tiles behind a signup/API key - confirmed live,
            // not assumed; see the commit message. Mapnik remains the one genuinely free,
            // unauthenticated option, so that's the baseline until an API key is wired in.
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(DUBLIN_CITY_CENTRE)
            overlays.add(CopyrightOverlay(context))
        }
    }
    var pendingLocationRequest by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingLocationRequest) seekToCurrentLocation(context, mapView)
        pendingLocationRequest = false
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                viewModel.onViewportChanged(mapView.currentViewport())
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                viewModel.onViewportChanged(mapView.currentViewport())
                return false
            }
        }
        mapView.addMapListener(listener)
        viewModel.onViewportChanged(mapView.currentViewport())
        onDispose { mapView.removeMapListener(listener) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            FilterChip(
                selected = showBuses,
                onClick = viewModel::toggleBuses,
                label = { Text(if (showBuses) "Buses shown" else "Show buses") },
            )
        }

        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
                update = { map ->
                    map.overlays.removeAll { it is Marker }

                    clusterStops(map, state.stops).forEach { cluster ->
                        val anchor = cluster.first()
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(anchor.lat, anchor.lon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                if (cluster.size == 1) {
                                    title = "${anchor.name} (${anchor.code})"
                                    setOnMarkerClickListener { _, _ -> onStopSelected(anchor); true }
                                } else {
                                    title = "${cluster.size} stops"
                                    setTextIcon(cluster.size.toString())
                                    setOnMarkerClickListener { _, markerMap ->
                                        markerMap.controller.animateTo(
                                            position,
                                            (markerMap.zoomLevelDouble + 2).coerceAtMost(20.0),
                                            300L,
                                        )
                                        true
                                    }
                                }
                            },
                        )
                    }

                    state.vehicles.forEach { vehicle ->
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(vehicle.position.lat, vehicle.position.lon)
                                title = "${vehicle.routeShortName} to ${vehicle.headsign}"
                                setTextIcon(vehicle.routeShortName)
                            },
                        )
                    }
                    map.invalidate()
                },
            )

            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
            }

            FloatingActionButton(
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        seekToCurrentLocation(context, mapView)
                    } else {
                        pendingLocationRequest = true
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Go to my location")
            }
        }
    }
}

/** Centres the map on the device's current location, requesting one fresh fix if there's no
 * recent one cached. Caller must already hold ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION. */
@SuppressLint("MissingPermission")
private fun seekToCurrentLocation(context: Context, mapView: MapView) {
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return
    val onFix: (Location) -> Unit = { location ->
        mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude), MY_LOCATION_ZOOM, 500L)
    }

    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
    val lastKnown = providers
        .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (lastKnown != null) {
        onFix(lastKnown)
        return
    }

    val provider = providers.firstOrNull() ?: return
    locationManager.requestLocationUpdates(
        provider,
        0L,
        0f,
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onFix(location)
                locationManager.removeUpdates(this)
            }
        },
        Looper.getMainLooper(),
    )
}

/** Groups stops whose current screen positions are within [STOP_CLUSTER_RADIUS_PX] of each other. */
private fun clusterStops(map: MapView, stops: List<Stop>): List<List<Stop>> {
    val projection = map.projection
    val clusters = mutableListOf<MutableList<Stop>>()
    val clusterPoints = mutableListOf<Point>()
    val radiusSquared = STOP_CLUSTER_RADIUS_PX * STOP_CLUSTER_RADIUS_PX

    stops.forEach { stop ->
        val point = Point()
        projection.toPixels(GeoPoint(stop.lat, stop.lon), point)
        val existingIndex = clusterPoints.indexOfFirst { existing ->
            val dx = existing.x - point.x
            val dy = existing.y - point.y
            dx * dx + dy * dy < radiusSquared
        }
        if (existingIndex >= 0) {
            clusters[existingIndex].add(stop)
        } else {
            clusters.add(mutableListOf(stop))
            clusterPoints.add(point)
        }
    }
    return clusters
}

private fun MapView.currentViewport(): Viewport {
    val box = boundingBox
    return Viewport(
        centerLat = box.centerLatitude,
        centerLon = box.centerLongitude,
        radiusMeters = (box.diagonalLengthInMeters / 2).toInt().coerceAtLeast(200),
        minLat = box.actualSouth,
        minLon = box.lonWest,
        maxLat = box.actualNorth,
        maxLon = box.lonEast,
    )
}
