package dev.wherop.batterywidget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Redraws the widget when the device starts charging, so the bolt does not have to wait for the
 * next [UpdateScheduler] tick.
 *
 * Manifest receivers for `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` cannot do this —
 * Android's implicit-broadcast restriction drops them. See CLAUDE.md.
 *
 * **This job does one thing and finishes.** An earlier version returned `true` from
 * [onStartJob] and never called `jobFinished()`, holding the job nominally running so that
 * `onStopJob` would fire on unplug and give us the other edge of the transition for free. On a
 * real device (Galaxy A53, One UI 8) that turned into a restart loop: something — job quota,
 * most likely — stopped the held job every ten seconds or so, `onStopJob` re-armed it, the
 * charging constraint was still satisfied, and it started again immediately. Six job instances
 * a minute, each redrawing the widget twice. Do not reintroduce the hold-open.
 *
 * Unplugging is therefore left to the polling alarm, which is the honest trade: catching it
 * promptly costs a job that never ends, and that is not a bargain a battery widget should make.
 */
class ChargingJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        BatteryWidgetUpdater.updateAll(applicationContext)
        // Done. false = no work continues on another thread, so the job ends here and the
        // system has no running job to stop, quota to charge, or wakelock to hold.
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Nothing is held open, so this only runs if the system stopped us mid-callback.
        // Returning false declines a retry; the re-arm paths below will bring it back.
        return false
    }

    companion object {

        private const val JOB_ID = 1

        /**
         * Arms the watcher for the next time charging starts.
         *
         * Idempotent — scheduling an existing id replaces the pending job. Called from
         * `onEnabled`, [BootReceiver], and each alarm tick, which is also what re-arms it after
         * it has fired. Deliberately **not** called from [onStopJob]: re-arming while the device
         * is still charging is what caused the restart loop described above.
         */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ChargingJobService::class.java))
                .setRequiresCharging(true)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
