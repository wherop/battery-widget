package dev.wherop.batterywidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import kotlin.math.roundToInt

/**
 * Renders the current battery state into every placed widget.
 *
 * The design calls for the fill and its colour to move over 0.5s rather than jump, which a
 * widget can only do by posting a short burst of frames — see [animate]. Everything else
 * (the percentage text, the charging bolt) switches instantly, as in the prototype.
 */
object BatteryWidgetUpdater {

    /** Frames posted across [BatteryDesign.TRANSITION_MS]; each one is a full bitmap push. */
    private const val FRAMES = 8

    /**
     * @param animate  interpolate from the previously drawn state
     * @param onFinished invoked once the last frame is pushed — use it to release a
     *                   `BroadcastReceiver.goAsync()` result
     */
    fun updateAll(context: Context, animate: Boolean = false, onFinished: (() -> Unit)? = null) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, BatteryWidgetProvider::class.java))
        update(app, manager, ids, animate, onFinished)
    }

    fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        animate: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        val app = context.applicationContext
        if (appWidgetIds.isEmpty()) {
            onFinished?.invoke()
            return
        }

        val target = BatteryStatus.read(app)
        val previous = WidgetState.read(app)
        WidgetState.write(app, target)

        if (!animate || previous == null || previous.level == target.level) {
            draw(app, manager, appWidgetIds, target.fraction, target.fillColor, target)
            onFinished?.invoke()
            return
        }

        animate(app, manager, appWidgetIds, previous, target, onFinished)
    }

    private fun animate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        from: BatteryStatus,
        to: BatteryStatus,
        onFinished: (() -> Unit)?,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val frameDelay = BatteryDesign.TRANSITION_MS / FRAMES

        for (frame in 1..FRAMES) {
            handler.postDelayed({
                val t = CssEase.transform(frame.toFloat() / FRAMES)
                draw(
                    context,
                    manager,
                    appWidgetIds,
                    fraction = from.fraction + (to.fraction - from.fraction) * t,
                    fillColor = blend(from.fillColor, to.fillColor, t),
                    status = to,
                )
                if (frame == FRAMES) onFinished?.invoke()
            }, frameDelay * frame)
        }
    }

    private fun draw(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        fraction: Float,
        fillColor: Int,
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
                fraction = fraction,
                fillColor = fillColor,
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

    /** Straight per-channel mix, matching how CSS transitions a background colour. */
    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val start = (from shr shift) and 0xFF
            val end = (to shr shift) and 0xFF
            return (start + (end - start) * t).roundToInt().coerceIn(0, 255)
        }
        return Color.argb(channel(24), channel(16), channel(8), channel(0))
    }
}
