package com.etaalert

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.etaalert.data.AppPreferences
import com.etaalert.ui.StatusActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = AppPreferences(this)

        val destination = when {
            prefs.isTracking() -> {
                Intent(this, StatusActivity::class.java)
            }
            else -> {
                Intent(this, SetupActivity::class.java)
            }
        }

        startActivity(destination)
        finish()
    }
}
