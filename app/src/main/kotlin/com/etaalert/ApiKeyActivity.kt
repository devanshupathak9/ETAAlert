package com.etaalert

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.etaalert.data.AppPreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ApiKeyActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_key)

        prefs = AppPreferences(this)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSave = findViewById<Button>(R.id.btnSaveApiKey)
        val progressBar = findViewById<ProgressBar>(R.id.progressValidation)

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

            lifecycleScope.launch {
                val status = validateApiKey(key)
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true

                when (status) {
                    "OK", "ZERO_RESULTS", "NOT_FOUND" -> {
                        prefs.saveApiKey(key)
                        Toast.makeText(
                            this@ApiKeyActivity,
                            getString(R.string.api_key_valid),
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(this@ApiKeyActivity, SetupActivity::class.java))
                        finish()
                    }
                    "REQUEST_DENIED" -> {
                        AlertDialog.Builder(this@ApiKeyActivity)
                            .setTitle(getString(R.string.api_key_error_title))
                            .setMessage(getString(R.string.api_key_error_denied))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    "OVER_DAILY_LIMIT", "OVER_QUERY_LIMIT" -> {
                        AlertDialog.Builder(this@ApiKeyActivity)
                            .setTitle(getString(R.string.api_key_error_title))
                            .setMessage(getString(R.string.api_key_error_quota))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    else -> {
                        AlertDialog.Builder(this@ApiKeyActivity)
                            .setTitle(getString(R.string.api_key_error_title))
                            .setMessage(getString(R.string.api_key_error_network))
                            .setPositiveButton(getString(R.string.btn_save_anyway)) { _, _ ->
                                prefs.saveApiKey(key)
                                startActivity(Intent(this@ApiKeyActivity, SetupActivity::class.java))
                                finish()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            }
        }
    }

    private suspend fun validateApiKey(key: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=New+York,NY&destination=Newark,NJ&key=$key"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext "NETWORK_ERROR"
            val body = response.body?.string() ?: return@withContext "NETWORK_ERROR"
            JsonParser.parseString(body).asJsonObject.get("status")?.asString ?: "NETWORK_ERROR"
        } catch (e: Exception) {
            "NETWORK_ERROR"
        }
    }
}
