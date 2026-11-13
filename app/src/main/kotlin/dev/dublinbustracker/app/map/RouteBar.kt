package dev.dublinbustracker.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.dublinbustracker.core.Route
import dev.dublinbustracker.core.directionCompass
import dev.dublinbustracker.core.legendFor
import dev.dublinbustracker.core.trackingColour

/**
 * The top of the map: a box that suggests routes as you type, the routes being followed (tap a chip's X to drop one),
 * the button that refreshes what is live on the map, and the one that opens the map's options. (A route is added with the
 * keyboard's done key or by tapping a suggestion.) Everything else lives in the options sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteBar(
    state: TrackingUiState,
    suggestions: List<Route>,
    onQueryChanged: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOptions: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val add = { name: String ->
        onAdd(name)
        text = ""
        onQueryChanged("")
    }

    // Chips are laid out at their drawn size rather than padded out to a 48dp touch target; that padding
    // was most of what made the bar so tall.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CompactField(
                        value = text,
                        onValueChange = {
                            text = it
                            onQueryChanged(it)
                        },
                        placeholder = "Add a route, e.g. S8",
                        onDone = { add(text) },
                    )
                    // Not focusable, so the keyboard stays up and typing carries on while choices are showing.
                    DropdownMenu(
                        expanded = suggestions.isNotEmpty() && text.isNotBlank(),
                        onDismissRequest = { onQueryChanged("") },
                        properties = PopupProperties(focusable = false),
                    ) {
                        suggestions.forEach { route ->
                            DropdownMenuItem(
                                text = { Text(listOf(route.shortName, route.longName).filter { it.isNotBlank() }.joinToString("  "), maxLines = 1) },
                                onClick = { add(route.shortName) },
                            )
                        }
                    }
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).semantics { contentDescription = "Refreshing the map" },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh the map")
                    }
                }
                IconButton(onClick = onOptions, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = "Map options")
                }
            }

            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            // The chips are the routes drawn on the map, which only happens while following; otherwise they'd name routes that aren't shown.
            if (state.enabled && state.routes.isNotEmpty()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.routes.forEach { route ->
                        InputChip(
                            selected = false,
                            onClick = { onRemove(route.name) },
                            label = { Text(route.name) },
                            leadingIcon = { Dot(Color(trackingColour(route.colourIndex, "0"))) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove ${route.name}", modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
    }
}

/** Each direction of each followed route (e.g. "4 to Heuston Station (NW)") with a tick to switch it on or off. */
@Composable
fun DirectionsList(state: TrackingUiState, onToggleDirection: (route: String, directionId: String) -> Unit) {
    val directions = legendFor(state.routes.map { it.name }, state.vehicles, state.destinations)
    if (directions.isEmpty()) {
        Text("Directions appear once the routes have loaded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    directions.forEach { entry ->
        val index = state.routes.first { it.name == entry.route }.colourIndex
        val compass = directionCompass(state.stops[entry.route]?.get(entry.directionId).orEmpty())?.let { " ($it)" }.orEmpty()
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleDirection(entry.route, entry.directionId) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = (entry.route to entry.directionId) !in state.hidden, onCheckedChange = null, modifier = Modifier.padding(vertical = 4.dp))
            Dot(Color(trackingColour(index, entry.directionId)))
            Text("${entry.route} to ${entry.headsign.ifBlank { "direction ${entry.directionId}" }}$compass", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A slim route box: 40dp tall, where a standard outlined text field is a minimum of 56dp. */
@Composable
private fun CompactField(value: String, onValueChange: (String) -> Unit, placeholder: String, onDone: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().height(40.dp),
        decorationBox = { field ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                field()
            }
        },
    )
}

@Composable
private fun Dot(colour: Color) {
    Box(modifier = Modifier.size(12.dp).background(colour, CircleShape))
}
