package dev.dublinbustracker.app.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.FilledTonalIconToggleButton
import dev.dublinbustracker.app.bus.BusInfoSheet
import dev.dublinbustracker.app.location.requestCurrentLocation
import dev.dublinbustracker.app.ui.PollWhileVisible
import dev.dublinbustracker.core.OkabeIto
import dev.dublinbustracker.core.toHexColour
import dev.dublinbustracker.core.trackingColour
import dev.dublinbustracker.core.trackingTextColour
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
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

private const val BUS_ORANGE = OkabeIto.ORANGE

private const val STOPS_SOURCE = "stops"
private const val CLUSTER_LAYER = "stop-clusters"
private const val STOP_LAYER = "stop-points"
private const val BUSES_SOURCE = "buses"
private const val BUS_CIRCLE_LAYER = "bus-circles"
private const val TRACK_LINES_SOURCE = "track-lines"
private const val TRACK_BUSES_SOURCE = "track-buses"
private const val TRACK_STOPS_SOURCE = "track-stops"
private const val TRACK_STOP_LAYER = "track-stop-points"
private const val TRACK_BUS_LAYER = "track-bus-circles"

// Hidden while following routes, so only the followed buses and paths are on the map.
private val BROWSE_LAYERS = listOf(CLUSTER_LAYER, "stop-cluster-count", STOP_LAYER, BUS_CIRCLE_LAYER, "bus-labels")
private val TRACK_LAYERS = listOf("track-lines", TRACK_STOP_LAYER, TRACK_BUS_LAYER, "track-bus-labels")

