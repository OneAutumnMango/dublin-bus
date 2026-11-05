package dev.dublinbustracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.app.favourites.FavouriteStarButton
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.core.display
import dev.dublinbustracker.data.FavouritesRepository
import dev.dublinbustracker.data.TransitApiClient
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
fun StopDetailScreen(
    stop: Stop,
    apiClient: TransitApiClient,
    favouritesRepository: FavouritesRepository,
    onBack: () -> Unit,
    onViewRoute: (Arrival) -> Unit,
) {
    val viewModel = viewModel<StopDetailViewModel>(
        key = stop.id,
        factory = viewModelFactory { initializer { StopDetailViewModel(stop.id, apiClient) } },
    )
    val uiState by viewModel.uiState.collectAsState()
    val routeFilter by viewModel.routeFilter.collectAsState()
    val favouriteIds by favouritesRepository.favouriteIds.collectAsState()

    var autoRefresh by remember { mutableStateOf(true) }
    LaunchedEffect(stop.id, autoRefresh) {
        while (autoRefresh) {
            delay(ARRIVALS_REFRESH_INTERVAL_MS)
            viewModel.refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FavouriteStarButton(
                isFavourite = stop.id in favouriteIds,
                onToggle = { favouritesRepository.toggle(stop) },
            )
            TextButton(onClick = onBack) { Text("Back") }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (autoRefresh) "Stop ${stop.code}" else "Stop ${stop.code} · auto-refresh paused",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            IconButton(onClick = { autoRefresh = !autoRefresh }) {
                Icon(
                    if (autoRefresh) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (autoRefresh) "Pause auto-refresh" else "Resume auto-refresh",
                    // Tinted while paused so it's obvious the list is no longer updating itself.
                    tint = if (autoRefresh) LocalContentColor.current else MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (stop.routes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = routeFilter == null,
                    onClick = { viewModel.setRouteFilter(null) },
                    label = { Text("All") },
                )
                stop.routes.forEach { route ->
                    FilterChip(
                        selected = routeFilter == route,
                        onClick = { viewModel.setRouteFilter(route.takeIf { routeFilter != route }) },
                        label = { Text(route) },
                    )
                }
            }
        }

        when (val state = uiState) {
            is ArrivalsUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            is ArrivalsUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is ArrivalsUiState.Loaded -> if (state.arrivals.isEmpty()) {
                Text("No upcoming arrivals", modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(state.arrivals) { arrival ->
                        ArrivalRow(arrival, onClick = { onViewRoute(arrival) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/** One compact line-pair per bus: the time it's due in large type, then where it's going and how late. */
@Composable
private fun ArrivalRow(arrival: Arrival, onClick: () -> Unit) {
    val shown = arrival.display(System.currentTimeMillis() / 1000, ZoneId.systemDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Greyed when the bus may already have gone.
            .alpha(if (shown.muted) 0.5f else 1f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            shown.headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(ARRIVAL_HEADLINE_WIDTH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(shown.destinationLine, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (shown.detailLine.isNotEmpty()) {
                Text(
                    shown.detailLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Filled.Map,
            contentDescription = "Route map",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private val ARRIVAL_HEADLINE_WIDTH = 80.dp
