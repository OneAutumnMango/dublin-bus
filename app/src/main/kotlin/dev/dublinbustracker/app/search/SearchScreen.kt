package dev.dublinbustracker.app.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
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
import dev.dublinbustracker.data.StopRepository

@Composable
fun SearchScreen(stopRepository: StopRepository, favouritesRepository: FavouritesRepository, onStopSelected: (Stop) -> Unit) {
    val viewModel = viewModel<SearchViewModel>(
        factory = viewModelFactory { initializer { SearchViewModel(stopRepository) } },
    )
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val favouriteIds by favouritesRepository.favouriteIds.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text("Stop name or number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyColumn {
            items(results, key = { it.id }) { stop ->
                ListItem(
                    headlineContent = { Text(stop.name) },
                    supportingContent = { Text("Stop ${stop.code}") },
                    trailingContent = {
                        FavouriteStarButton(
                            isFavourite = stop.id in favouriteIds,
                            onToggle = { favouritesRepository.toggle(stop.id) },
                        )
                    },
                    modifier = Modifier.clickable { onStopSelected(stop) },
                )
            }
        }
    }
}
