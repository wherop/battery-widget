package dev.wherop.batterywidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * The widget itself. It owns no UI of its own beyond a single `ImageView` — the battery is
 * drawn per instance by [BatteryRenderer] at the exact size the launcher hands out, which is
 * how one provider covers every span from 1x1 upward.
 */
class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Periodic and first-placement redraws: no animation, just land on the current value.
        BatteryWidgetUpdater.update(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Resized: the shape, radii, bump and type sizes are all derived from the new box.
        BatteryWidgetUpdater.update(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        UpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        UpdateScheduler.cancel(context)
        WidgetState.clear(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // Keep the broadcast alive for the length of the fill animation.
            val pendingResult = goAsync()
            BatteryWidgetUpdater.updateAll(context, animate = true) { pendingResult.finish() }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.wherop.batterywidget.action.REFRESH"
    }
}
