package dev.dublinbustracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dublinbustracker.app.detail.StopDetailScreen
import dev.dublinbustracker.app.favourites.FavouritesScreen
import dev.dublinbustracker.app.map.MapScreen
import dev.dublinbustracker.app.route.RouteMapScreen
import dev.dublinbustracker.app.search.SearchScreen
import dev.dublinbustracker.app.settings.ChooseAreaScreen
import dev.dublinbustracker.app.settings.SettingsScreen
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.Stop

class MainActivity : ComponentActivity() {
    private val container: AppContainer by lazy { (application as BusTrackerApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Android 15+ (targetSdk 36 here) enforces edge-to-edge with no opt-out, so
                // content draws under the status/nav bars unless padded for them explicitly -
                // without this, the top row of every screen (back button, favourite star, tabs)
                // sits under the status bar and is unreachable by touch.
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    AppRoot(container)
                }
            }
        }
    }
}

private enum class AppTab(val label: String) {
    SEARCH("Search"),
    MAP("Map"),
    FAVOURITES("Favourites"),
    SETTINGS("Settings"),
}

@Composable
private fun AppRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(AppTab.SEARCH) }
    var selectedStop by remember { mutableStateOf<Stop?>(null) }

    var routeMapArrival by remember { mutableStateOf<Arrival?>(null) }
    var choosingMapArea by remember { mutableStateOf(false) }

    val stop = selectedStop
    val mapArrival = routeMapArrival
    when {
        choosingMapArea -> ChooseAreaScreen(onBack = { choosingMapArea = false })
        stop != null && mapArrival != null -> RouteMapScreen(
            stop = stop,
            arrival = mapArrival,
            apiClient = container.apiClient,
            onBack = { routeMapArrival = null },
        )
        stop != null -> StopDetailScreen(
            initialStop = stop,
            apiClient = container.apiClient,
            favouritesRepository = container.favouritesRepository,
            onBack = { selectedStop = null },
            onViewRoute = { routeMapArrival = it },
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            SecondaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                AppTab.entries.forEach { entry ->
                    Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(entry.label) })
                }
            }
            when (tab) {
                AppTab.SEARCH -> SearchScreen(
                    container.apiClient,
                    container.favouritesRepository,
                    onStopSelected = { selectedStop = it },
                )
                AppTab.MAP -> MapScreen(container.apiClient, onStopSelected = { selectedStop = it })
                AppTab.FAVOURITES -> FavouritesScreen(
                    container.apiClient,
                    container.favouritesRepository,
                    onStopSelected = { selectedStop = it },
                )
                AppTab.SETTINGS -> SettingsScreen(container.settingsRepository, container.apiClient, onChooseArea = { choosingMapArea = true })
            }
        }
    }
}
