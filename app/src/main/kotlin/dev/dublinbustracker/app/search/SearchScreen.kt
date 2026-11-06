package dev.dublinbustracker.app.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.app.favourites.FavouriteStarButton
import dev.dublinbustracker.app.ui.AppListItem
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.FavouritesRepository
import dev.dublinbustracker.data.TransitApiClient

@Composable
fun SearchScreen(
    apiClient: TransitApiClient,
    favouritesRepository: FavouritesRepository,
    onStopSelected: (Stop) -> Unit,
) {
    val viewModel = viewModel<SearchViewModel>(
        factory = viewModelFactory { initializer { SearchViewModel(apiClient) } },
    )
    val query by viewModel.query.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val favouriteIds by favouritesRepository.favouriteIds.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text("Stop name or number") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            singleLine = true,
        )

        when (val current = state) {
            is SearchUiState.Idle -> Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(56.dp))
                Text(
                    "Search for a stop by name, or by the number on the pole.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            is SearchUiState.Searching -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            is SearchUiState.Error -> Column(modifier = Modifier.padding(16.dp)) {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::retry, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
            }
            is SearchUiState.Loaded -> if (current.stops.isEmpty()) {
                Text("No matching stops", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(current.stops, key = { it.id }) { stop ->
                        StopRow(
                            stop = stop,
                            isFavourite = stop.id in favouriteIds,
                            onToggleFavourite = { favouritesRepository.toggle(stop) },
                            onClick = { onStopSelected(stop) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StopRow(
    stop: Stop,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit,
) {
    AppListItem(
        onClick = onClick,
        headlineContent = { Text(stop.name) },
        supportingContent = {
            val routes = stop.routes.joinToString(", ")
            Text(if (routes.isEmpty()) "Stop ${stop.code}" else "Stop ${stop.code} · $routes")
        },
        trailingContent = { FavouriteStarButton(isFavourite = isFavourite, onToggle = onToggleFavourite) },
    )
}
