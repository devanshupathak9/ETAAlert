package com.etaalert

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.etaalert.data.AppPreferences

class ApiKeyActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_key)

        prefs = AppPreferences(this)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSave = findViewById<Button>(R.id.btnSaveApiKey)

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
            prefs.saveApiKey(key)
            Toast.makeText(this, getString(R.string.api_key_saved), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
        }
    }
}
