package dev.dublinbustracker.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.core.directionCompass
import dev.dublinbustracker.data.RouteProfile
import dev.dublinbustracker.data.TransitApiClient
import dev.dublinbustracker.data.summary

/**
 * Saved route selections: tap one to bring it back, bin the ones you no longer want, or name what is on the map
 * now and keep it. Saving under a name already used replaces that profile.
 */
@Composable
fun ProfilesGroup(
    apiClient: TransitApiClient,
    profiles: List<RouteProfile>,
    followedCount: Int,
    onLoad: (RouteProfile) -> Unit,
    onSave: (String) -> Unit,
    onRename: (RouteProfile, String) -> Boolean,
    onDelete: (RouteProfile) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<RouteProfile?>(null) }
    // Which way a route's one switched-on direction heads is worked out from its stops, asked for as the list is shown.
    val headings = remember { mutableStateMapOf<Pair<String, String>, String>() }
    LaunchedEffect(profiles) {
        profiles.forEach { profile ->
            profile.routes.forEach { route ->
                val off = profile.hidden.filter { it.route == route.name }.map { it.direction }.toSet()
                val on = listOf("0", "1").filterNot { it in off }
                val key = on.singleOrNull()?.let { route.name to it } ?: return@forEach
                if (key in headings) return@forEach
                runCatching { apiClient.routeStops(route.name, key.second) }.getOrNull()
                    ?.let(::directionCompass)
                    ?.let { headings[key] = it }
            }
        }
    }
    val replaces = profiles.any { it.name.equals(name.trim(), ignoreCase = true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (profiles.isEmpty()) {
            Text("Nothing saved yet. Follow some routes, then name them below.", style = MaterialTheme.typography.bodyMedium)
        }
        profiles.forEach { profile ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onLoad(profile) }, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        profile.summary { route, direction -> headings[route to direction] },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { renaming = profile }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Rename ${profile.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(profile) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${profile.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Profile name") },
            supportingText = {
                Text(
                    when {
                        followedCount == 0 -> "Add some routes to the map first"
                        replaces -> "Replaces the saved profile of that name"
                        else -> "$followedCount route${if (followedCount == 1) "" else "s"}, and which directions are on"
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = name.isNotBlank() && followedCount > 0,
            onClick = { onSave(name.trim()); name = "" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (replaces) "Replace" else "Save") }
    }

    renaming?.let { target ->
        var draft by remember(target.name) { mutableStateOf(target.name) }
        val taken = profiles.any { it.name != target.name && it.name.equals(draft.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    isError = taken,
                    supportingText = { if (taken) Text("Another profile already has that name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank() && !taken,
                    onClick = { if (onRename(target, draft)) renaming = null },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}
