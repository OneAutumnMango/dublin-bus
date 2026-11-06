package dev.dublinbustracker.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class BusStopWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BusStopWidget()

    /** The first widget was placed: start keeping it fresh. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresh.ensureScheduled(context)
    }

    // The system also calls this on its own slow schedule, which revives the polling if it was ever lost.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefresh.ensureScheduled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetConfigStore(context)
        appWidgetIds.forEach { store.remove(it) }
    }

    /** The last widget was removed: nothing left to poll for. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresh.cancel(context)
    }
}
