package dev.dublinbustracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.dublinbustracker.app.ui.BusTrackerTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        // Android 15+ (targetSdk 36 here) draws the app edge to edge with no opt-out. Say so explicitly,
        // with dark status-bar icons: otherwise they come out white-on-pale and can't be read.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            BusTrackerTheme {
                // The surface fills the whole window, bars included, so no strip of a different colour
                // shows behind the status bar; only the content inside is padded clear of the bars.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        AppRoot(container)
                    }
                }
            }
        }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    SEARCH("Search", Icons.Filled.Search),
    MAP("Map", Icons.Filled.Map),
    FAVOURITES("Favourites", Icons.Filled.Star),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(AppTab.SEARCH) }
    var selectedStop by remember { mutableStateOf<Stop?>(null) }

    var routeMapArrival by remember { mutableStateOf<Arrival?>(null) }
    var choosingMapArea by remember { mutableStateOf(false) }

    val stop = selectedStop
    val mapArrival = routeMapArrival

    // Back steps out one level at a time: map -> stop -> tab, and from another tab to Search, before the app closes.
    BackHandler(enabled = choosingMapArea || stop != null || tab != AppTab.SEARCH) {
        when {
            choosingMapArea -> choosingMapArea = false
            mapArrival != null -> routeMapArrival = null
            stop != null -> selectedStop = null
            else -> tab = AppTab.SEARCH
        }
    }
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
            Box(modifier = Modifier.weight(1f)) {
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
            NavigationBar {
                AppTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        }
    }
}
