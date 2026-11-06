package dev.dublinbustracker.app.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.app.location.LOCATION_PERMISSIONS
import dev.dublinbustracker.app.location.hasLocationPermission
import dev.dublinbustracker.app.map.MapStart
import dev.dublinbustracker.app.map.MapStartStore
import dev.dublinbustracker.app.map.StartMode
import dev.dublinbustracker.app.ui.SettingsSection

/** Where the Map tab opens. */
@Composable
fun MapStartSection(store: MapStartStore, onChooseStart: () -> Unit) {
    val context = LocalContext.current
    var start by remember { mutableStateOf(store.load()) }
    fun choose(next: MapStart) {
        start = next
        store.save(next)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    SettingsSection(
        title = "Map start position",
        description = "Where the map opens. Your location needs the location permission; without it the map opens on the city centre.",
    ) {
        val options = listOf(
            StartMode.CITY_CENTRE to "Dublin city centre",
            StartMode.MY_LOCATION to "My location (GPS)",
            StartMode.CHOSEN to if (start.mode == StartMode.CHOSEN) "A chosen spot (%.4f, %.4f)".format(start.lat, start.lon) else "A chosen spot",
        )
        androidx.compose.foundation.layout.Column(modifier = Modifier.selectableGroup()) {
            options.forEach { (mode, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = start.mode == mode,
                        role = Role.RadioButton,
                        onClick = {
                            when {
                                mode == StartMode.CHOSEN && start.lat == 0.0 && start.lon == 0.0 -> onChooseStart()
                                else -> choose(start.copy(mode = mode))
                            }
                            if (mode == StartMode.MY_LOCATION && !hasLocationPermission(context)) permissionLauncher.launch(LOCATION_PERMISSIONS)
                        },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = start.mode == mode, onClick = null)
                    Text(label, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                }
            }
        }
        OutlinedButton(onClick = onChooseStart) { Text("Choose a spot on the map") }
    }
}
