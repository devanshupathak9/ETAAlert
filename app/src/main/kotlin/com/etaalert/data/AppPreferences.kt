package com.etaalert.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "eta_alert_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DESTINATION_NAME = "destination_name"
        private const val KEY_DESTINATION_LAT = "destination_lat"
        private const val KEY_DESTINATION_LNG = "destination_lng"
        private const val KEY_THRESHOLD = "threshold_minutes"
        private const val KEY_DURATION = "duration_minutes"
        private const val KEY_TRACKING_START = "tracking_start_time"
        private const val KEY_IS_TRACKING = "is_tracking"
        private const val KEY_LAST_ETA = "last_eta_minutes"
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun getApiKey(): String? {
        return prefs.getString(KEY_API_KEY, null)
    }

    fun saveDestination(name: String, lat: Double, lng: Double) {
        prefs.edit()
            .putString(KEY_DESTINATION_NAME, name)
            .putFloat(KEY_DESTINATION_LAT, lat.toFloat())
            .putFloat(KEY_DESTINATION_LNG, lng.toFloat())
            .apply()
    }

    fun getDestinationName(): String? {
        return prefs.getString(KEY_DESTINATION_NAME, null)
    }

    fun getDestinationLat(): Double {
        return prefs.getFloat(KEY_DESTINATION_LAT, 0f).toDouble()
    }

    fun getDestinationLng(): Double {
        return prefs.getFloat(KEY_DESTINATION_LNG, 0f).toDouble()
    }

    fun saveThreshold(minutes: Int) {
        prefs.edit().putInt(KEY_THRESHOLD, minutes).apply()
    }

    fun getThreshold(): Int {
        return prefs.getInt(KEY_THRESHOLD, 20)
    }

    fun saveDuration(minutes: Int) {
        prefs.edit().putInt(KEY_DURATION, minutes).apply()
    }

    fun getDuration(): Int {
        return prefs.getInt(KEY_DURATION, 60)
    }

    fun saveTrackingStartTime(time: Long) {
        prefs.edit().putLong(KEY_TRACKING_START, time).apply()
    }

    fun getTrackingStartTime(): Long {
        return prefs.getLong(KEY_TRACKING_START, 0L)
    }

    fun saveTracking(active: Boolean) {
        prefs.edit().putBoolean(KEY_IS_TRACKING, active).apply()
    }

    fun isTracking(): Boolean {
        return prefs.getBoolean(KEY_IS_TRACKING, false)
    }

    fun saveLastEta(minutes: Int) {
        prefs.edit().putInt(KEY_LAST_ETA, minutes).apply()
    }

    fun getLastEta(): Int {
        return prefs.getInt(KEY_LAST_ETA, -1)
    }
}
