package dev.dublinbustracker.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dublinbustracker.app.AppContainer
import dev.dublinbustracker.app.BusTrackerApp
import dev.dublinbustracker.app.MainActivity
import dev.dublinbustracker.app.R
import dev.dublinbustracker.app.location.lastKnownFix
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.distanceText
import dev.dublinbustracker.data.WidgetConfig
import dev.dublinbustracker.data.WidgetMode
import dev.dublinbustracker.data.dropRoutes
import dev.dublinbustracker.data.keepRoutes
import dev.dublinbustracker.data.nearestFavourite
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val SHOWN_ARRIVALS = 4
private val REFRESHED_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** What the widget has to say right now. */
sealed interface WidgetView {
    data object Loading : WidgetView
    data object NotConfigured : WidgetView
    data class Unreachable(val reason: String?) : WidgetView
    data object NoFavourites : WidgetView
    data object NoLocation : WidgetView
    data class Arrivals(val title: String, val subtitle: String?, val arrivals: List<Arrival>, val refreshedAt: String) : WidgetView
}

private val REFRESHED_AT = longPreferencesKey("refreshed_at")

class BusStopWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        provideContent {
            // Glance keeps a widget's composition alive for a while, and an update during that time only recomposes it:
            // it doesn't run provideGlance again. So the data is fetched in here, keyed on a value every refresh changes,
            // and a refresh (or a just-finished setup) always fetches again.
            val refreshedAt = currentState(REFRESHED_AT)
            var view by remember { mutableStateOf<WidgetView>(WidgetView.Loading) }
            LaunchedEffect(refreshedAt) {
                val next = load(context, appWidgetId)
                // One failed fetch shouldn't wipe good data that's a couple of minutes old.
                view = if (next is WidgetView.Unreachable && view is WidgetView.Arrivals) view else next
            }
            WidgetContent(context, appWidgetId, view)
        }
    }
}

/** Fetches this widget's data again and redraws it, whether or not it is currently being shown. */
suspend fun BusStopWidget.refresh(context: Context, glanceId: GlanceId) {
    updateAppWidgetState(context, glanceId) { it[REFRESHED_AT] = System.currentTimeMillis() }
    update(context, glanceId)
}

/** Refreshes every widget placed on the home screen; returns how many there are. */
suspend fun refreshAllWidgets(context: Context): Int {
    val ids = GlanceAppWidgetManager(context).getGlanceIds(BusStopWidget::class.java)
    ids.forEach { BusStopWidget().refresh(context, it) }
    return ids.size
}

private suspend fun load(context: Context, appWidgetId: Int?): WidgetView {
    val config = appWidgetId?.let { WidgetConfigStore(context).load(it) }
    val container = (context.applicationContext as? BusTrackerApp)?.container
    return if (config == null || container == null) WidgetView.NotConfigured else resolve(context, container, config)
}

private suspend fun resolve(context: Context, container: AppContainer, config: WidgetConfig): WidgetView {
    val (stopId, title, subtitle) = when (config.mode) {
        WidgetMode.SINGLE_STOP -> {
            val id = config.stopId ?: return WidgetView.NotConfigured
            val stop = retrying { container.apiClient.stop(id) }.getOrElse { return WidgetView.Unreachable(it.message) }
            Triple(id, stop.name, subtitleOf(stopNumber(stop.code), config.routes.sorted().joinToString(", ").ifEmpty { null }?.let { "Buses $it" }))
        }
        WidgetMode.NEAREST_FAVOURITE -> {
            if (container.favouritesRepository.favourites.value.isEmpty()) return WidgetView.NoFavourites
            val here = lastKnownFix(context) ?: return WidgetView.NoLocation
            val (favourite, meters) = nearestFavourite(container.favouritesRepository.favourites.value, config.excludedStopIds, here)
                ?: return WidgetView.NoFavourites
            Triple(
                favourite.id,
                favourite.nickname.ifBlank { favourite.name.ifBlank { favourite.id } },
                subtitleOf(stopNumber(favourite.code), "Nearest favourite · ${distanceText(meters)}"),
            )
        }
    }
    val arrivals = retrying { container.apiClient.arrivals(stopId, limit = 20) }.getOrElse { return WidgetView.Unreachable(it.message) }
    // Buses the user hid for this favourite never show, in either mode.
    val hidden = container.favouritesRepository.favourites.value.firstOrNull { it.id == stopId }?.hiddenRoutes.orEmpty()
    return WidgetView.Arrivals(
        title,
        subtitle,
        dropRoutes(keepRoutes(arrivals, config.routes) { it.routeShortName }, hidden) { it.routeShortName }.take(SHOWN_ARRIVALS),
        refreshedAt = REFRESHED_FORMAT.format(LocalTime.now()),
    )
}

