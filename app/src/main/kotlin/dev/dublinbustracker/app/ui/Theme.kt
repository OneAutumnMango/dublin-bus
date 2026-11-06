package dev.dublinbustracker.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** One corner radius for fields, chips and cards, instead of the 4dp / 8dp / pill mix the defaults give. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BusTrackerTheme(content: @Composable () -> Unit) {
    // Material's own baseline colours (the purple the app has always had); only the shapes are the app's own.
    MaterialTheme(colorScheme = lightColorScheme(), shapes = AppShapes, content = content)
}
