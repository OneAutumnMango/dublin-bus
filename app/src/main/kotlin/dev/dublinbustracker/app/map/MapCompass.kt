package dev.dublinbustracker.app.map

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.dublinbustracker.core.OkabeIto
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

private const val NORTH_LOCKED = "north_locked"
private val NORTH_RED = Color(OkabeIto.VERMILLION)
private val SOUTH_GREY = Color(0xFF9E9E9E)
private val LOCK_GREY = Color(0xFFBDBDBD)

/**
 * A compass showing how the map is turned. Tap to swing it back to north; double-tap to lock it
 * facing north (a small padlock appears) so it can't be rotated by accident, and again to unlock.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MapCompass(handle: MapHandle, modifier: Modifier = Modifier) {
    var bearing by remember { mutableFloatStateOf(0f) }
    val prefs = LocalContext.current.getSharedPreferences("map_ui", Context.MODE_PRIVATE)
    var locked by remember { mutableStateOf(prefs.getBoolean(NORTH_LOCKED, false)) }

    DisposableEffect(handle.map) {
        val map = handle.map ?: return@DisposableEffect onDispose { }
        map.uiSettings.isCompassEnabled = false // this one replaces the built-in
        bearing = map.cameraPosition.bearing.toFloat()
        val listener = MapLibreMap.OnCameraMoveListener { bearing = map.cameraPosition.bearing.toFloat() }
        map.addOnCameraMoveListener(listener)
        onDispose { map.removeOnCameraMoveListener(listener) }
    }

    LaunchedEffect(handle.map, locked) {
        val map = handle.map ?: return@LaunchedEffect
        map.uiSettings.isRotateGesturesEnabled = !locked
        if (locked) map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 300)
    }

    Surface(
        modifier = modifier
            .size(44.dp)
            .combinedClickable(
                onClick = { handle.map?.animateCamera(CameraUpdateFactory.bearingTo(0.0), 300) },
                onDoubleClick = {
                    locked = !locked
                    prefs.edit { putBoolean(NORTH_LOCKED, locked) }
                },
            ),
        shape = CircleShape,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Locked means it always faces north, so the needle has nothing to say: just the padlock.
            if (!locked) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    // The needle is turned against the map's rotation so the red end always points at true north.
                    rotate(degrees = -bearing) {
                        val middle = center
                        val half = size.width * 0.22f
                        drawPath(
                            Path().apply {
                                moveTo(middle.x, 0f)
                                lineTo(middle.x - half, middle.y)
                                lineTo(middle.x + half, middle.y)
                                close()
                            },
                            NORTH_RED,
                        )
                        drawPath(
                            Path().apply {
                                moveTo(middle.x, size.height)
                                lineTo(middle.x - half, middle.y)
                                lineTo(middle.x + half, middle.y)
                                close()
                            },
                            SOUTH_GREY,
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "North is locked",
                    tint = LOCK_GREY,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
