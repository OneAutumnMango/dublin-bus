package dev.dublinbustracker.app.map

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.dublinbustracker.core.Route
import dev.dublinbustracker.core.legendFor
import dev.dublinbustracker.core.trackingColour

/**
 * Route-following controls: a box that suggests routes as you type, the routes followed, and a
 * dropdown listing each direction of them (e.g. "4 to Heuston Station") to switch on or off.
 * An arrow folds it down to a single slim row so the map gets the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var expanded by rememberSaveable { mutableStateOf(true) }
    val add = { name: String ->
        onAdd(name)
        text = ""
        onQueryChanged("")
    }

    // Chips are laid out at their drawn size rather than padded out to a 48dp touch target; that padding
    // was most of what made the panel so tall.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!expanded) {
                CollapsedSummary(state, onExpand = { expanded = true }, modeToggle = modeToggle)
                return@Column
            }

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
                IconButton(onClick = { add(text) }, enabled = !state.busy, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add route")
                }
                modeToggle()
            }

            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            if (state.routes.isNotEmpty()) {
                val directions = legendFor(state.routes.map { it.name }, state.vehicles, state.destinations)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // One scrolling row instead of three. The two controls come first so they are always in view, and the route chips scroll after them.
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (directions.isNotEmpty()) {
                            val shown = directions.count { (it.route to it.directionId) !in state.hidden }
                            Box {
                                // A chip, like the Stops chip beside it, so the two are the same height and shape.
                                AssistChip(
                                    onClick = { directionsOpen = true },
                                    label = { Text("Directions · $shown of ${directions.size}") },
                                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                                )
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
                    IconButton(onClick = { expanded = false }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Hide route controls")
                    }
                }
            }
        }
    }
}

/** The panel folded down: just which routes are being followed, an arrow to open it, and the mode toggle. */
@Composable
private fun CollapsedSummary(state: TrackingUiState, onExpand: () -> Unit, modeToggle: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.routes.isEmpty()) {
                Text("No routes followed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.routes.forEach { route ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(Color(trackingColour(route.colourIndex, "0")))
                    Text(route.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        IconButton(onClick = onExpand, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Show route controls")
        }
        modeToggle()
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
