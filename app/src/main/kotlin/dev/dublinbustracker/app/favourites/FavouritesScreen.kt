package dev.dublinbustracker.app.favourites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.core.Stop
import dev.dublinbustracker.data.FavouritesRepository
import dev.dublinbustracker.data.TransitApiClient

@Composable
fun FavouritesScreen(
    apiClient: TransitApiClient,
    favouritesRepository: FavouritesRepository,
    onStopSelected: (Stop) -> Unit,
) {
    val favourites by favouritesRepository.favourites.collectAsState()

    // Entries migrated from the old id-only format have no name until the server fills them in.
    LaunchedEffect(favourites) {
        favourites.filter { it.needsDetails }.forEach { favourite ->
            runCatching { apiClient.stop(favourite.id) }
                .onSuccess { favouritesRepository.updateDetails(it) }
        }
    }

    if (favourites.isEmpty()) {
        Text(
            "No favourites yet - tap the star on a stop to add it.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(favourites, key = { it.id }) { favourite ->
            ListItem(
                headlineContent = { Text(favourite.name.ifBlank { favourite.id }) },
                supportingContent = { Text("Stop ${favourite.code.ifBlank { "-" }}") },
                trailingContent = {
                    FavouriteStarButton(isFavourite = true, onToggle = { favouritesRepository.remove(favourite.id) })
                },
                modifier = Modifier.clickable { onStopSelected(favourite.toStop()) },
            )
        }
    }
}
