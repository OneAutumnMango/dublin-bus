package dev.dublinbustracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stop.name, style = MaterialTheme.typography.titleLarge)
                Text("Stop ${stop.code}")
            }
            Row {
                FavouriteStarButton(
                    isFavourite = stop.id in favouriteIds,
                    onToggle = { favouritesRepository.toggle(stop) },
                )
                TextButton(onClick = onBack) { Text("Back") }
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

        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::refresh) { Text("Refresh") }
            OutlinedButton(onClick = { autoRefresh = !autoRefresh }) {
                Text(if (autoRefresh) "Pause auto-refresh" else "Resume auto-refresh")
            }
        }

        when (val state = uiState) {
            is ArrivalsUiState.Loading -> CircularProgressIndicator()
            is ArrivalsUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is ArrivalsUiState.Loaded -> if (state.arrivals.isEmpty()) {
                Text("No upcoming arrivals")
            } else {
                LazyColumn {
                    items(state.arrivals) { arrival -> ArrivalRow(arrival, onClick = { onViewRoute(arrival) }) }
                }
            }
        }
    }
}

@Composable
private fun ArrivalRow(arrival: Arrival, onClick: () -> Unit) {
    val shown = arrival.display(System.currentTimeMillis() / 1000, ZoneId.systemDefault())
    ListItem(
        // Greyed when the bus may already have gone.
        modifier = Modifier.clickable(onClick = onClick).alpha(if (shown.muted) 0.5f else 1f),
        headlineContent = { Text(shown.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Column {
                Text(shown.destinationLine, style = MaterialTheme.typography.bodyMedium)
                if (shown.detailLine.isNotEmpty()) Text(shown.detailLine, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = { Text("Route map", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) },
    )
}
