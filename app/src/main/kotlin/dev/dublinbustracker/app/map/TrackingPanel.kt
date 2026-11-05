package dev.dublinbustracker.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.dublinbustracker.core.Route
import dev.dublinbustracker.core.legendFor
import dev.dublinbustracker.core.trackingColour

/**
 * Route-following controls: a box that suggests routes as you type, the routes followed, and a
 * dropdown listing each direction of them (e.g. "4 to Heuston Station") to switch on or off.
 */
@Composable
fun TrackingPanel(
    state: TrackingUiState,
    suggestions: List<Route>,
    onQueryChanged: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onToggleDirection: (route: String, directionId: String) -> Unit,
    onShowStops: (Boolean) -> Unit,
    /** The mode toggle, which sits at the end of the first row so the panel needs no row of its own for it. */
    modeToggle: @Composable () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var directionsOpen by remember { mutableStateOf(false) }
    val add = { name: String ->
        onAdd(name)
        text = ""
        onQueryChanged("")
    }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onQueryChanged(it)
                    },
                    placeholder = { Text("Add a route, e.g. S8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add(text) }),
                    modifier = Modifier.fillMaxWidth(),
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
            IconButton(onClick = { add(text) }, enabled = !state.busy) { Icon(Icons.Filled.Add, contentDescription = "Add route") }
            modeToggle()
        }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (state.routes.isNotEmpty()) {
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

            val directions = legendFor(state.routes.map { it.name }, state.vehicles, state.destinations)
            if (directions.isNotEmpty()) {
                val shown = directions.count { (it.route to it.directionId) !in state.hidden }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { directionsOpen = true }) {
                            Text("Directions · $shown of ${directions.size}")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = directionsOpen, onDismissRequest = { directionsOpen = false }) {
                            directions.forEach { entry ->
                                val index = state.routes.first { it.name == entry.route }.colourIndex
                                DropdownMenuItem(
                                    leadingIcon = { Checkbox(checked = (entry.route to entry.directionId) !in state.hidden, onCheckedChange = null) },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Dot(Color(trackingColour(index, entry.directionId)))
                                            Text("${entry.route} to ${entry.headsign.ifBlank { "direction ${entry.directionId}" }}")
                                        }
                                    },
                                    // The menu stays open, so several can be switched without reopening it.
                                    onClick = { onToggleDirection(entry.route, entry.directionId) },
                                )
                            }
                        }
                    }
                    FilterChip(selected = state.showStops, onClick = { onShowStops(!state.showStops) }, label = { Text("Stops") })
                }
            }
        }
    }
}

@Composable
private fun Dot(colour: Color) {
    Box(modifier = Modifier.size(12.dp).background(colour, CircleShape))
}
