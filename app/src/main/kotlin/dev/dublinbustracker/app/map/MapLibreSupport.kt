package dev.dublinbustracker.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * OpenFreeMap's "positron" - a pale, minimal vector basemap that keeps the route and stops
 * readable. Free, no API key (unlike the raster providers, which all turned out to be gated).
 */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/** The label font OpenFreeMap serves glyphs for; a font it lacks renders no text at all. */
val MAP_BOLD_FONT = arrayOf("Noto Sans Bold")

const val MAP_BLUE = 0xFF1565C0.toInt()

/** Owns a [MapView] and exposes the map and its loaded style, so a screen can add its own layers. */
class MapHandle(val view: MapView) {
    var map by mutableStateOf<MapLibreMap?>(null)
    var style by mutableStateOf<Style?>(null)
    private var destroyed = false

    fun destroy() {
        if (destroyed) return
        destroyed = true
        view.onPause()
        view.onStop()
        view.onDestroy()
    }
}

@Composable
fun rememberMapHandle(): MapHandle {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handle = remember {
        MapLibre.getInstance(context)
        val handle = MapHandle(MapView(context))
        handle.view.getMapAsync { map ->
            handle.map = map
            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { handle.style = it }
        }
        handle
    }
    DisposableEffect(lifecycle, handle) {
        // The lifecycle replays events up to its current state, so this also runs onCreate/onStart/onResume.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> handle.view.onCreate(null)
                Lifecycle.Event.ON_START -> handle.view.onStart()
                Lifecycle.Event.ON_RESUME -> handle.view.onResume()
                Lifecycle.Event.ON_PAUSE -> handle.view.onPause()
                Lifecycle.Event.ON_STOP -> handle.view.onStop()
                Lifecycle.Event.ON_DESTROY -> handle.destroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            handle.destroy()
        }
    }
    return handle
}

fun featureAt(lat: Double, lon: Double, vararg properties: Pair<String, Any>): Feature =
    Feature.fromGeometry(Point.fromLngLat(lon, lat)).also { feature ->
        properties.forEach { (key, value) ->
            when (value) {
                is String -> feature.addStringProperty(key, value)
                is Boolean -> feature.addBooleanProperty(key, value)
                is Number -> feature.addNumberProperty(key, value)
            }
        }
    }

fun collectionOf(features: List<Feature>): FeatureCollection = FeatureCollection.fromFeatures(features)
