package dev.dublinbustracker.app.route

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.app.bus.BusInfoSheet
import dev.dublinbustracker.app.map.MAP_BLUE
import dev.dublinbustracker.app.map.MapCompass
import dev.dublinbustracker.app.ui.ScreenHeader
import dev.dublinbustracker.app.map.MAP_BOLD_FONT
import dev.dublinbustracker.app.map.collectionOf
import dev.dublinbustracker.app.map.featureAt
import dev.dublinbustracker.app.map.rememberMapHandle
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.OkabeIto
import dev.dublinbustracker.core.readableTextOn
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.toHexColour
import dev.dublinbustracker.data.TransitApiClient
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.symbolSpacing
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val THIS_STOP_COLOR = OkabeIto.VERMILLION
private const val YOUR_BUS_COLOR = OkabeIto.BLUISH_GREEN
private const val OTHER_BUS_COLOR = OkabeIto.ORANGE
private const val FIT_PADDING_PX = 80
private const val ARROW_COLOR = OkabeIto.BLUE

private const val ARROW_IMAGE = "route-arrow"
private const val LINE_SOURCE = "route-line"
private const val STOPS_SOURCE = "route-stops"
private const val SELECTED_SOURCE = "route-selected"
private const val BUSES_SOURCE = "route-buses"
private const val STOPS_LAYER = "route-stops-layer"
private const val SELECTED_LAYER = "route-selected-layer"
private const val BUSES_LAYER = "route-buses-layer"

