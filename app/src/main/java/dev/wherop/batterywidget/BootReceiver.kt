package dev.wherop.batterywidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the widget's two wake-up mechanisms after the app comes back, and repaints once so a
 * reboot doesn't leave a stale percentage on the homescreen. Neither `onEnabled` nor a job
 * survives an uninstall/reinstall, and `AppWidgetProvider` is not told about either event.
 *
 * These are the only two system broadcasts this app can still rely on. The battery and power
 * ones — `ACTION_POWER_CONNECTED`, `ACTION_POWER_DISCONNECTED`, `BATTERY_LOW`, `BATTERY_OKAY` —
 * are dropped by Android before they arrive; [ChargingJobService] replaces them. `BOOT_COMPLETED`
 * and `MY_PACKAGE_REPLACED` are on the exemption list and do get through, verified on API 36.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                UpdateScheduler.schedule(context)
                ChargingJobService.schedule(context)
                BatteryWidgetUpdater.updateAll(context)
            }
        }
    }
}
