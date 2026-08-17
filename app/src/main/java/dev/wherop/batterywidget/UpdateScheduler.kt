package dev.wherop.batterywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Keeps the displayed percentage roughly current.
 *
 * `ACTION_BATTERY_CHANGED` cannot be received from the manifest, and a background process
 * holding a runtime receiver would be killed anyway, so the widget polls: an inexact,
 * non-wakeup repeating alarm the system is free to batch with other work. This is what tracks
 * the level drifting down; plugging in and unplugging arrive promptly through
 * [ChargingJobService], and `updatePeriodMillis` in the widget metadata is the last-resort
 * backstop.
 */
internal object UpdateScheduler {

    private const val REQUEST_CODE = 1
    private val INTERVAL_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BatteryWidgetProvider::class.java).apply {
            action = BatteryWidgetProvider.ACTION_REFRESH
            component = ComponentName(context, BatteryWidgetProvider::class.java)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
