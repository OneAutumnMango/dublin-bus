package dev.dublinbustracker.app.favourites

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.launch
import dev.dublinbustracker.app.widget.refreshAllWidgets
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.dublinbustracker.app.location.LOCATION_PERMISSIONS
import dev.dublinbustracker.app.location.hasLocationPermission
import dev.dublinbustracker.app.location.requestCurrentLocation
import dev.dublinbustracker.app.ui.AppListItem
import dev.dublinbustracker.core.LatLon
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.distanceText
import dev.dublinbustracker.core.headingsText
import dev.dublinbustracker.core.sortedByDistance
import dev.dublinbustracker.data.FavouriteStop
import dev.dublinbustracker.data.FavouritesRepository
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val PREFS = "favourites_ui"
private const val NEAREST_FIRST = "nearest_first"

@Composable
fun FavouritesScreen(
    apiClient: TransitApiClient,
    favouritesRepository: FavouritesRepository,
    onStopSelected: (Stop) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val favourites by favouritesRepository.favourites.collectAsState()

    var nearestFirst by remember { mutableStateOf(prefs.getBoolean(NEAREST_FIRST, false)) }
    var here by remember { mutableStateOf<LatLon?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<FavouriteStop?>(null) }
    var hidingBuses by remember { mutableStateOf<FavouriteStop?>(null) }
    val scope = rememberCoroutineScope()
    // Widgets already on the home screen should pick up a change straight away rather than at their next poll.
    fun widgetsChanged() {
        scope.launch { runCatching { refreshAllWidgets(context) } }
    }

    fun setNearestFirst(on: Boolean) {
        nearestFirst = on
        prefs.edit { putBoolean(NEAREST_FIRST, on) }
    }

    fun locate() {
        status = "Finding your location..."
        requestCurrentLocation(
            context,
            onUnavailable = { status = "Couldn't get your location - is location switched on?" },
        ) { fix ->
            here = LatLon(fix.latitude, fix.longitude)
            status = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) {
            locate()
        } else {
            setNearestFirst(false)
            status = "Location permission is needed to sort by distance"
        }
    }

    // Looked up afresh each time the sort is switched on or the tab is opened, so it follows you around.
    LaunchedEffect(nearestFirst) {
        if (!nearestFirst) {
            status = null
            return@LaunchedEffect
        }
        if (hasLocationPermission(context)) locate() else permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    // Each visit asks the server for every favourite's current details: a name for entries from the
    // old id-only format, and which way its routes head (which can be corrected server-side).
    // Each stop is asked once per visit. Every change republishes the list and so restarts this
    // effect, which is why a stop only counts as asked once its request is done - a restart picks
    // up the rest.
    val asked = remember { mutableSetOf<String>() }
    LaunchedEffect(favourites) {
        favourites.filter { it.id !in asked }.forEach { favourite ->
            val stop = runCatching { apiClient.stop(favourite.id) }.getOrNull()
            // runCatching swallows cancellation too; without this a restart would mark a stop as asked that never was.
            currentCoroutineContext().ensureActive()
            asked += favourite.id
            if (stop != null) favouritesRepository.updateDetails(stop)
        }
    }

    if (favourites.isEmpty()) {
        Text(
            "No favourites yet - tap the star on a stop to add it.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    val origin = here
    val rows = if (nearestFirst && origin != null) {
        // A favourite whose position isn't known yet can't be placed, so it goes last.
        sortedByDistance(favourites, origin) { if (it.needsDetails || (it.lat == 0.0 && it.lon == 0.0)) null else LatLon(it.lat, it.lon) }
    } else {
        favourites.map { it to null }
    }

    renaming?.let { target ->
        var draft by remember(target.id) { mutableStateOf(target.nickname) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Name this stop") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text(target.name.ifBlank { target.id }) },
                    supportingText = { Text("Leave blank to use the official name") },
                )
            },
            confirmButton = {
                TextButton(onClick = { favouritesRepository.rename(target.id, draft); renaming = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    hidingBuses?.let { target ->
        HideBusesDialog(
            apiClient = apiClient,
            favourite = target,
            onSave = {
                favouritesRepository.setHiddenRoutes(target.id, it)
                hidingBuses = null
                widgetsChanged()
            },
            onDismiss = { hidingBuses = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterChip(
            selected = nearestFirst,
            onClick = { setNearestFirst(!nearestFirst) },
            label = { Text("Nearest first") },
            leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(rows, key = { it.first.id }) { (favourite, meters) ->
                AppListItem(
                    onClick = { onStopSelected(favourite.toStop()) },
                    headlineContent = { Text(favourite.nickname.ifBlank { favourite.name.ifBlank { favourite.id } }) },
                    supportingContent = {
                        Column {
                            Text(
                                listOfNotNull(
                                    favourite.name.takeIf { favourite.nickname.isNotBlank() },
                                    "Stop ${favourite.code.ifBlank { "-" }}",
                                    meters?.let(::distanceText),
                                ).joinToString(" · "),
                            )
                            widgetNote(favourite)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (favourite.headings.isNotEmpty()) {
                                // e.g. "4 (W)  L25 (W)  L27 (SW)": each bus number and the way it goes from this stop.
                                Text(
                                    headingsText(favourite.toStop().headings),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        FavouriteMenu(
                            favourite,
                            onRename = { renaming = favourite },
                            onToggleExcluded = {
                                favouritesRepository.setExcludedFromWidgets(favourite.id, !favourite.excludedFromWidgets)
                                widgetsChanged()
                            },
                            onChooseBuses = { hidingBuses = favourite },
                        )
                        FavouriteStarButton(isFavourite = true, onToggle = { favouritesRepository.remove(favourite.id) })
                    },
                )
            }
        }
    }
}

/** What is switched off for widgets on this favourite, if anything. */
private fun widgetNote(favourite: FavouriteStop): String? = listOfNotNull(
    "Not shown in widgets".takeIf { favourite.excludedFromWidgets },
    "Hiding ${favourite.hiddenRoutes.sorted().joinToString(", ")} in widgets".takeIf { favourite.hiddenRoutes.isNotEmpty() },
).joinToString(" · ").ifEmpty { null }

/** The three dots on a favourite: rename it, and what widgets may do with it. */
@Composable
private fun FavouriteMenu(favourite: FavouriteStop, onRename: () -> Unit, onToggleExcluded: () -> Unit, onChooseBuses: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Exclude from widgets") },
                leadingIcon = { Checkbox(checked = favourite.excludedFromWidgets, onCheckedChange = null) },
                onClick = { open = false; onToggleExcluded() },
            )
            DropdownMenuItem(
                text = { Text("Hide buses in widgets...") },
                onClick = { open = false; onChooseBuses() },
            )
        }
    }
}

/** Pick the buses that never show in widgets for a favourite, from the buses that serve its stop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HideBusesDialog(apiClient: TransitApiClient, favourite: FavouriteStop, onSave: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var hidden by remember(favourite.id) { mutableStateOf(favourite.hiddenRoutes) }
    val routes by produceState(favourite.headings.map { it.route }, favourite.id) {
        value = runCatching { apiClient.stop(favourite.id).routes }.getOrDefault(value)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hide buses in widgets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ticked buses never show in a widget for this stop.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A bus already hidden stays listed even if the stop no longer reports it, so it can be switched back on.
                    (routes + hidden).distinct().forEach { route ->
                        FilterChip(
                            selected = route in hidden,
                            onClick = { hidden = if (route in hidden) hidden - route else hidden + route },
                            label = { Text(route) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(hidden) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
