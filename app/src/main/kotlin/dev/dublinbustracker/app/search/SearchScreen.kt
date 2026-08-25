package dev.dublinbustracker.app.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.app.favourites.FavouriteStarButton
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text("Stop name or number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        when (val current = state) {
            is SearchUiState.Idle -> Text(
                "Search for a stop by name or by the number on the pole.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            is SearchUiState.Searching -> CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            is SearchUiState.Error -> Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::retry) { Text("Retry") }
            }
            is SearchUiState.Loaded -> if (current.stops.isEmpty()) {
                Text("No matching stops", modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn {
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
    ListItem(
        headlineContent = { Text(stop.name) },
        supportingContent = {
            val routes = stop.routes.joinToString(", ")
            Text(if (routes.isEmpty()) "Stop ${stop.code}" else "Stop ${stop.code} · $routes")
        },
        trailingContent = { FavouriteStarButton(isFavourite = isFavourite, onToggle = onToggleFavourite) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
