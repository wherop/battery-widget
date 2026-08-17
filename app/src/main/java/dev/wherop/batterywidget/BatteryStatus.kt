package dev.wherop.batterywidget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlin.math.roundToInt

/** The two pieces of device state the widget renders. */
data class BatteryStatus(val level: Int, val charging: Boolean) {

    val fraction: Float get() = level / 100f
    val fillColor: Int get() = BatteryDesign.fillColor(level)
    val label: String get() = "$level%"

    companion object {

        /**
         * Reads the current state from the sticky `ACTION_BATTERY_CHANGED` broadcast.
         *
         * Must be called with an application context: a `BroadcastReceiver` context cannot
         * register receivers, not even the null-receiver sticky read this uses.
         */
        fun read(context: Context): BatteryStatus {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, filter)
            }
            return from(intent)
        }

        fun from(intent: Intent?): BatteryStatus {
            if (intent == null) return BatteryStatus(0, false)

            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val level = if (raw >= 0 && scale > 0) {
                (raw * 100f / scale).roundToInt().coerceIn(0, 100)
            } else {
                0
            }

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            // Plugged in covers AC/USB/wireless/dock; the status check catches chargers the
            // framework reports without a plug type.
            val charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING

            return BatteryStatus(level, charging)
        }
    }
}
