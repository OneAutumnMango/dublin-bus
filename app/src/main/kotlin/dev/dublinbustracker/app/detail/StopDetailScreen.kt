package dev.dublinbustracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.app.favourites.FavouriteStarButton
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.ArrivalsRepository
import dev.dublinbustracker.data.FavouritesRepository
import kotlinx.coroutines.delay

// The NTA key's rate limit resets every 60s; never poll faster than that.
private const val REFRESH_INTERVAL_MS = 60_000L

@Composable
fun StopDetailScreen(
    stop: Stop,
    arrivalsRepository: ArrivalsRepository,
    favouritesRepository: FavouritesRepository,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<StopDetailViewModel>(
        key = stop.id,
        factory = viewModelFactory { initializer { StopDetailViewModel(stop.id, arrivalsRepository) } },
    )
    val uiState by viewModel.uiState.collectAsState()
    val favouriteIds by favouritesRepository.favouriteIds.collectAsState()

    LaunchedEffect(stop.id) {
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            viewModel.refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(stop.name, style = MaterialTheme.typography.titleLarge)
                Text("Stop ${stop.code}")
            }
            Row {
                FavouriteStarButton(
                    isFavourite = stop.id in favouriteIds,
                    onToggle = { favouritesRepository.toggle(stop.id) },
                )
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        Button(onClick = viewModel::refresh) { Text("Refresh") }

        when (val state = uiState) {
            is ArrivalsUiState.Loading -> CircularProgressIndicator()
            is ArrivalsUiState.Error -> Text("Couldn't load arrivals: ${state.message}")
            is ArrivalsUiState.Loaded -> if (state.arrivals.isEmpty()) {
                Text("No upcoming arrivals")
            } else {
                LazyColumn {
                    items(state.arrivals) { arrival ->
                        ListItem(
                            headlineContent = { Text("${arrival.routeShortName} ${arrival.destination}") },
                            supportingContent = { Text("Due in ${arrival.dueMinutes} min") },
                        )
                    }
                }
            }
        }
    }
}
