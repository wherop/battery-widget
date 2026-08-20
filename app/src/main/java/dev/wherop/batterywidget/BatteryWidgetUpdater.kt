package dev.wherop.batterywidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/**
 * Renders the current battery state into every placed widget.
 *
 * Every update is a single bitmap push. The prototype specifies `transition: … 0.5s ease` on the
 * fill and its colour, and this deliberately does not follow it: the widget is repainted on a
 * timer rather than on a battery event, so consecutive updates are about one percent apart and
 * the easing was smoothing a change too small to see. See the deviations section in README.md.
 *
 * It is also where the polling interval gets chosen, because this is the only place that sees
 * the charging state change from one draw to the next — see [UpdateScheduler].
 */
object BatteryWidgetUpdater {

    /**
     * @param force redraw even when the state has not moved since the last push. Placement,
     *              resize and a reboot need it; a timer tick does not.
     */
    fun updateAll(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, BatteryWidgetProvider::class.java))
        update(app, manager, ids, force)
    }

    fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        force: Boolean = false,
    ) {
        val app = context.applicationContext
        if (appWidgetIds.isEmpty()) return

        val status = BatteryStatus.read(app)
        val previous = WidgetState.read(app)
        if (!force && status == previous) return

        WidgetState.write(app, status)

        // The charger came or went, so the alarm has to run at the other interval from here on.
        // Only on the transition: re-arming on every tick would push the next tick away each time.
        if (previous == null || previous.charging != status.charging) {
            UpdateScheduler.schedule(app, charging = status.charging)
        }

        draw(app, manager, appWidgetIds, status)
    }

    private fun draw(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        status: BatteryStatus,
    ) {
        val renderer = BatteryRenderer(context.resources.displayMetrics.density)
        val description = context.getString(
            if (status.charging) R.string.battery_charging else R.string.battery_level,
            status.level,
        )

        for (appWidgetId in appWidgetIds) {
            val size = WidgetSize.of(context, manager, appWidgetId) ?: continue
            val bitmap = renderer.render(
                widthPx = size.width,
                heightPx = size.height,
                fraction = status.fraction,
                fillColor = status.fillColor,
                label = status.label,
                charging = status.charging,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_battery).apply {
                setImageViewBitmap(R.id.battery, bitmap)
                setContentDescription(R.id.battery, description)
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
