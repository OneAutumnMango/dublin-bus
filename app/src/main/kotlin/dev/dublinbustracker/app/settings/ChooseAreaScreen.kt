package dev.dublinbustracker.app.settings

import android.graphics.PointF
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.dublinbustracker.app.map.OfflineMaps
import dev.dublinbustracker.app.map.rememberMapHandle
import dev.dublinbustracker.core.AREA_PRESETS
import dev.dublinbustracker.core.AreaPreset
import dev.dublinbustracker.core.GeoBounds
import dev.dublinbustracker.core.MAX_AREA_TILES
import dev.dublinbustracker.core.estimatedMegabytes
import dev.dublinbustracker.core.tileCount
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

private val FRAME_INSET = 28.dp

/**
 * Pick what to download: move and zoom the map so the area you want sits inside the frame
 * (or jump to a preset city), name it, and download.
 */
@Composable
fun ChooseAreaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handle = rememberMapHandle()
    val density = context.resources.displayMetrics.density
    val insetPx = FRAME_INSET.value * density

    var bounds by remember { mutableStateOf<GeoBounds?>(null) }
    var name by remember { mutableStateOf("My area") }
    var downloading by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(handle.style) {
        if (handle.style == null) return@LaunchedEffect
        val dublin = AREA_PRESETS.first { it.name == "Dublin" }
        handle.map?.cameraPosition = CameraPosition.Builder().target(LatLng(53.35, -6.26)).zoom(9.0).build()
        // A turned map would make the frame's corners a tilted box, so keep it facing north here.
        handle.map?.uiSettings?.isRotateGesturesEnabled = false
        name = dublin.name
        handle.map?.let { bounds = it.frameBounds(handle.view, insetPx) }
    }

    DisposableEffect(handle.map) {
        val map = handle.map ?: return@DisposableEffect onDispose { }
        val listener = MapLibreMap.OnCameraIdleListener { bounds = map.frameBounds(handle.view, insetPx) }
        map.addOnCameraIdleListener(listener)
        onDispose { map.removeOnCameraIdleListener(listener) }
    }

    val tiles = bounds?.let(::tileCount)
    val tooBig = tiles != null && tiles > MAX_AREA_TILES

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Choose an area", style = MaterialTheme.typography.titleMedium)
                Text("Fit what you want inside the frame", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onBack) { Text("Cancel") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AREA_PRESETS.forEach { preset ->
                AssistChip(
                    onClick = {
                        name = preset.name
                        handle.map?.easeTo(preset, insetPx.toInt())
                    },
                    label = { Text(preset.name) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { handle.view })
            // Purely visual: it has no touch handling, so panning and pinching reach the map beneath.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(FRAME_INSET)
                    .border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.small),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    tiles == null -> "Measuring the area..."
                    tooBig -> "That's too large to download - zoom in a little."
                    else -> "About ${estimatedMegabytes(tiles)} MB"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (tooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = bounds != null && tiles != null && !tooBig && !downloading,
                onClick = {
                    val area = bounds ?: return@Button
                    downloading = true
                    scope.launch {
                        OfflineMaps.download(context, name.trim().ifEmpty { "My area" }, area)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (downloading) "Starting..." else "Download") }
        }
    }
}

/** The land inside the on-screen frame, i.e. the map view less [insetPx] on every side. */
private fun MapLibreMap.frameBounds(view: View, insetPx: Float): GeoBounds? {
    if (view.width == 0 || view.height == 0) return null
    val topLeft = projection.fromScreenLocation(PointF(insetPx, insetPx))
    val bottomRight = projection.fromScreenLocation(PointF(view.width - insetPx, view.height - insetPx))
    return GeoBounds(
        south = bottomRight.latitude,
        west = topLeft.longitude,
        north = topLeft.latitude,
        east = bottomRight.longitude,
    )
}

private fun MapLibreMap.easeTo(preset: AreaPreset, paddingPx: Int) {
    val area = LatLngBounds.Builder()
        .include(LatLng(preset.bounds.south, preset.bounds.west))
        .include(LatLng(preset.bounds.north, preset.bounds.east))
        .build()
    easeCamera(CameraUpdateFactory.newLatLngBounds(area, paddingPx), 600)
}
