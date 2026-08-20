package dev.wherop.batterywidget

import android.content.Context

/**
 * The last state the widget was drawn with. Two things need it, and neither can hold it in
 * memory, because the widget process is short-lived: a timer tick uses it to skip a redraw that
 * would change nothing, and [BatteryWidgetUpdater] compares its `charging` against the current
 * one to notice the charger arriving or leaving.
 *
 * A push that must happen regardless of the stored state — placement, resize, reboot — goes
 * through `force` rather than around this.
 */
internal object WidgetState {

    private const val PREFS = "battery_widget_state"
    private const val KEY_LEVEL = "level"
    private const val KEY_CHARGING = "charging"

    fun read(context: Context): BatteryStatus? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val level = prefs.getInt(KEY_LEVEL, -1)
        if (level < 0) return null
        return BatteryStatus(level, prefs.getBoolean(KEY_CHARGING, false))
    }

    fun write(context: Context, status: BatteryStatus) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LEVEL, status.level)
            .putBoolean(KEY_CHARGING, status.charging)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
