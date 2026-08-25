package dev.dublinbustracker.app.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.StopRepository
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val DUBLIN_CITY_CENTRE = GeoPoint(53.3498, -6.2603)

@Composable
fun MapScreen(stopRepository: StopRepository, onStopSelected: (Stop) -> Unit) {
    val stops = remember { stopRepository.all() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            controller.setZoom(13.0)
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

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            mapView.apply {
                setMultiTouchControls(true)
                overlays.addAll(
                    stops.map { stop ->
                        Marker(this).apply {
                            position = GeoPoint(stop.lat, stop.lon)
                            title = "${stop.name} (${stop.code})"
                            setOnMarkerClickListener { _, _ -> onStopSelected(stop); true }
                        }
                    },
                )
                invalidate()
            }
        },
    )
}
