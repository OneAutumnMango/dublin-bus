package dev.dublinbustracker.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.app.map.DownloadedArea
import dev.dublinbustracker.app.map.OfflineMaps
import dev.dublinbustracker.app.ui.SettingsSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PROGRESS_POLL_MS = 2_000L

/** The areas stored for offline use, with a way to add or remove them. */
@Composable
fun OfflineMapsSection(onChooseArea: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var areas by remember { mutableStateOf<List<DownloadedArea>>(emptyList()) }

    // Polled while the section is on screen, so a download's progress ticks along.
    LaunchedEffect(Unit) {
        while (true) {
            areas = OfflineMaps.list(context)
            delay(PROGRESS_POLL_MS)
        }
    }

    SettingsSection(
        title = "Offline maps",
        description = "Download areas so the map opens quickly and still shows without a signal.",
    ) {
        areas.forEach { area ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(area.name, style = MaterialTheme.typography.bodyLarge)
                    Text(statusText(area), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            OfflineMaps.delete(context, area.id)
                            areas = OfflineMaps.list(context)
                        }
                    },
                ) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${area.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (areas.isEmpty()) {
            Text("Nothing downloaded yet.", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(onClick = onChooseArea) { Text("Download an area") }
    }
}

private fun statusText(area: DownloadedArea): String {
    val megabytes = "%.1f MB".format(area.bytes / 1_000_000.0)
    return if (area.complete) "Ready · $megabytes" else "Downloading ${area.percent}% · $megabytes"
}
