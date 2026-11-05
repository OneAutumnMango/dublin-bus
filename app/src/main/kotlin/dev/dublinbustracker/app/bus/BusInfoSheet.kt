package dev.dublinbustracker.app.bus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.BusInfo
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.TripDetails
import dev.dublinbustracker.core.describe
import dev.dublinbustracker.data.TransitApiClient
import java.time.ZoneId

private val LABEL_WIDTH = 112.dp

/**
 * Everything known about one bus, in a popup. [onViewOnMap] adds a button to open the bus's
 * route on the map; leave it null where the map is already what's on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusInfoSheet(
    tripId: String,
    contextStop: Stop?,
    arrival: Arrival?,
    apiClient: TransitApiClient,
    onViewOnMap: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val result by produceState<Result<TripDetails>?>(initialValue = null, tripId) {
        value = runCatching { apiClient.tripDetails(tripId) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val loaded = result
            when {
                loaded == null -> CircularProgressIndicator()
                loaded.isFailure -> Text(
                    loaded.exceptionOrNull()?.message ?: "Couldn't load this bus",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val info = loaded.getOrThrow().describe(contextStop, arrival, System.currentTimeMillis() / 1000, ZoneId.systemDefault())
                    BusInfoContent(info, onViewOnMap)
                }
            }
        }
    }
}

@Composable
private fun BusInfoContent(info: BusInfo, onViewOnMap: (() -> Unit)?) {
    Text(info.title, style = MaterialTheme.typography.titleLarge)
    if (onViewOnMap != null) {
        Button(onClick = onViewOnMap, modifier = Modifier.fillMaxWidth()) { Text("View route on map") }
    }

    info.rows.forEach { row ->
        Row {
            Text(
                row.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(LABEL_WIDTH),
            )
            Text(row.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }

    if (info.upcoming.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Next stops", style = MaterialTheme.typography.titleSmall)
        info.upcoming.forEach { stop ->
            Row {
                Text(stop.time, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                Text(stop.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (stop.note.isNotEmpty()) {
                    Text(stop.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
