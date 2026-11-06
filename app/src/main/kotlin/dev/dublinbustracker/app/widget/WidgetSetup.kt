package dev.dublinbustracker.app.widget

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.dublinbustracker.app.AppContainer
import dev.dublinbustracker.app.location.LOCATION_PERMISSIONS
import dev.dublinbustracker.app.location.hasBackgroundLocationPermission
import dev.dublinbustracker.app.location.hasLocationPermission
import dev.dublinbustracker.app.search.SearchScreen
import dev.dublinbustracker.app.ui.ScreenHeader
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.WidgetConfig
import dev.dublinbustracker.data.WidgetMode

private enum class Step { MODE, PICK_STOP, ROUTES, FAVOURITES }

/** Setting up a widget: choose what it shows, then the details for that choice. */
@Composable
fun WidgetSetup(container: AppContainer, onDone: (WidgetConfig) -> Unit, onCancel: () -> Unit) {
    var step by remember { mutableStateOf(Step.MODE) }
    var stop by remember { mutableStateOf<Stop?>(null) }

    BackHandler(enabled = step != Step.MODE) {
        step = if (step == Step.ROUTES) Step.PICK_STOP else Step.MODE
    }

    when (step) {
        Step.MODE -> ModeChoice(
            onOneStop = { step = Step.PICK_STOP },
            onNearest = { step = Step.FAVOURITES },
            onCancel = onCancel,
        )
        Step.PICK_STOP -> Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Choose a stop", onBack = { step = Step.MODE })
            SearchScreen(
                container.apiClient,
                container.favouritesRepository,
                container.recentStops,
                onStopSelected = {
                    stop = it
                    step = Step.ROUTES
                },
            )
        }
        Step.ROUTES -> stop?.let { chosen ->
            RouteChoice(container, chosen, onBack = { step = Step.PICK_STOP }) { routes ->
                onDone(WidgetConfig(WidgetMode.SINGLE_STOP, chosen.id, routes = routes))
            }
        }
        Step.FAVOURITES -> FavouriteChoice(container, onBack = { step = Step.MODE }) { excluded ->
            onDone(WidgetConfig(WidgetMode.NEAREST_FAVOURITE, excludedStopIds = excluded))
        }
    }
}

@Composable
private fun ModeChoice(onOneStop: () -> Unit, onNearest: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Set up the widget", onBack = onCancel)
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ModeCard("One stop", "Pick a stop to show, and if you like only some of its buses.", onOneStop)
            ModeCard("Nearest favourite", "Shows whichever of your favourite stops is closest to you. You can leave some out.", onNearest)
        }
    }
}

@Composable
private fun ModeCard(title: String, description: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteChoice(container: AppContainer, stop: Stop, onBack: () -> Unit, onDone: (Set<String>) -> Unit) {
    var chosen by remember { mutableStateOf(setOf<String>()) }
    // A stop opened from the recent list doesn't carry its buses, so ask for them.
    val routes by produceState(stop.routes, stop.id) {
        value = runCatching { container.apiClient.stop(stop.id).routes }.getOrDefault(stop.routes)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Which buses?", subtitle = stop.name, onBack = onBack)
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Leave them all off to show every bus at this stop.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                routes.forEach { route ->
                    FilterChip(
                        selected = route in chosen,
                        onClick = { chosen = if (route in chosen) chosen - route else chosen + route },
                        label = { Text(route) },
                    )
                }
            }
            Button(onClick = { onDone(chosen) }, modifier = Modifier.fillMaxWidth()) { Text("Add widget") }
        }
    }
}

@Composable
private fun FavouriteChoice(container: AppContainer, onBack: () -> Unit, onDone: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val favourites by container.favouritesRepository.favourites.collectAsState()
    var excluded by remember { mutableStateOf(setOf<String>()) }
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick++ }
    // Read again whenever the permission screens hand control back.
    val hasForeground = remember(permissionTick) { hasLocationPermission(context) }
    val hasBackground = remember(permissionTick) { hasBackgroundLocationPermission(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Nearest favourite", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "The widget shows the closest of these. Untick any you never want it to pick.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!hasForeground || !hasBackground) {
                Text(
                    if (!hasForeground) {
                        "It needs your location to know which is closest."
                    } else {
                        "For it to follow you while the app is closed, set the app's location to \"Allow all the time\". Until then it uses the last place the app saw you."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        if (!hasForeground) {
                            launcher.launch(LOCATION_PERMISSIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        }
                    },
                ) { Text(if (!hasForeground) "Allow location" else "Open app settings") }
            }
        }

        if (favourites.isEmpty()) {
            Text(
                "You have no favourite stops yet. Star a stop in the app first.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(favourites, key = { it.id }) { favourite ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            excluded = if (favourite.id in excluded) excluded - favourite.id else excluded + favourite.id
                        }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = favourite.id !in excluded, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Column {
                            Text(favourite.nickname.ifBlank { favourite.name.ifBlank { favourite.id } }, style = MaterialTheme.typography.bodyLarge)
                            Text("Stop ${favourite.code.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Button(
            enabled = favourites.any { it.id !in excluded },
            onClick = { onDone(excluded) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Add widget") }
    }
}
