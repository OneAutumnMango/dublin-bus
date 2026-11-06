package dev.dublinbustracker.app.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.dublinbustracker.app.map.MAP_BLUE
import dev.dublinbustracker.app.map.collectionOf
import dev.dublinbustracker.app.map.featureAt
import dev.dublinbustracker.app.map.rememberMapHandle
import dev.dublinbustracker.app.ui.ScreenHeader
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource

private const val PICK_SOURCE = "picked"

/** Tap the map to drop a pin where the map should open, then save. */
@Composable
fun ChooseLocationScreen(initial: LatLng?, onSave: (LatLng) -> Unit, onBack: () -> Unit) {
    val handle = rememberMapHandle()
    var picked by remember { mutableStateOf(initial) }

    BackHandler(onBack = onBack)

    LaunchedEffect(handle.style) {
        val style = handle.style ?: return@LaunchedEffect
        val start = initial ?: LatLng(53.3498, -6.2603)
        handle.map?.cameraPosition = CameraPosition.Builder().target(start).zoom(if (initial != null) 14.0 else 11.0).build()
        style.addSource(GeoJsonSource(PICK_SOURCE, collectionOf(emptyList())))
        style.addLayer(
            CircleLayer("picked-pin", PICK_SOURCE).withProperties(
                circleColor(MAP_BLUE), circleStrokeColor(android.graphics.Color.WHITE), circleStrokeWidth(3f), circleRadius(10f),
            ),
        )
    }
    LaunchedEffect(handle.style, picked) {
        val point = picked
        handle.style?.getSourceAs<GeoJsonSource>(PICK_SOURCE)?.setGeoJson(
            collectionOf(listOfNotNull(point?.let { featureAt(it.latitude, it.longitude) })),
        )
    }
    DisposableEffect(handle.map) {
        val map = handle.map ?: return@DisposableEffect onDispose { }
        val listener = MapLibreMap.OnMapClickListener { picked = it; true }
        map.addOnMapClickListener(listener)
        onDispose { map.removeOnMapClickListener(listener) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Choose a start position", subtitle = "Tap the map to place the pin", onBack = onBack)
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { handle.view })
        Button(
            enabled = picked != null,
            onClick = { picked?.let(onSave) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Use this position") }
    }
}
