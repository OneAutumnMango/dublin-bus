package dev.dublinbustracker.app.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import dev.dublinbustracker.core.OkabeIto
import dev.dublinbustracker.app.location.hasLocationPermission
import org.maplibre.android.MapLibre
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.SymbolLayer
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

const val MAP_BLUE = OkabeIto.BLUE

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

/** Draws the device's own position as a blue dot with an accuracy ring; does nothing until a location permission is held. */
@SuppressLint("MissingPermission")
fun MapLibreMap.showUserLocation(context: Context, style: Style) {
    if (!hasLocationPermission(context)) return
    runCatching {
        locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.NONE // show the dot without taking over the camera
        locationComponent.renderMode = RenderMode.NORMAL
    }
}

/** Bus markers are drawn at this fraction of their original size (about a third smaller). */
private const val BUS_MARKER_SCALE = 0.65f

/** Half the arrow's width; the nose reaches beyond it. */
private const val ARROW_HALF_WIDTH_DP = 16f * BUS_MARKER_SCALE

/** The round marker for a bus with no known heading, and the route number written on either kind. */
const val BUS_CIRCLE_RADIUS_DP = 15f * BUS_MARKER_SCALE
const val BUS_LABEL_SIZE_SP = 11f * BUS_MARKER_SCALE + 1f // text can't shrink as far as the marker and stay readable

/**
 * A bus as an arrowhead in its route's colour, drawn pointing up (the layer turns it to the bus's heading). The
 * bitmap's centre sits in the arrow's solid body so the route number, which stays upright, always lands on it.
 */
private fun busArrowBitmap(colour: Int, density: Float): Bitmap {
    val r = ARROW_HALF_WIDTH_DP * density
    val size = (2 * (1.4f * ARROW_HALF_WIDTH_DP + 3) * density).toInt() // room for the nose and outline at any turn
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val arrow = Path().apply {
        moveTo(c, c - 1.4f * r) // the nose
        lineTo(c + r, c + 0.7f * r)
        lineTo(c, c + 0.4f * r) // the notch in the tail
        lineTo(c - r, c + 0.7f * r)
        close()
    }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = android.graphics.Color.WHITE
        strokeWidth = 3 * density
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(arrow, outline)
    canvas.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
    return bitmap
}

/** The name of this colour's bus marker image, added to the style the first time it is asked for. */
private fun Style.busArrowImage(colour: Int, density: Float): String {
    val name = "bus-arrow-%06X".format(colour and 0xFFFFFF)
    if (getImage(name) == null) addImage(name, busArrowBitmap(colour, density))
    return name
}

/** The feature properties that draw a bus in [colour]: its marker image and, when known, the way it is heading. */
fun Style.busProperties(colour: Int, density: Float, bearing: Float?): Array<Pair<String, Any>> =
    listOfNotNull<Pair<String, Any>>("icon" to busArrowImage(colour, density), bearing?.let { "bearing" to it }).toTypedArray()

/** Buses that have a heading, drawn as the arrow turned to face it; the round layer beneath shows any that don't. */
fun busArrowLayer(id: String, source: String): SymbolLayer =
    SymbolLayer(id, source).withFilter(Expression.has("bearing")).withProperties(
        iconImage(Expression.get("icon")),
        iconRotate(Expression.get("bearing")),
        iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        iconAllowOverlap(true),
        iconIgnorePlacement(true),
    )

/** For the round layer: only the buses with no known heading. */
val NO_BEARING: Expression = Expression.not(Expression.has("bearing"))
