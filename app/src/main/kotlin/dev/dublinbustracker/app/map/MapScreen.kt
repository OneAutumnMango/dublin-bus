package dev.dublinbustracker.app.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import org.osmdroid.views.overlay.Marker

private val DUBLIN_CITY_CENTRE = GeoPoint(53.3498, -6.2603)

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
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(DUBLIN_CITY_CENTRE)
        }
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
                    state.stops.forEach { stop ->
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(stop.lat, stop.lon)
                                title = "${stop.name} (${stop.code})"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { _, _ -> onStopSelected(stop); true }
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
        }
    }
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
