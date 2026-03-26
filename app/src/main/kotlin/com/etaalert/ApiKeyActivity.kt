package com.etaalert

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.etaalert.data.AppPreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiKeyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOW_BACK = "show_back_button"
    }

    private lateinit var prefs: AppPreferences
    private lateinit var tvValidationStatus: TextView

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_key)

        prefs = AppPreferences(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSave = findViewById<Button>(R.id.btnSaveApiKey)
        val progressBar = findViewById<ProgressBar>(R.id.progressValidation)
        tvValidationStatus = findViewById(R.id.tvValidationStatus)

        // Show back button if launched from setup or if an API key already exists
        val showBack = intent.getBooleanExtra(EXTRA_SHOW_BACK, false) || prefs.getApiKey() != null
        if (showBack) {
            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { finish() }
        }

        val existingKey = prefs.getApiKey()
        if (!existingKey.isNullOrEmpty()) {
            etApiKey.setText(existingKey)
        }

        btnSave.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_api_key), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            progressBar.visibility = View.VISIBLE
            tvValidationStatus.visibility = View.GONE

            lifecycleScope.launch {
                val status = validateApiKey(key)
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true

                when (status) {
                    "OK" -> {
                        prefs.saveApiKey(key)
                        showStatus(getString(R.string.api_key_status_valid), isSuccess = true)
                        // Navigate after brief delay so user sees success message
                        launch {
                            kotlinx.coroutines.delay(800)
                            startActivity(Intent(this@ApiKeyActivity, SetupActivity::class.java))
                            finish()
                        }
                    }
                    "PERMISSION_DENIED" -> {
                        showStatus(getString(R.string.api_key_status_invalid), isSuccess = false)
                    }
                    "QUOTA_EXCEEDED" -> {
                        showStatus(getString(R.string.api_key_error_quota), isSuccess = false)
                    }
                    else -> {
                        // Network error — offer to save anyway via Toast + inline note
                        showStatus("Could not reach Google servers. Check your connection.", isSuccess = false)
                        Toast.makeText(
                            this@ApiKeyActivity,
                            getString(R.string.btn_save_anyway) + "? Tap Verify again when online.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun showStatus(message: String, isSuccess: Boolean) {
        tvValidationStatus.text = message
        tvValidationStatus.setTextColor(if (isSuccess) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        tvValidationStatus.visibility = View.VISIBLE
    }

    private suspend fun validateApiKey(key: String): String = withContext(Dispatchers.IO) {
        try {
            val jsonBody = """
                {
                  "origin": {"location": {"latLng": {"latitude": 40.7128, "longitude": -74.0060}}},
                  "destination": {"location": {"latLng": {"latitude": 40.7306, "longitude": -73.9352}}},
                  "travelMode": "DRIVE",
                  "routingPreference": "TRAFFIC_AWARE"
                }
            """.trimIndent()
            val request = Request.Builder()
                .url("https://routes.googleapis.com/directions/v2:computeRoutes")
                .addHeader("X-Goog-Api-Key", key)
                .addHeader("X-Goog-FieldMask", "routes.duration")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "NETWORK_ERROR"
            when (response.code) {
                200 -> "OK"
                403 -> "PERMISSION_DENIED"
                429 -> "QUOTA_EXCEEDED"
                else -> {
                    // Try to extract error status from body
                    try {
                        JsonParser.parseString(body).asJsonObject
                            .getAsJsonObject("error")?.get("status")?.asString ?: "NETWORK_ERROR"
                    } catch (e: Exception) {
                        "NETWORK_ERROR"
                    }
                }
            }
        } catch (e: Exception) {
            "NETWORK_ERROR"
        }
    }
}
