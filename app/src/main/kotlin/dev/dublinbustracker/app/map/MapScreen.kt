package dev.dublinbustracker.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.TransitApiClient
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

private val DUBLIN_CITY_CENTRE = LatLng(53.3498, -6.2603)
// MapLibre zoom levels run one lower than the 256px-tile scale (osmdroid, web maps) most references use.
private const val DEFAULT_ZOOM = 14.0
private const val MY_LOCATION_ZOOM = 16.0

private const val BUS_ORANGE = 0xFFEF6C00.toInt()

private const val STOPS_SOURCE = "stops"
private const val CLUSTER_LAYER = "stop-clusters"
private const val STOP_LAYER = "stop-points"
private const val BUSES_SOURCE = "buses"
private const val BUS_CIRCLE_LAYER = "bus-circles"

@Composable
fun MapScreen(apiClient: TransitApiClient, onStopSelected: (Stop) -> Unit) {
    val viewModel = viewModel<MapViewModel>(
        factory = viewModelFactory { initializer { MapViewModel(apiClient) } },
    )
    val state by viewModel.uiState.collectAsState()
    val showBuses by viewModel.showBuses.collectAsState()
    val context = LocalContext.current
    val handle = rememberMapHandle()
    val latestStops by rememberUpdatedState(state.stops)
    val latestOnStopSelected by rememberUpdatedState(onStopSelected)

    var pendingLocationRequest by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingLocationRequest) handle.map?.let { seekToCurrentLocation(context, it) }
        pendingLocationRequest = false
    }

    // Layers are added once per loaded style; the data pushed into them below changes freely.
    LaunchedEffect(handle.style) {
        val style = handle.style ?: return@LaunchedEffect
        handle.map?.cameraPosition = CameraPosition.Builder().target(DUBLIN_CITY_CENTRE).zoom(DEFAULT_ZOOM).build()

        style.addSource(
            GeoJsonSource(
                STOPS_SOURCE,
                collectionOf(emptyList()),
                GeoJsonOptions().withCluster(true).withClusterRadius(50).withClusterMaxZoom(15),
            ),
        )
        style.addLayer(
            CircleLayer(CLUSTER_LAYER, STOPS_SOURCE).withFilter(Expression.has("point_count")).withProperties(
                circleColor(MAP_BLUE),
                circleStrokeColor(android.graphics.Color.WHITE),
                circleStrokeWidth(2f),
                circleRadius(
                    Expression.step(
                        Expression.get("point_count"),
                        Expression.literal(14),
                        Expression.stop(10, Expression.literal(18)),
                        Expression.stop(50, Expression.literal(24)),
                    ),
                ),
            ),
        )
        style.addLayer(
            SymbolLayer("stop-cluster-count", STOPS_SOURCE).withFilter(Expression.has("point_count")).withProperties(
                textField(Expression.get("point_count_abbreviated")),
                textFont(MAP_BOLD_FONT),
                textSize(12f),
                textColor(android.graphics.Color.WHITE),
                textAllowOverlap(true),
            ),
        )
        style.addLayer(
            CircleLayer(STOP_LAYER, STOPS_SOURCE).withFilter(Expression.not(Expression.has("point_count"))).withProperties(
                circleColor(android.graphics.Color.WHITE),
                circleStrokeColor(MAP_BLUE),
                circleStrokeWidth(2.5f),
                circleRadius(7f),
            ),
        )

        style.addSource(GeoJsonSource(BUSES_SOURCE, collectionOf(emptyList())))
        style.addLayer(
            CircleLayer(BUS_CIRCLE_LAYER, BUSES_SOURCE).withProperties(
                circleColor(BUS_ORANGE),
                circleStrokeColor(android.graphics.Color.WHITE),
                circleStrokeWidth(2f),
                circleRadius(15f),
            ),
        )
        style.addLayer(
            SymbolLayer("bus-labels", BUSES_SOURCE).withProperties(
                textField(Expression.get("label")),
                textFont(MAP_BOLD_FONT),
                textSize(11f),
                textColor(android.graphics.Color.WHITE),
                textAllowOverlap(true),
            ),
        )
    }

    LaunchedEffect(handle.style, state.stops) {
        handle.style?.getSourceAs<GeoJsonSource>(STOPS_SOURCE)?.setGeoJson(
            collectionOf(state.stops.map { featureAt(it.lat, it.lon, "id" to it.id) }),
        )
    }
    LaunchedEffect(handle.style, state.vehicles) {
        handle.style?.getSourceAs<GeoJsonSource>(BUSES_SOURCE)?.setGeoJson(
            collectionOf(
                state.vehicles.map {
                    featureAt(it.position.lat, it.position.lon, "label" to it.routeShortName)
                },
            ),
        )
    }

    DisposableEffect(handle.map) {
        val map = handle.map ?: return@DisposableEffect onDispose { }
        val idleListener = MapLibreMap.OnCameraIdleListener { viewModel.onViewportChanged(map.currentViewport()) }
        val clickListener = MapLibreMap.OnMapClickListener { latLng ->
            val screenPoint = map.projection.toScreenLocation(latLng)
            val hit = map.queryRenderedFeatures(screenPoint, CLUSTER_LAYER, STOP_LAYER).firstOrNull()
                ?: return@OnMapClickListener false
            val point = hit.geometry() as? Point ?: return@OnMapClickListener false
            val stopId = hit.getStringProperty("id")
            if (stopId == null) {
                // A cluster: zoom in on it rather than opening anything.
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude(), point.longitude()), map.cameraPosition.zoom + 2),
                    300,
                )
            } else {
                latestStops.firstOrNull { it.id == stopId }?.let(latestOnStopSelected)
            }
            true
        }
        map.addOnCameraIdleListener(idleListener)
        map.addOnMapClickListener(clickListener)
        viewModel.onViewportChanged(map.currentViewport())
        onDispose {
            map.removeOnCameraIdleListener(idleListener)
            map.removeOnMapClickListener(clickListener)
        }
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
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { handle.view })

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
                        handle.map?.let { seekToCurrentLocation(context, it) }
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
private fun seekToCurrentLocation(context: Context, map: MapLibreMap) {
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return
    val onFix: (Location) -> Unit = { location ->
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), MY_LOCATION_ZOOM),
            500,
        )
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

private fun MapLibreMap.currentViewport(): Viewport {
    val bounds = projection.visibleRegion.latLngBounds
    return Viewport(
        centerLat = bounds.center.latitude,
        centerLon = bounds.center.longitude,
        radiusMeters = (bounds.northEast.distanceTo(bounds.southWest) / 2).toInt().coerceAtLeast(200),
        minLat = bounds.latitudeSouth,
        minLon = bounds.longitudeWest,
        maxLat = bounds.latitudeNorth,
        maxLon = bounds.longitudeEast,
    )
}
