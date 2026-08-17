package dev.wherop.batterywidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The battery transitions the system will deliver to a manifest receiver: plugging in and
 * out (the charging bolt) and the low/okay thresholds (where the fill changes colour).
 * Also re-arms the polling alarm after a reboot or an app update, since neither replays
 * `onEnabled`.
 */
class PowerEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                UpdateScheduler.schedule(context)
                BatteryWidgetUpdater.updateAll(context)
            }

            else -> {
                val pendingResult = goAsync()
                BatteryWidgetUpdater.updateAll(context, animate = true) { pendingResult.finish() }
            }
        }
    }
}
