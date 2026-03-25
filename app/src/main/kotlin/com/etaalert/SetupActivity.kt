package com.etaalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.etaalert.data.AppPreferences
import com.etaalert.service.EtaForegroundService
import com.etaalert.service.EtaWorker
import com.etaalert.ui.StatusActivity

class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    private lateinit var etDestination: EditText
    private lateinit var etThreshold: EditText
    private lateinit var etDuration: EditText

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            requestNotificationPermissionIfNeeded()
        } else {
            Toast.makeText(this, getString(R.string.error_location_permission), Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startTrackingService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = AppPreferences(this)

        etDestination = findViewById(R.id.etDestination)
        etThreshold = findViewById(R.id.etThreshold)
        etDuration = findViewById(R.id.etDuration)
        val btnStart = findViewById<Button>(R.id.btnStartTracking)
        val btnChangeKey = findViewById<Button>(R.id.btnChangeApiKey)

        etThreshold.setText(prefs.getThreshold().toString())
        etDuration.setText(prefs.getDuration().toString())

        val savedDest = prefs.getDestinationName()
        if (!savedDest.isNullOrEmpty()) {
            etDestination.setText(savedDest)
        }

        btnStart.setOnClickListener {
            if (validateAndSaveInputs()) {
                checkLocationPermissionAndStart()
            }
        }

        btnChangeKey.setOnClickListener {
            startActivity(Intent(this, ApiKeyActivity::class.java))
        }
    }

    private fun validateAndSaveInputs(): Boolean {
        val destination = etDestination.text.toString().trim()
        if (destination.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_destination), Toast.LENGTH_SHORT).show()
            return false
        }

        val thresholdText = etThreshold.text.toString().trim()
        val threshold = thresholdText.toIntOrNull()
        if (threshold == null || threshold <= 0) {
            Toast.makeText(this, getString(R.string.error_invalid_threshold), Toast.LENGTH_SHORT).show()
            return false
        }

        val durationText = etDuration.text.toString().trim()
        val duration = durationText.toIntOrNull()
        if (duration == null || duration <= 0) {
            Toast.makeText(this, getString(R.string.error_invalid_duration), Toast.LENGTH_SHORT).show()
            return false
        }

        prefs.saveDestination(destination, 0.0, 0.0)
        prefs.saveThreshold(threshold)
        prefs.saveDuration(duration)
        return true
    }

    private fun checkLocationPermissionAndStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            requestNotificationPermissionIfNeeded()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startTrackingService()
    }

    private fun startTrackingService() {
        val serviceIntent = Intent(this, EtaForegroundService::class.java).apply {
            action = EtaForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        EtaWorker.schedule(this)

        startActivity(Intent(this, StatusActivity::class.java))
        finish()
    }
}
