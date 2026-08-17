package dev.wherop.batterywidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.SizeF
import kotlin.math.max
import kotlin.math.roundToInt

/** The pixel box a widget instance currently occupies. */
internal data class WidgetSizePx(val width: Int, val height: Int)

internal object WidgetSize {

    /**
     * Resolves the current size of one widget instance.
     *
     * On Android 12+ the launcher reports the exact sizes it will show the widget at, one
     * per device orientation. Before that, only the min/max dp bounds are available: in
     * portrait the widget is `minWidth x maxHeight`, in landscape `maxWidth x minHeight`.
     */
    fun of(context: Context, manager: AppWidgetManager, appWidgetId: Int): WidgetSizePx? {
        val options = manager.getAppWidgetOptions(appWidgetId) ?: return null
        val metrics = context.resources.displayMetrics
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        var widthDp = 0f
        var heightDp = 0f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                val size = if (landscape && sizes.size > 1) sizes[1] else sizes[0]
                widthDp = size.width
                heightDp = size.height
            }
        }

        if (widthDp <= 0f || heightDp <= 0f) {
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            widthDp = (if (landscape) maxWidth else minWidth).toFloat()
            heightDp = (if (landscape) minHeight else maxHeight).toFloat()
        }

        if (widthDp <= 0f || heightDp <= 0f) return null

        // A widget can never be larger than the screen; clamping keeps the bitmap well
        // inside the size limit RemoteViews imposes on what it will marshal.
        val limit = max(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        return WidgetSizePx(
            width = (widthDp * metrics.density).roundToInt().coerceIn(1, limit),
            height = (heightDp * metrics.density).roundToInt().coerceIn(1, limit),
        )
    }
}
