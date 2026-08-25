package dev.dublinbustracker.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.action.ActionParameters
import androidx.glance.text.Text
import dev.dublinbustracker.app.BusTrackerApp
import dev.dublinbustracker.app.MainActivity
import dev.dublinbustracker.core.Arrival
import dev.dublinbustracker.core.Stop

class BusStopWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val container = (context.applicationContext as BusTrackerApp).container
        val stop = WidgetStopStore(context).stopIdFor(appWidgetId)?.let { container.stopRepository.byId(it) }
        val arrivals = stop?.let {
            runCatching { container.arrivalsRepository.arrivalsForStop(it.id) }.getOrElse { emptyList() }
        }.orEmpty()

        provideContent { WidgetContent(stop, arrivals) }
    }
}

@Composable
private fun WidgetContent(stop: Stop?, arrivals: List<Arrival>) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (stop == null) {
            Text("Tap to add this widget, then choose a stop")
        } else {
            Text(stop.name)
            if (arrivals.isEmpty()) {
                Text("No upcoming arrivals")
            } else {
                arrivals.take(3).forEach { arrival ->
                    Text("${arrival.routeShortName} ${arrival.destination}: ${arrival.dueMinutes}m")
                }
            }
            Text("Refresh", modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()))
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        BusStopWidget().update(context, glanceId)
    }
}
