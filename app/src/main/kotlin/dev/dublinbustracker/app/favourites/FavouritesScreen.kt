package dev.dublinbustracker.app.favourites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.FavouritesRepository
import dev.dublinbustracker.data.StopRepository

@Composable
fun FavouritesScreen(
    stopRepository: StopRepository,
    favouritesRepository: FavouritesRepository,
    onStopSelected: (Stop) -> Unit,
) {
    val favouriteIds by favouritesRepository.favouriteIds.collectAsState()
    val favouriteStops = remember(favouriteIds) {
        favouriteIds.mapNotNull { stopRepository.byId(it) }.sortedBy { it.name }
    }

    if (favouriteStops.isEmpty()) {
        Text("No favourites yet - star a stop from Search or its detail page", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(favouriteStops, key = { it.id }) { stop ->
            ListItem(
                headlineContent = { Text(stop.name) },
                supportingContent = { Text("Stop ${stop.code}") },
                modifier = Modifier.clickable { onStopSelected(stop) },
            )
        }
    }
}
