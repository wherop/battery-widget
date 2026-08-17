package dev.wherop.batterywidget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Notices the charger going in and coming out.
 *
 * The obvious mechanism — manifest receivers for `ACTION_POWER_CONNECTED` and
 * `ACTION_POWER_DISCONNECTED` — does not work: Android's implicit-broadcast restriction drops
 * both before they reach the app. See "Battery state never arrives by broadcast" in CLAUDE.md.
 *
 * `JobScheduler` is the framework's sanctioned replacement, and it reports *both* edges of the
 * transition, which is what makes a single job enough:
 *
 * - the charging constraint becoming true starts the job, so [onStartJob] means "plugged in";
 * - the constraint ceasing to hold stops it, so [onStopJob] means "unplugged".
 *
 * The job therefore stays nominally running for as long as the device is on power. It holds no
 * thread and does no work in the meantime — it exists only to be stopped. The system also stops
 * it when it reaches its execution time limit, which is indistinguishable from an unplug and
 * equally harmless: both redraw and re-arm.
 */
class ChargingJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // The device just went on to power. The bolt switches instantly, as it does in the CSS:
        // the level hasn't moved, so the updater skips the fill animation and pushes one frame.
        BatteryWidgetUpdater.updateAll(applicationContext, animate = true)
        // true = "still working". That is what keeps onStopJob coming; do not call jobFinished().
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Either the charger came out or the execution limit expired. Both want a redraw — the
        // bolt has to disappear on an unplug — and a fresh job to catch the next transition.
        BatteryWidgetUpdater.updateAll(applicationContext, animate = true)
        schedule(applicationContext)
        // false: we have just re-armed the job ourselves, so don't also ask for a backoff retry.
        return false
    }

    companion object {

        private const val JOB_ID = 1

        /**
         * Arms the watcher.
         *
         * Idempotent — scheduling an existing id replaces the pending job — so it is safe to
         * call from every path that might be the first to run after a reboot, an update, or the
         * process being killed while the job was running. That last case is the reason the
         * 15-minute alarm re-arms it too: a killed process never reaches [onStopJob], so
         * without a periodic re-arm the watcher would stay dead until the next reboot.
         */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ChargingJobService::class.java))
                .setRequiresCharging(true)
                // Survives a reboot by itself; BootReceiver re-arms it as well, belt and braces.
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
