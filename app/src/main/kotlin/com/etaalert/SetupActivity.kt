package com.etaalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.etaalert.data.AppPreferences
import com.etaalert.data.LocationRepository
import com.etaalert.data.PlacesRepository
import com.etaalert.service.EtaForegroundService
import com.etaalert.service.EtaWorker
import com.etaalert.ui.StatusActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private val placesRepo = PlacesRepository()
    private val locationRepo by lazy { LocationRepository(this) }

    private lateinit var etDestination: AutoCompleteTextView
    private lateinit var etThreshold: EditText
    private lateinit var etDuration: EditText
    private lateinit var tvCurrentLocation: TextView
    private lateinit var btnRefreshLocation: ImageButton

    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private var searchJob: Job? = null
    private lateinit var suggestionAdapter: ArrayAdapter<String>

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
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation)
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation)
        val btnStart = findViewById<Button>(R.id.btnStartTracking)
        val btnChangeKey = findViewById<Button>(R.id.btnChangeApiKey)

        etThreshold.setText(prefs.getThreshold().toString())
        etDuration.setText(prefs.getDuration().toString())

        val savedDest = prefs.getDestinationName()
        if (!savedDest.isNullOrEmpty()) {
            etDestination.setText(savedDest)
        }

        // Places autocomplete setup
        suggestionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        etDestination.setAdapter(suggestionAdapter)
        etDestination.threshold = 2

        etDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: return
                if (query.length < 2) return
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(400)
                    val apiKey = prefs.getApiKey() ?: return@launch
                    val suggestions = placesRepo.getSuggestions(query, apiKey, currentLat, currentLng)
                    suggestionAdapter.clear()
                    suggestionAdapter.addAll(suggestions)
                    suggestionAdapter.notifyDataSetChanged()
                }
            }
        })

        btnStart.setOnClickListener {
            if (validateAndSaveInputs()) {
                checkLocationPermissionAndStart()
            }
        }

        btnChangeKey.setOnClickListener {
            startActivity(Intent(this, ApiKeyActivity::class.java))
        }

        btnRefreshLocation.setOnClickListener {
            fetchCurrentLocation()
        }

        fetchCurrentLocation()
    }

    private fun fetchCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            tvCurrentLocation.text = getString(R.string.current_location_permission_needed)
            return
        }

        tvCurrentLocation.text = getString(R.string.current_location_detecting)

        lifecycleScope.launch {
            val location = locationRepo.getCurrentLocation(this@SetupActivity)
            if (location != null) {
                currentLat = location.latitude
                currentLng = location.longitude
                val apiKey = prefs.getApiKey()
                if (apiKey != null) {
                    val address = placesRepo.reverseGeocode(location.latitude, location.longitude, apiKey)
                    tvCurrentLocation.text = address
                        ?: "%.4f, %.4f".format(location.latitude, location.longitude)
                } else {
                    tvCurrentLocation.text = "%.4f, %.4f".format(location.latitude, location.longitude)
                }
            } else {
                tvCurrentLocation.text = getString(R.string.current_location_unavailable)
            }
        }
    }

    private fun validateAndSaveInputs(): Boolean {
        val destination = etDestination.text.toString().trim()
        if (destination.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_destination), Toast.LENGTH_SHORT).show()
            return false
        }

        val threshold = etThreshold.text.toString().trim().toIntOrNull()
        if (threshold == null || threshold <= 0) {
            Toast.makeText(this, getString(R.string.error_invalid_threshold), Toast.LENGTH_SHORT).show()
            return false
        }

        val duration = etDuration.text.toString().trim().toIntOrNull()
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
