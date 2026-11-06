package dev.dublinbustracker.app.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.data.RouteProfile

/**
 * Saved route selections: tap one to bring it back, bin the ones you no longer want, or name what is on the map
 * now and keep it. Saving under a name already used replaces that profile.
 */
@Composable
fun RouteProfilesDialog(
    profiles: List<RouteProfile>,
    followedCount: Int,
    onLoad: (RouteProfile) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (RouteProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val replaces = profiles.any { it.name.equals(name.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Route profiles") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (profiles.isEmpty()) {
                    Text("Nothing saved yet. Follow some routes, then name them below.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(profiles, key = { it.name }) { profile ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onLoad(profile) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        profile.routes.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onDelete(profile) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${profile.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name the routes on the map") },
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