@Composable
fun MapScreen(apiClient: TransitApiClient, onStopSelected: (Stop) -> Unit) {
    val viewModel = viewModel<MapViewModel>(
        factory = viewModelFactory { initializer { MapViewModel(apiClient) } },
    )
    val state by viewModel.uiState.collectAsState()
    val showBuses by viewModel.showBuses.collectAsState()
    val tracking by viewModel.tracking.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    PollWhileVisible(BUS_REFRESH_MS, viewModel::poll)
    val context = LocalContext.current
    val handle = rememberMapHandle()
    val latestStops by rememberUpdatedState(state.stops)
    val latestOnStopSelected by rememberUpdatedState(onStopSelected)
    val latestTracking by rememberUpdatedState(tracking)
    var infoTripId by remember { mutableStateOf<String?>(null) }

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
                textColor(android.graphics.Color.BLACK),
                textAllowOverlap(true),
            ),
        )

        // Followed routes: their paths and buses, coloured by route and direction from each feature's own properties.
        style.addSource(GeoJsonSource(TRACK_LINES_SOURCE, collectionOf(emptyList())))
        style.addLayer(
            LineLayer("track-lines", TRACK_LINES_SOURCE).withProperties(
                lineColor(Expression.toColor(Expression.get("color"))),
                lineWidth(4f),
                lineOpacity(0.8f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addSource(GeoJsonSource(TRACK_STOPS_SOURCE, collectionOf(emptyList())))
        style.addLayer(
            CircleLayer(TRACK_STOP_LAYER, TRACK_STOPS_SOURCE).withProperties(
                circleColor(android.graphics.Color.WHITE),
                circleStrokeColor(Expression.toColor(Expression.get("color"))),
                circleStrokeWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(9, 1f), Expression.stop(15, 2.5f))),
                circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(9, 2f), Expression.stop(15, 5.5f))),
            ),
        )
        style.addSource(GeoJsonSource(TRACK_BUSES_SOURCE, collectionOf(emptyList())))
        style.addLayer(
            CircleLayer(TRACK_BUS_LAYER, TRACK_BUSES_SOURCE).withProperties(
                circleColor(Expression.toColor(Expression.get("color"))),
                circleStrokeColor(android.graphics.Color.WHITE),
                circleStrokeWidth(2f),
                circleRadius(15f),
            ),
        )
        style.addLayer(
            SymbolLayer("track-bus-labels", TRACK_BUSES_SOURCE).withProperties(
                textField(Expression.get("label")),
                textFont(MAP_BOLD_FONT),
                textSize(11f),
                textColor(Expression.toColor(Expression.get("textColor"))),
                textAllowOverlap(true),
            ),
        )
    }

    // Following routes swaps the whole map over to just those routes' paths and buses.
    LaunchedEffect(handle.style, tracking.enabled, tracking.showStops) {
        val style = handle.style ?: return@LaunchedEffect
        BROWSE_LAYERS.forEach { style.getLayer(it)?.setProperties(visibility(if (tracking.enabled) Property.NONE else Property.VISIBLE)) }
        TRACK_LAYERS.forEach { style.getLayer(it)?.setProperties(visibility(if (tracking.enabled) Property.VISIBLE else Property.NONE)) }
        style.getLayer(TRACK_STOP_LAYER)?.setProperties(visibility(if (tracking.enabled && tracking.showStops) Property.VISIBLE else Property.NONE))
    }
    LaunchedEffect(handle.style, tracking.routes, tracking.vehicles, tracking.shapes, tracking.stops, tracking.hidden) {
        val style = handle.style ?: return@LaunchedEffect
        val lines = tracking.routes.flatMap { route ->
            tracking.shapes[route.name].orEmpty().filter { (direction, path) -> path.size >= 2 && (route.name to direction) !in tracking.hidden }.map { (direction, path) ->
                Feature.fromGeometry(LineString.fromLngLats(path.map { Point.fromLngLat(it.lon, it.lat) }))
                    .also { it.addStringProperty("color", trackingColour(route.colourIndex, direction).toHexColour()) }
            }
        }
        val buses = tracking.routes.flatMap { route ->
            tracking.vehicles[route.name].orEmpty().filter { (route.name to it.directionId) !in tracking.hidden }.map { bus ->
                featureAt(
                    bus.position.lat,
                    bus.position.lon,
                    "label" to bus.routeShortName,
                    "color" to trackingColour(route.colourIndex, bus.directionId).toHexColour(),
                    "textColor" to trackingTextColour(route.colourIndex, bus.directionId).toHexColour(),
                    "tripId" to bus.tripId,
                )
            }
        }
        val stopPoints = tracking.routes.flatMap { route ->
            tracking.stops[route.name].orEmpty().filterKeys { (route.name to it) !in tracking.hidden }.flatMap { (direction, list) ->
                list.map { featureAt(it.lat, it.lon, "id" to it.id, "color" to trackingColour(route.colourIndex, direction).toHexColour()) }
            }
        }
        style.getSourceAs<GeoJsonSource>(TRACK_STOPS_SOURCE)?.setGeoJson(collectionOf(stopPoints))
        style.getSourceAs<GeoJsonSource>(TRACK_LINES_SOURCE)?.setGeoJson(collectionOf(lines))
        style.getSourceAs<GeoJsonSource>(TRACK_BUSES_SOURCE)?.setGeoJson(collectionOf(buses))
    }

    // When a route is added, frame everything being followed so the new one is in view.
    var followedCount by remember { mutableStateOf(0) }
    LaunchedEffect(tracking.routes.size, tracking.shapes) {
        if (tracking.routes.size > followedCount) {
            val points = tracking.shapes.values.flatMap { it.values }.flatten().map { LatLng(it.lat, it.lon) }
            val map = handle.map
            if (map != null && points.size >= 2) {
                map.easeCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 80), 600)
            }
        }
        followedCount = tracking.routes.size
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
            map.queryRenderedFeatures(screenPoint, TRACK_BUS_LAYER).firstOrNull()?.getStringProperty("tripId")?.let {
                infoTripId = it
                return@OnMapClickListener true
            }
            map.queryRenderedFeatures(screenPoint, TRACK_STOP_LAYER).firstOrNull()?.getStringProperty("id")?.let { stopId ->
                latestTracking.stops.values.flatMap { it.values }.flatten().firstOrNull { it.id == stopId }?.let(latestOnStopSelected)
                return@OnMapClickListener true
            }
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
        val modeToggle: @Composable () -> Unit = {
            FilledTonalIconToggleButton(checked = tracking.enabled, onCheckedChange = viewModel::setTracking) {
                Icon(Icons.Filled.Route, contentDescription = if (tracking.enabled) "Stop following routes" else "Follow routes")
            }
        }

        if (!tracking.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = showBuses,
                    onClick = viewModel::toggleBuses,
                    label = { Text(if (showBuses) "Buses shown" else "Show buses") },
                )
                Spacer(modifier = Modifier.weight(1f))
                modeToggle()
            }
        } else {
            TrackingPanel(
                state = tracking,
                suggestions = suggestions,
                onQueryChanged = viewModel::onRouteQueryChanged,
                onAdd = viewModel::addRoute,
                onRemove = viewModel::removeRoute,
                onToggleDirection = viewModel::toggleDirection,
                onShowStops = viewModel::setShowStops,
                modeToggle = modeToggle,
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

            if (state.loading && !tracking.enabled) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
            }

            MapCompass(handle, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

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

    infoTripId?.let { tripId ->
        BusInfoSheet(
            tripId = tripId,
            contextStop = null,
            arrival = null,
            apiClient = apiClient,
            onViewOnMap = null,
            onDismiss = { infoTripId = null },
        )
    }
}

/** Centres the map on the device's current location. Caller must already hold the location permission. */
private fun seekToCurrentLocation(context: Context, map: MapLibreMap) {
    requestCurrentLocation(context) { location ->
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), MY_LOCATION_ZOOM),
            500,
        )
    }
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
