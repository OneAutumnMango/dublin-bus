package dev.dublinbustracker.app.map

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

private const val PREFS = "map_ui"

/** A map option that is remembered between visits: the current value and a way to change it. */
class MapFlag(private val context: Context, private val key: String, initial: Boolean) {
    var value by mutableStateOf(initial)
        private set

    fun set(on: Boolean) {
        value = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(key, on) }
    }
}

@Composable
fun rememberMapFlag(key: String, default: Boolean): MapFlag {
    val context = LocalContext.current
    return remember(key) { MapFlag(context, key, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, default)) }
}

/** The map's three-dot menu, a round button in the style of the compass; [content] is its items, given a way to close the menu. */
@Composable
fun MapMenu(modifier: Modifier = Modifier, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(modifier = Modifier.size(44.dp), shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Map options") }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp)) {
            content { open = false }
        }
    }
}

/** One on/off option in a [MapMenu]; the menu stays open so several can be changed. */
@Composable
fun MapToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
    )
}
