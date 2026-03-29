package com.etaalert

import android.Manifest
import android.content.Context
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
import android.widget.Filter
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private val placesRepo = PlacesRepository()
    private val locationRepo by lazy { LocationRepository(this) }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private lateinit var etDestination: AutoCompleteTextView
    private lateinit var etThreshold: EditText
    private lateinit var etDuration: AutoCompleteTextView
    private lateinit var tvCurrentLocation: TextView
    private lateinit var btnRefreshLocation: ImageButton

    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private var searchJob: Job? = null
    private val suggestionList = mutableListOf<String>()
    private lateinit var suggestionAdapter: NoFilterArrayAdapter

    private val durationOptions = listOf(15, 30, 60, 90, 120)

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

        val savedDest = prefs.getDestinationName()
        if (!savedDest.isNullOrEmpty()) {
            etDestination.setText(savedDest)
        }

        // Set up duration dropdown
        val durationLabels = durationOptions.map { "$it minutes" }
        val durationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, durationLabels)
        etDuration.setAdapter(durationAdapter)
        etDuration.inputType = 0 // disable keyboard

        // Select saved duration (default 60 min)
        val savedDuration = prefs.getDuration()
        val savedIndex = durationOptions.indexOf(savedDuration).takeIf { it >= 0 } ?: 1
        etDuration.setText(durationLabels[savedIndex], false)

        // Places autocomplete setup — NoFilterArrayAdapter bypasses client-side filtering
        // so all API-returned suggestions are shown regardless of how they start
        suggestionAdapter = NoFilterArrayAdapter(this, suggestionList)
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
                    suggestionList.clear()
                    suggestionList.addAll(suggestions)
                    suggestionAdapter.notifyDataSetChanged()
                    if (suggestions.isNotEmpty()) etDestination.showDropDown()
                }
            }
        })

        btnStart.setOnClickListener {
            val apiKey = prefs.getApiKey()
            if (apiKey == null) {
                showApiKeySnackbar("No API key set. Tap \"Add Key\" to enter your Google Maps API key.", "Add Key")
                return@setOnClickListener
            }
            if (!validateAndSaveInputs()) return@setOnClickListener

            btnStart.isEnabled = false
            btnStart.text = "Verifying key\u2026"
            lifecycleScope.launch {
                val status = validateApiKey(apiKey)
                btnStart.isEnabled = true
                btnStart.text = getString(R.string.btn_start_tracking)
                when (status) {
                    "OK", "NETWORK_ERROR" -> checkLocationPermissionAndStart()
                    else -> showApiKeySnackbar("API key is invalid. Tap \"Update Key\" to fix it.")
                }
            }
        }

        btnChangeKey.setOnClickListener {
            startActivity(
                Intent(this, ApiKeyActivity::class.java).apply {
                    putExtra(ApiKeyActivity.EXTRA_SHOW_BACK, true)
                }
            )
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

        val durationText = etDuration.text.toString().trim()
        val duration = durationText.split(" ").firstOrNull()?.toIntOrNull()
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

    private fun showApiKeySnackbar(message: String, actionLabel: String = "Update Key") {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setAction(actionLabel) {
                startActivity(Intent(this, ApiKeyActivity::class.java).apply {
                    putExtra(ApiKeyActivity.EXTRA_SHOW_BACK, true)
                })
            }
            .show()
    }

    private suspend fun validateApiKey(key: String): String = withContext(Dispatchers.IO) {
        try {
            val jsonBody = """{"origin":{"location":{"latLng":{"latitude":40.7128,"longitude":-74.0060}}},"destination":{"location":{"latLng":{"latitude":40.7306,"longitude":-73.9352}}},"travelMode":"DRIVE","routingPreference":"TRAFFIC_AWARE"}"""
            val request = Request.Builder()
                .url("https://routes.googleapis.com/directions/v2:computeRoutes")
                .addHeader("X-Goog-Api-Key", key)
                .addHeader("X-Goog-FieldMask", "routes.duration")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            when (response.code) {
                200 -> "OK"
                403 -> "PERMISSION_DENIED"
                429 -> "QUOTA_EXCEEDED"
                else -> "NETWORK_ERROR"
            }
        } catch (e: Exception) {
            "NETWORK_ERROR"
        }
    }

    /** ArrayAdapter that skips client-side filtering — all items from the API are shown as-is. */
    private inner class NoFilterArrayAdapter(
        context: Context,
        private val items: MutableList<String>
    ) : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, items) {

        private val noOpFilter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
                values = items.toList()
                count = items.size
            }
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = noOpFilter
    }
}
