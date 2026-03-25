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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.etaalert.R
import com.etaalert.SetupActivity
import com.etaalert.data.AppPreferences
import com.etaalert.service.EtaForegroundService
import com.etaalert.service.EtaWorker

class StatusActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var tvDestination: TextView
    private lateinit var ivStatusCircle: ImageView
    private lateinit var tvEtaMinutes: TextView
    private lateinit var tvStatusText: TextView
    private lateinit var tvThresholdInfo: TextView
    private lateinit var btnStopTracking: Button

    private val etaUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val etaMinutes = intent?.getIntExtra(EtaForegroundService.EXTRA_ETA_MINUTES, -1) ?: -1
            if (etaMinutes >= 0) {
                updateEtaDisplay(etaMinutes)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)

        prefs = AppPreferences(this)

        tvDestination = findViewById(R.id.tvDestination)
        ivStatusCircle = findViewById(R.id.ivStatusCircle)
        tvEtaMinutes = findViewById(R.id.tvEtaMinutes)
        tvStatusText = findViewById(R.id.tvStatusText)
        tvThresholdInfo = findViewById(R.id.tvThresholdInfo)
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

        val lastEta = prefs.getLastEta()
        if (lastEta >= 0) {
            updateEtaDisplay(lastEta)
        } else {
            tvEtaMinutes.text = getString(R.string.eta_calculating)
            tvStatusText.text = getString(R.string.status_calculating)
            ivStatusCircle.setImageResource(R.drawable.ic_red_circle)
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(etaUpdateReceiver)
    }

    private fun updateEtaDisplay(etaMinutes: Int) {
        val threshold = prefs.getThreshold()
        tvEtaMinutes.text = getString(R.string.eta_minutes_format, etaMinutes)

        if (etaMinutes <= threshold) {
            ivStatusCircle.setImageResource(R.drawable.ic_green_circle)
            tvStatusText.text = getString(R.string.status_good_to_go)
        } else {
            ivStatusCircle.setImageResource(R.drawable.ic_red_circle)
            tvStatusText.text = getString(R.string.status_not_yet)
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
