package com.etaalert.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.etaalert.data.AppPreferences
import java.util.concurrent.TimeUnit

class EtaWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "eta_heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EtaWorker>(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(context)

        if (!prefs.isTracking()) {
            return Result.success()
        }

        if (!isServiceRunning()) {
            val serviceIntent = Intent(context, EtaForegroundService::class.java).apply {
                action = EtaForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager

        @Suppress("DEPRECATION")
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        return runningServices.any { serviceInfo ->
            serviceInfo.service.className == EtaForegroundService::class.java.name
        }
    }
}
