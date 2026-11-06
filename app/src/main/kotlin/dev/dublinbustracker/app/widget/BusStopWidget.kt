package dev.dublinbustracker.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dublinbustracker.app.AppContainer
import dev.dublinbustracker.app.BusTrackerApp
import dev.dublinbustracker.app.MainActivity
import dev.dublinbustracker.app.location.lastKnownFix
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.distanceText
import dev.dublinbustracker.data.WidgetConfig
import dev.dublinbustracker.data.WidgetMode
import dev.dublinbustracker.data.keepRoutes
import dev.dublinbustracker.data.nearestFavourite

private const val SHOWN_ARRIVALS = 4

/** What the widget has to say right now. */
sealed interface WidgetView {
    data object NotConfigured : WidgetView
    data object Unreachable : WidgetView
    data object NoFavourites : WidgetView
    data object NoLocation : WidgetView
    data class Arrivals(val title: String, val subtitle: String?, val arrivals: List<Arrival>) : WidgetView
}

class BusStopWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val config = appWidgetId?.let { WidgetConfigStore(context).load(it) }
        val container = (context.applicationContext as? BusTrackerApp)?.container
        val view = if (config == null || container == null) WidgetView.NotConfigured else resolve(context, container, config)

        provideContent { WidgetContent(view) }
    }
}

private suspend fun resolve(context: Context, container: AppContainer, config: WidgetConfig): WidgetView {
    val (stopId, title, subtitle) = when (config.mode) {
        WidgetMode.SINGLE_STOP -> {
            val id = config.stopId ?: return WidgetView.NotConfigured
            val stop = runCatching { container.apiClient.stop(id) }.getOrNull() ?: return WidgetView.Unreachable
            Triple(id, stop.name, config.routes.sorted().joinToString(", ").ifEmpty { null }?.let { "Buses $it" })
        }
        WidgetMode.NEAREST_FAVOURITE -> {
            if (container.favouritesRepository.favourites.value.isEmpty()) return WidgetView.NoFavourites
            val here = lastKnownFix(context) ?: return WidgetView.NoLocation
            val (favourite, meters) = nearestFavourite(container.favouritesRepository.favourites.value, config.excludedStopIds, here)
                ?: return WidgetView.NoFavourites
            Triple(favourite.id, favourite.nickname.ifBlank { favourite.name.ifBlank { favourite.id } }, "Nearest favourite · ${distanceText(meters)}")
        }
    }
    val arrivals = runCatching { container.apiClient.arrivals(stopId, limit = 20) }.getOrNull() ?: return WidgetView.Unreachable
    return WidgetView.Arrivals(title, subtitle, keepRoutes(arrivals, config.routes) { it.routeShortName }.take(SHOWN_ARRIVALS))
}

private val TEXT = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
private val MUTED = ColorProvider(day = Color(0xFF5F5B66), night = Color(0xFFB0ABB5))
private val BACKGROUND = ColorProvider(day = Color(0xFFFDFDFD), night = Color(0xFF2B2930))

@Composable
private fun WidgetContent(view: WidgetView) {
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(BACKGROUND).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when (view) {
            is WidgetView.NotConfigured -> Message("Tap to open the app, then remove and re-add this widget to set it up.")
            is WidgetView.Unreachable -> Message("Can't reach the server - check the address in Settings.")
            is WidgetView.NoFavourites -> Message("No favourite stops to choose from. Star a stop in the app, or exclude fewer.")
            is WidgetView.NoLocation -> Message("Waiting for your location. Open the app once, or allow location \"all the time\".")
            is WidgetView.Arrivals -> {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(view.title, maxLines = 1, style = TextStyle(color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        view.subtitle?.let { Text(it, maxLines = 1, style = TextStyle(color = MUTED, fontSize = 11.sp)) }
                    }
                    Text(
                        "Refresh",
                        style = TextStyle(color = MUTED, fontSize = 11.sp),
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
                    )
                }
                Spacer(modifier = GlanceModifier.padding(top = 4.dp))
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
        BusStopWidget().update(context, glanceId)
    }
}