/** A background refresh often runs just as the network is waking up, so a couple of quick retries beat a one-shot failure. */
private suspend fun <T> retrying(block: suspend () -> T): Result<T> {
    repeat(2) {
        runCatching { block() }.let { if (it.isSuccess) return it }
        delay(1500)
    }
    return runCatching { block() }
}

private fun stopNumber(code: String): String? = code.takeIf { it.isNotBlank() }?.let { "Stop $it" }

/** The small line under the title: the parts that are there, with the stop number first. */
private fun subtitleOf(vararg parts: String?): String? = parts.filterNotNull().joinToString(" · ").ifEmpty { null }

private val TEXT = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
private val MUTED = ColorProvider(day = Color(0xFF5F5B66), night = Color(0xFFB0ABB5))
private val BACKGROUND = ColorProvider(day = Color(0xFFFDFDFD), night = Color(0xFF2B2930))

@Composable
private fun WidgetContent(context: Context, appWidgetId: Int?, view: WidgetView) {
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(BACKGROUND).padding(12.dp)
            .clickable(
                if (view is WidgetView.NotConfigured && appWidgetId != null) {
                    // Opens the setup screen for this widget; the data makes each widget's intent distinct.
                    actionStartIntent(
                        Intent(context, WidgetConfigActivity::class.java)
                            .setData(Uri.parse("widget://$appWidgetId"))
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                    )
                } else {
                    actionStartActivity<MainActivity>()
                },
            ),
    ) {
        when (view) {
            is WidgetView.Loading -> Message("Loading...")
            is WidgetView.NotConfigured -> Message("This widget isn't set up yet. Tap to choose what it shows.")
            is WidgetView.Unreachable -> Message("Couldn't load buses: ${view.reason ?: "unknown error"}. Tap the widget to open the app.")
            is WidgetView.NoFavourites -> Message("No favourite stops to choose from. Star a stop in the app, or exclude fewer.")
            is WidgetView.NoLocation -> Message("Waiting for your location. Open the app once, or allow location \"all the time\".")
            is WidgetView.Arrivals -> {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(view.title, maxLines = 1, style = TextStyle(color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        view.subtitle?.let { Text(it, maxLines = 1, style = TextStyle(color = MUTED, fontSize = 11.sp)) }
                    }
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        colorFilter = ColorFilter.tint(MUTED),
                        modifier = GlanceModifier.size(30.dp).padding(3.dp).clickable(actionRunCallback<RefreshWidgetAction>()),
                    )
                }
                // A Spacer with no height of its own stretches to fill the widget and pushes everything below it out of view.
                Spacer(modifier = GlanceModifier.height(4.dp))
                if (view.arrivals.isEmpty()) {
                    Text("No upcoming buses", style = TextStyle(color = MUTED, fontSize = 13.sp))
                }
                view.arrivals.forEach { arrival ->
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(arrival.routeShortName, style = TextStyle(color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.width(40.dp))
                        Text(arrival.destination, maxLines = 1, style = TextStyle(color = TEXT, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
                        Text(dueText(arrival), style = TextStyle(color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
                // A spacer that takes the leftover height puts the footer at the bottom.
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text("Refreshed at ${view.refreshedAt}", style = TextStyle(color = MUTED, fontSize = 10.sp))
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = TEXT, fontSize = 13.sp))
}

private fun dueText(arrival: Arrival) = if (arrival.dueMinutes <= 0) "Due" else "${arrival.dueMinutes} min"

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BusStopWidget().refresh(context, glanceId)
    }
}
