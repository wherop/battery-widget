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
 * non-wakeup repeating alarm.
 *
 * **This is the widget's primary mechanism, not a backstop.** It tracks the level drifting down
 * and, on the hardware tested so far, it is also what notices the charger — [ChargingJobService]
 * is meant to beat it to a plug-in but did not on a Galaxy A53, and nothing catches an unplug
 * except this alarm. `updatePeriodMillis` in the widget metadata is the last-resort backstop
 * behind it.
 */
internal object UpdateScheduler {

    private const val REQUEST_CODE = 1

    /**
     * Five minutes in release, one minute in debug so a change can be watched in a test cycle
     * rather than a quarter of an hour.
     *
     * Five rather than [AlarmManager.INTERVAL_FIFTEEN_MINUTES] because on real hardware this
     * alarm is the *only* mechanism that reliably notices the charger — measured on a Galaxy
     * A53, the `JobScheduler` charging constraint took over 15 minutes to satisfy, which the
     * alarm had already beaten. Fifteen minutes of a stale charging bolt is too long. The cost
     * is small: the alarm is non-wakeup (see [AlarmManager.ELAPSED] below), so it never wakes a
     * sleeping device, it only fires when the phone is already awake — which is when somebody is
     * plausibly looking at the widget. It gives up the batching that the `INTERVAL_*` constants
     * get, which for a non-wakeup alarm is a cheap thing to give up.
     *
     * `updatePeriodMillis` in `battery_widget_info.xml` cannot be shortened to match: the system
     * clamps it to 30 minutes. It is only a backstop, so that is fine.
     */
    private val INTERVAL_MS = if (BuildConfig.DEBUG) 60_000L else 5 * 60_000L

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
