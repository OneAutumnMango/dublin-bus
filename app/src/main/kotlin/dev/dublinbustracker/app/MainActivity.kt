package dev.dublinbustracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dublinbustracker.app.detail.StopDetailScreen
import dev.dublinbustracker.app.map.MapScreen
import dev.dublinbustracker.app.search.SearchScreen
import dev.dublinbustracker.core.Stop

class MainActivity : ComponentActivity() {
    private val container: AppContainer by lazy { (application as BusTrackerApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(container)
                }
            }
        }
    }
}

private enum class AppTab(val label: String) { SEARCH("Search"), MAP("Map") }

@Composable
private fun AppRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(AppTab.SEARCH) }
    var selectedStop by remember { mutableStateOf<Stop?>(null) }

    val stop = selectedStop
    when {
        stop != null -> StopDetailScreen(
            stop = stop,
            arrivalsRepository = container.arrivalsRepository,
            onBack = { selectedStop = null },
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTabRow(selectedTabIndex = tab.ordinal) {
                AppTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                }
            }
            when (tab) {
                AppTab.SEARCH -> SearchScreen(container.stopRepository, onStopSelected = { selectedStop = it })
                AppTab.MAP -> MapScreen(container.stopRepository, onStopSelected = { selectedStop = it })
            }
        }
    }
}
