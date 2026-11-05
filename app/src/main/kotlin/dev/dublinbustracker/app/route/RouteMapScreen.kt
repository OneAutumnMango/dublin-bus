package dev.dublinbustracker.app.route

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.TransitApiClient
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val ROUTE_LINE_COLOR = 0xFF1565C0.toInt()
private const val THIS_STOP_COLOR = 0xFFD32F2F.toInt()
private const val YOUR_BUS_COLOR = 0xFF2E7D32.toInt()
private const val OTHER_BUS_COLOR = 0xFFEF6C00.toInt()
private const val FIT_PADDING_PX = 80

/**
 * The route [arrival] is running: its path, every stop along it, [stop] (the one being viewed)
 * highlighted, and where the buses on that route are right now.
 */
@Composable
fun RouteMapScreen(stop: Stop, arrival: Arrival, apiClient: TransitApiClient, onBack: () -> Unit) {
    val viewModel = viewModel<RouteMapViewModel>(
        key = arrival.tripId,
        factory = viewModelFactory { initializer { RouteMapViewModel(arrival.tripId, apiClient) } },
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onBack)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            overlays.add(CopyrightOverlay(context))
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

    // The camera is fitted to the route once; later bus refreshes must not yank it back
    // while the user is panning around.
    val fitted = remember { booleanArrayOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${arrival.routeShortName} to ${arrival.destination}", style = MaterialTheme.typography.titleMedium)
                Text("Viewing ${stop.name} (${stop.code})", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
        Legend(busCount = state.vehicles.size)

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        // osmdroid draws outside its own bounds, over the header above, unless clipped.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                factory = { mapView },
                update = { map ->
                    val data = state.data ?: return@AndroidView
                    map.overlays.removeAll { it is Marker || it is Polyline }

                    map.overlays.add(
                        Polyline(map).apply {
                            setPoints(data.shape.map { GeoPoint(it.lat, it.lon) })
                            outlinePaint.color = ROUTE_LINE_COLOR
                            outlinePaint.strokeWidth = 10f
                        },
                    )
                    data.stops.filter { it.id != stop.id }.forEach { map.overlays.add(stopMarker(map, it, highlighted = false)) }

                    state.vehicles.forEach { vehicle ->
                        val yours = arrival.vehicleId != null && vehicle.vehicleId == arrival.vehicleId
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(vehicle.position.lat, vehicle.position.lon)
                                title = "${vehicle.routeShortName} to ${vehicle.headsign}" + if (yours) " (your bus)" else ""
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = labelIcon(map.context, vehicle.routeShortName, if (yours) YOUR_BUS_COLOR else OTHER_BUS_COLOR)
                            },
                        )
                    }

                    // Last, so a bus parked at the viewed stop can't hide it.
                    map.overlays.add(stopMarker(map, stop, highlighted = true))

                    if (!fitted[0] && data.shape.isNotEmpty()) {
                        fitted[0] = true
                        val box = BoundingBox.fromGeoPoints(data.shape.map { GeoPoint(it.lat, it.lon) })
                        // zoomToBoundingBox needs a laid-out view, which this one may not be yet.
                        map.post { map.zoomToBoundingBox(box, false, FIT_PADDING_PX) }
                    }
                    map.invalidate()
                },
            )
            if (state.data == null && state.error == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun Legend(busCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(Color(THIS_STOP_COLOR), "This stop")
        LegendItem(Color(YOUR_BUS_COLOR), "Your bus")
        LegendItem(Color(OTHER_BUS_COLOR), if (busCount == 1) "1 bus" else "$busCount buses")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun stopMarker(map: MapView, stop: Stop, highlighted: Boolean) = Marker(map).apply {
    position = GeoPoint(stop.lat, stop.lon)
    title = "${stop.name} (${stop.code})"
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = dotIcon(map.context, if (highlighted) THIS_STOP_COLOR else android.graphics.Color.WHITE, if (highlighted) 24 else 9, ROUTE_LINE_COLOR)
}

private fun dotIcon(context: Context, fill: Int, sizeDp: Int, stroke: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val strokeWidth = 2 * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.FILL
    paint.color = fill
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - strokeWidth, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
    paint.color = stroke
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - strokeWidth, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** A rounded label showing the route number, so a bus reads as a bus rather than a dot. */
private fun labelIcon(context: Context, label: String, fill: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 12 * density
        isFakeBoldText = true
    }
    val padding = 6 * density
    val width = (text.measureText(label) + 2 * padding).toInt()
    val height = (22 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), height / 2f, height / 2f, background)
    canvas.drawText(label, padding, height / 2f - (text.ascent() + text.descent()) / 2f, text)
    return BitmapDrawable(context.resources, bitmap)
}
