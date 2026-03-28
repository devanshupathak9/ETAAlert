package com.etaalert.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.etaalert.R
import com.etaalert.data.AppPreferences
import com.etaalert.data.DirectionsRepository
import com.etaalert.data.InvalidApiKeyException
import com.etaalert.data.LocationRepository
import com.etaalert.data.PlacesRepository
import com.etaalert.domain.EtaEvaluator
import com.etaalert.ui.StatusActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EtaForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.etaalert.START"
        const val ACTION_STOP = "com.etaalert.STOP"
        const val ACTION_ETA_UPDATE = "com.etaalert.ETA_UPDATE"
        const val ACTION_TRACKING_STOPPED = "com.etaalert.TRACKING_STOPPED"
        const val EXTRA_ETA_MINUTES = "eta_minutes"
        const val EXTRA_POLL_COUNT = "poll_count"
        const val EXTRA_PREV_ETA = "prev_eta"
        const val EXTRA_LOCATION_ADDRESS = "location_address"

        private const val CHANNEL_TRACKING = "eta_tracking"
        private const val CHANNEL_ALERT = "eta_alert"
        private const val NOTIFICATION_ID_TRACKING = 1001
        private const val NOTIFICATION_ID_ALERT = 1002
        private const val POLL_INTERVAL_MS = 3 * 60 * 1000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // In-memory only — not persisted across sessions
    private var lastPollEta: Int = -1

    private lateinit var prefs: AppPreferences
    private lateinit var directionsRepo: DirectionsRepository
    private lateinit var locationRepo: LocationRepository
    private lateinit var placesRepo: PlacesRepository
    private lateinit var evaluator: EtaEvaluator
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        directionsRepo = DirectionsRepository()
        locationRepo = LocationRepository(this)
        placesRepo = PlacesRepository()
        evaluator = EtaEvaluator()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        releaseWakeLock()
        prefs.saveTracking(false)
    }

    private fun startTracking() {
        val trackingNotification = buildTrackingNotification("Initializing...")
        startForeground(NOTIFICATION_ID_TRACKING, trackingNotification)
        prefs.saveTracking(true)
        prefs.saveTrackingStartTime(System.currentTimeMillis())
        prefs.savePollCount(0)
        prefs.saveLastEta(-1)
        lastPollEta = -1  // Reset in-memory comparison baseline for this session
        startPollingLoop()
        scheduleDurationStop()
    }

    private fun scheduleDurationStop() {
        serviceScope.launch {
            val startTime = prefs.getTrackingStartTime()
            val durationMs = prefs.getDuration() * 60 * 1000L
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = durationMs - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            if (pollingJob?.isActive == true) {
                updateTrackingNotification("Tracking duration ended. Stopping.")
                stopTracking()
            }
        }
    }

    private fun stopTracking() {
        pollingJob?.cancel()
        prefs.saveTracking(false)
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_TRACKING_STOPPED))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (true) {
                if (isDurationExpired()) {
                    updateTrackingNotification("Tracking duration ended. Stopping.")
                    stopTracking()
                    break
                }
                performPoll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun performPoll() {
        val pollCount = prefs.incrementAndGetPollCount()

        val apiKey = prefs.getApiKey() ?: run {
            stopTracking()
            return
        }

        val location = locationRepo.getCurrentLocation(this) ?: run {
            if (isDurationExpired()) {
                updateTrackingNotification("Poll #$pollCount — Tracking duration expired. Stopping.")
                stopTracking()
            } else {
                updateTrackingNotification("Poll #$pollCount — Could not get location. Retrying...")
            }
            return
        }

        val destinationName = prefs.getDestinationName() ?: run {
            stopTracking()
            return
        }

        val etaMinutes = try {
            directionsRepo.getEtaMinutesByName(
                originLat = location.latitude,
                originLng = location.longitude,
                destinationName = destinationName,
                apiKey = apiKey
            ) ?: run {
                if (isDurationExpired()) {
                    updateTrackingNotification("Poll #$pollCount — Tracking duration expired. Stopping.")
                    stopTracking()
                } else {
                    updateTrackingNotification("Poll #$pollCount — Unable to fetch ETA. Retrying...")
                }
                return
            }
        } catch (e: InvalidApiKeyException) {
            fireInvalidApiKeyNotification()
            stopTracking()
            return
        }

        val prevEta = lastPollEta  // In-memory only; never persisted across sessions
        lastPollEta = etaMinutes
        prefs.saveLastEta(etaMinutes)

        val locationAddress = placesRepo.reverseGeocode(location.latitude, location.longitude, apiKey)
            ?: "%.4f, %.4f".format(location.latitude, location.longitude)

        broadcastEtaUpdate(etaMinutes, pollCount, prevEta, locationAddress)

        val threshold = prefs.getThreshold()
        val duration = prefs.getDuration()
        val startTime = prefs.getTrackingStartTime()

        val result = evaluator.evaluate(etaMinutes, threshold, startTime, duration)

        when {
            result.shouldAlert -> {
                fireAlertNotification(etaMinutes, threshold)
                stopTracking()
            }
            result.trackingExpired -> {
                updateTrackingNotification("Poll #$pollCount — Tracking duration expired. Stopping.")
                stopTracking()
            }
            else -> {
                updateTrackingNotification("Poll #$pollCount — ETA: $etaMinutes min (target: ≤$threshold min)")
            }
        }
    }

    private fun isDurationExpired(): Boolean {
        val startTime = prefs.getTrackingStartTime()
        val duration = prefs.getDuration()
        return System.currentTimeMillis() > startTime + (duration * 60 * 1000L)
    }

    private fun broadcastEtaUpdate(etaMinutes: Int, pollCount: Int, prevEta: Int, locationAddress: String) {
        val intent = Intent(ACTION_ETA_UPDATE).apply {
            putExtra(EXTRA_ETA_MINUTES, etaMinutes)
            putExtra(EXTRA_POLL_COUNT, pollCount)
            putExtra(EXTRA_PREV_ETA, prevEta)
            putExtra(EXTRA_LOCATION_ADDRESS, locationAddress)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannels() {
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING,
            "ETA Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while ETA is being monitored"
            setShowBadge(false)
        }

        val alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alertAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "ETA Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Fires when it's time to leave"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(alertSound, alertAudioAttributes)
        }

        notificationManager.createNotificationChannel(trackingChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    private fun buildTrackingNotification(contentText: String): Notification {
        val statusIntent = Intent(this, StatusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, statusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tracking ETA...")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    private fun updateTrackingNotification(contentText: String) {
        val notification = buildTrackingNotification(contentText)
        notificationManager.notify(NOTIFICATION_ID_TRACKING, notification)
    }

    private fun fireAlertNotification(etaMinutes: Int, threshold: Int) {
        val statusIntent = Intent(this, StatusActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, statusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = PendingIntent.getActivity(
            this, 3, statusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val destination = prefs.getDestinationName() ?: "your destination"
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to leave now!")
            .setContentText("ETA: $etaMinutes min to $destination")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your ETA to $destination is now $etaMinutes min — below your $threshold min threshold. Head out now!")
                    .setBigContentTitle("Time to leave now!")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setVibrate(longArrayOf(0, 600, 200, 600, 200, 600, 200, 600))
            .setOnlyAlertOnce(false)
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    private fun fireInvalidApiKeyNotification() {
        val apiKeyIntent = Intent(this, com.etaalert.ApiKeyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 2, apiKeyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Invalid API Key")
            .setContentText("Your Google Maps API key is not working. Tap to update it.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ETAAlert:TrackingWakeLock"
        ).apply {
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
