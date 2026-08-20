package dev.wherop.batterywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
     * While discharging. Five rather than [AlarmManager.INTERVAL_FIFTEEN_MINUTES] because on real
     * hardware this alarm is the *only* mechanism that reliably notices the charger — measured on
     * a Galaxy A53, the `JobScheduler` charging constraint took over 15 minutes to satisfy, which
     * the alarm had already beaten. Fifteen minutes of a stale charging bolt is too long. The cost
     * is small: the alarm is non-wakeup (see [AlarmManager.ELAPSED_REALTIME] below), so it never
     * wakes a sleeping device, it only fires when the phone is already awake — which is when
     * somebody is plausibly looking at the widget. It gives up the batching that the `INTERVAL_*`
     * constants get, which for a non-wakeup alarm is a cheap thing to give up.
     *
     * `updatePeriodMillis` in `battery_widget_info.xml` cannot be shortened to match: the system
     * clamps it to 30 minutes. It is only a backstop, so that is fine.
     */
    private const val DISCHARGING_INTERVAL_MS = 5 * 60_000L

    /**
     * While charging.
     *
     * The usual argument against polling this often — battery cost — does not apply to a device
     * on mains, and it lands on the widget's worst-behaved transition: **nothing catches an
     * unplug except this alarm**, so the tick that notices it comes a minute after the fact
     * instead of five. Plug-in detection stays bounded by [DISCHARGING_INTERVAL_MS], which is why
     * that one is five minutes and not fifteen.
     */
    private const val CHARGING_INTERVAL_MS = 60_000L

    /**
     * While discharging, in a debug build. Two minutes rather than five so a test cycle is
     * bearable, and rather than one so that the switch to [CHARGING_INTERVAL_MS] is something a
     * debug build can actually demonstrate — `dumpsys alarm` shows the period change, and the
     * transitions can be timed against a real charger.
     *
     * It cannot go below a minute: `AlarmManagerService` expands any repeating period shorter
     * than that ("Suspiciously short interval …; expanding to 60 seconds" in logcat). Chaining
     * one-shot alarms would get around the clamp, at the price of replacing the mechanism the
     * release build actually uses — so, no.
     */
    private const val DEBUG_DISCHARGING_INTERVAL_MS = 2 * 60_000L

    /**
     * Arms (or re-arms) the alarm at the interval matching [charging]. Scheduling again replaces
     * the pending alarm, so this is idempotent — but each call also restarts the interval, so
     * call it on a charging *transition*, not on every tick.
     *
     * [charging] defaults to a fresh read for callers that have no state to hand — `onEnabled`
     * and [BootReceiver]. [BatteryWidgetUpdater] passes the value it has just read.
     */
    fun schedule(
        context: Context,
        charging: Boolean = BatteryStatus.read(context.applicationContext).charging,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val interval = intervalMs(context, charging)
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + interval,
            interval,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    /**
     * Debug builds poll faster in both states, so a change can be watched in a test cycle rather
     * than a quarter of an hour, while keeping the two states far enough apart to tell the
     * schedules apart. Debug-ness comes from the manifest flag rather than `BuildConfig.DEBUG`,
     * so the interval does not depend on a generated class.
     */
    private fun intervalMs(context: Context, charging: Boolean): Long {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return when {
            charging -> CHARGING_INTERVAL_MS
            debuggable -> DEBUG_DISCHARGING_INTERVAL_MS
            else -> DISCHARGING_INTERVAL_MS
        }
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
