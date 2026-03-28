package com.etaalert.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.etaalert.R
import com.etaalert.SetupActivity
import com.etaalert.data.AppPreferences
import com.etaalert.service.EtaForegroundService
import com.etaalert.service.EtaWorker

class StatusActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var tvDestination: TextView
    private lateinit var tvCurrentLocation: TextView
    private lateinit var tvAlertBanner: TextView
    private lateinit var ivStatusCircle: ImageView
    private lateinit var tvEtaMinutes: TextView
    private lateinit var tvEtaTrend: TextView
    private lateinit var tvStatusText: TextView
    private lateinit var tvThresholdInfo: TextView
    private lateinit var tvPollCount: TextView
    private lateinit var btnStopTracking: Button

    private val etaUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val etaMinutes = intent?.getIntExtra(EtaForegroundService.EXTRA_ETA_MINUTES, -1) ?: -1
            val pollCount = intent?.getIntExtra(EtaForegroundService.EXTRA_POLL_COUNT, 0) ?: 0
            val prevEta = intent?.getIntExtra(EtaForegroundService.EXTRA_PREV_ETA, -1) ?: -1
            val address = intent?.getStringExtra(EtaForegroundService.EXTRA_LOCATION_ADDRESS)
            if (etaMinutes >= 0) {
                updateEtaDisplay(etaMinutes, prevEta)
                tvPollCount.text = getString(R.string.poll_count_format, pollCount)
            }
            if (!address.isNullOrEmpty()) {
                tvCurrentLocation.text = getString(R.string.status_current_location, address)
            }
        }
    }

    private val trackingStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            prefs.saveTracking(false)
            val setupIntent = Intent(this@StatusActivity, SetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(setupIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)

        prefs = AppPreferences(this)

        tvDestination = findViewById(R.id.tvDestination)
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation)
        tvAlertBanner = findViewById(R.id.tvAlertBanner)
        ivStatusCircle = findViewById(R.id.ivStatusCircle)
        tvEtaMinutes = findViewById(R.id.tvEtaMinutes)
        tvEtaTrend = findViewById(R.id.tvEtaTrend)
        tvStatusText = findViewById(R.id.tvStatusText)
        tvThresholdInfo = findViewById(R.id.tvThresholdInfo)
        tvPollCount = findViewById(R.id.tvPollCount)
        btnStopTracking = findViewById(R.id.btnStopTracking)

        tvDestination.text = prefs.getDestinationName() ?: getString(R.string.unknown_destination)

        val threshold = prefs.getThreshold()
        val duration = prefs.getDuration()
        tvThresholdInfo.text = getString(R.string.threshold_info, threshold, duration)

        btnStopTracking.setOnClickListener {
            stopTracking()
        }
    }

    override fun onResume() {
        super.onResume()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            etaUpdateReceiver,
            IntentFilter(EtaForegroundService.ACTION_ETA_UPDATE)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            trackingStoppedReceiver,
            IntentFilter(EtaForegroundService.ACTION_TRACKING_STOPPED)
        )

        val lastEta = prefs.getLastEta()
        if (lastEta >= 0) {
            // On UI restore (e.g. rotate/resume), no in-memory prevEta is available — show ETA without trend
            updateEtaDisplay(lastEta, -1)
        } else {
            tvEtaMinutes.text = getString(R.string.eta_calculating)
            tvEtaTrend.text = ""
            tvStatusText.text = getString(R.string.status_calculating)
            ivStatusCircle.setImageResource(R.drawable.ic_red_circle)
        }

        val savedPollCount = prefs.getPollCount()
        if (savedPollCount > 0) {
            tvPollCount.text = getString(R.string.poll_count_format, savedPollCount)
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(etaUpdateReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(trackingStoppedReceiver)
    }

    private fun updateEtaDisplay(etaMinutes: Int, prevEta: Int) {
        val threshold = prefs.getThreshold()
        tvEtaMinutes.text = getString(R.string.eta_minutes_format, etaMinutes)

        if (etaMinutes <= threshold) {
            ivStatusCircle.setImageResource(R.drawable.ic_green_circle)
            tvStatusText.text = getString(R.string.status_good_to_go)
            tvAlertBanner.visibility = android.view.View.VISIBLE
            tvAlertBanner.text = getString(R.string.alert_banner_leave_now, etaMinutes, threshold)
        } else {
            ivStatusCircle.setImageResource(R.drawable.ic_red_circle)
            tvStatusText.text = getString(R.string.status_not_yet)
            tvAlertBanner.visibility = android.view.View.GONE
        }

        // prevEta == -1 means first poll of this session — no comparison to show
        if (prevEta >= 0) {
            when {
                etaMinutes < prevEta -> {
                    tvEtaTrend.text = getString(R.string.eta_trend_decreasing, prevEta - etaMinutes)
                    tvEtaTrend.setTextColor(ContextCompat.getColor(this, R.color.green))
                }
                etaMinutes > prevEta -> {
                    tvEtaTrend.text = getString(R.string.eta_trend_increasing, etaMinutes - prevEta)
                    tvEtaTrend.setTextColor(ContextCompat.getColor(this, R.color.orange))
                }
                else -> {
                    tvEtaTrend.text = getString(R.string.eta_trend_unchanged)
                    tvEtaTrend.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
            }
        } else {
            tvEtaTrend.text = ""
        }
    }

    private fun stopTracking() {
        val serviceIntent = Intent(this, EtaForegroundService::class.java).apply {
            action = EtaForegroundService.ACTION_STOP
        }
        startService(serviceIntent)

        EtaWorker.cancel(this)
        prefs.saveTracking(false)

        val setupIntent = Intent(this, SetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(setupIntent)
        finish()
    }
}