/** [farOut] when the whole route is in view, [closeIn] by street level: keeps dense stops from swamping the path. */
private fun zoomScaled(farOut: Float, closeIn: Float): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(9, farOut),
        Expression.stop(15, closeIn),
    )

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
    val handle = rememberMapHandle()
    var tappedLabel by remember { mutableStateOf<String?>(null) }
    var infoTripId by remember { mutableStateOf<String?>(null) }

    var showStops by remember { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    // Layers go in bottom-to-top once per loaded style: path, stops, buses, then the viewed
    // stop last so a bus parked there can't hide it.
    LaunchedEffect(handle.style) {
        val style = handle.style ?: return@LaunchedEffect
        listOf(LINE_SOURCE, STOPS_SOURCE, BUSES_SOURCE, SELECTED_SOURCE).forEach {
            style.addSource(GeoJsonSource(it, collectionOf(emptyList())))
        }
        style.addLayer(
            LineLayer("route-casing", LINE_SOURCE).withProperties(
                lineColor(android.graphics.Color.WHITE), lineWidth(zoomScaled(6f, 11f)), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer("route-path", LINE_SOURCE).withProperties(
                lineColor(MAP_BLUE), lineWidth(zoomScaled(4f, 7f)), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        // Chevrons along the path so the direction of travel reads at a glance.
        style.addImage(ARROW_IMAGE, arrowBitmap(handle.view.resources.displayMetrics.density))
        style.addLayer(
            SymbolLayer("route-arrows", LINE_SOURCE).withProperties(
                symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                symbolSpacing(70f),
                iconImage(ARROW_IMAGE),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            ),
        )
        style.addLayer(
            CircleLayer(STOPS_LAYER, STOPS_SOURCE).withProperties(
                circleColor(android.graphics.Color.WHITE), circleStrokeColor(MAP_BLUE), circleStrokeWidth(zoomScaled(0.8f, 2.5f)), circleRadius(zoomScaled(2f, 5.5f)),
            ),
        )
        style.addLayer(
            CircleLayer(BUSES_LAYER, BUSES_SOURCE).withProperties(
                circleColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get("yours"), Expression.literal(true)),
                        Expression.color(YOUR_BUS_COLOR),
                        Expression.color(OTHER_BUS_COLOR),
                    ),
                ),
                circleStrokeColor(android.graphics.Color.WHITE), circleStrokeWidth(2f), circleRadius(15f),
            ),
        )
        style.addLayer(
            SymbolLayer("route-bus-labels", BUSES_SOURCE).withProperties(
                textField(Expression.get("label")), textFont(MAP_BOLD_FONT), textSize(11f),
                textColor(Expression.toColor(Expression.get("textColor"))), textAllowOverlap(true),
            ),
        )
        style.addLayer(
            CircleLayer(SELECTED_LAYER, SELECTED_SOURCE).withProperties(
                circleColor(THIS_STOP_COLOR), circleStrokeColor(android.graphics.Color.WHITE), circleStrokeWidth(3f), circleRadius(11f),
            ),
        )
    }

    LaunchedEffect(handle.style, state.data) {
        val style = handle.style ?: return@LaunchedEffect
        val data = state.data ?: return@LaunchedEffect
        val points = data.shape.map { Point.fromLngLat(it.lon, it.lat) }
        if (points.size >= 2) {
            style.getSourceAs<GeoJsonSource>(LINE_SOURCE)?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))
        }
        style.getSourceAs<GeoJsonSource>(STOPS_SOURCE)?.setGeoJson(
            collectionOf(
                data.stops.filter { it.id != stop.id }
                    .map { featureAt(it.lat, it.lon, "title" to "${it.name} (${it.code})") },
            ),
        )
        style.getSourceAs<GeoJsonSource>(SELECTED_SOURCE)?.setGeoJson(
            featureAt(stop.lat, stop.lon, "title" to "${stop.name} (${stop.code}) - this stop"),
        )
        // Fitted once, here, so the 30s bus refreshes below can't yank the camera back mid-pan.
        val map = handle.map
        if (map != null && data.shape.isNotEmpty()) {
            val bounds = LatLngBounds.Builder().includes(data.shape.map { LatLng(it.lat, it.lon) }).build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX))
        }
    }

    LaunchedEffect(handle.style, showStops) {
        handle.style?.getLayer(STOPS_LAYER)?.setProperties(visibility(if (showStops) Property.VISIBLE else Property.NONE))
    }

    LaunchedEffect(handle.style, state.vehicles) {
        handle.style?.getSourceAs<GeoJsonSource>(BUSES_SOURCE)?.setGeoJson(
            collectionOf(
                state.vehicles.map { vehicle ->
                    val yours = arrival.vehicleId != null && vehicle.vehicleId == arrival.vehicleId
                    featureAt(
                        vehicle.position.lat,
                        vehicle.position.lon,
                        "label" to vehicle.routeShortName,
                        "yours" to yours,
                        "textColor" to readableTextOn(if (yours) YOUR_BUS_COLOR else OTHER_BUS_COLOR).toHexColour(),
                        "tripId" to vehicle.tripId,
                        "title" to "${vehicle.routeShortName} to ${vehicle.headsign}" + if (yours) " (your bus)" else "",
                    )
                },
            ),
        )
    }

    DisposableEffect(handle.map) {
        val map = handle.map ?: return@DisposableEffect onDispose { }
        val clickListener = MapLibreMap.OnMapClickListener { latLng ->
            val screenPoint = map.projection.toScreenLocation(latLng)
            // Topmost first, so tapping the viewed stop under a bus names the stop's own marker order.
            val hit = map.queryRenderedFeatures(screenPoint, SELECTED_LAYER, BUSES_LAYER, STOPS_LAYER).firstOrNull()
            val busTripId = hit?.getStringProperty("tripId")?.takeIf { it.isNotEmpty() }
            if (busTripId != null) {
                infoTripId = busTripId
                tappedLabel = null
            } else {
                tappedLabel = hit?.getStringProperty("title")
            }
            hit != null
        }
        map.addOnMapClickListener(clickListener)
        onDispose { map.removeOnMapClickListener(clickListener) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "${arrival.routeShortName} to ${arrival.destination}",
            subtitle = "Viewing ${stop.name} (${stop.code})",
            onBack = onBack,
        )
        Legend(busCount = state.vehicles.size, showStops = showStops, onShowStops = { showStops = it })

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { handle.view })
            if (state.data == null && state.error == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            MapCompass(handle, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
            tappedLabel?.let {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 4.dp,
                ) {
                    Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }

    infoTripId?.let { tripId ->
        BusInfoSheet(
            tripId = tripId,
            contextStop = stop,
            arrival = arrival.takeIf { it.tripId == tripId },
            apiClient = apiClient,
            onViewOnMap = null, // already on the map
            onDismiss = { infoTripId = null },
        )
    }
}

@Composable
private fun Legend(busCount: Int, showStops: Boolean, onShowStops: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(Color(THIS_STOP_COLOR), "This stop")
        LegendItem(Color(YOUR_BUS_COLOR), "Your bus")
        LegendItem(Color(OTHER_BUS_COLOR), if (busCount == 1) "1 bus" else "$busCount buses")
        Spacer(modifier = Modifier.weight(1f))
        FilterChip(selected = showStops, onClick = { onShowStops(!showStops) }, label = { Text("Stops") })
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun arrowBitmap(density: Float): Bitmap {
    val size = (18 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val path = Path().apply {
        moveTo(size * 0.2f, size * 0.15f)
        lineTo(size * 0.85f, size * 0.5f)
        lineTo(size * 0.2f, size * 0.85f)
        lineTo(size * 0.4f, size * 0.5f)
        close()
    }
    val canvas = Canvas(bitmap)
    // White outline first, so the blue arrow reads against both the path and the pale map.
    canvas.drawPath(
        path,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3 * density
            strokeJoin = Paint.Join.ROUND
        },
    )
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ARROW_COLOR })
    return bitmap
}
