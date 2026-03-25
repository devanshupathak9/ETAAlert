package com.etaalert.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DirectionsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getEtaMinutes(
        originLat: Double,
        originLng: Double,
        destinationLat: Double,
        destinationLng: Double,
        apiKey: String
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val origin = "$originLat,$originLng"
            val destination = "$destinationLat,$destinationLng"
            val url = buildUrl(origin, destination, apiKey)
            fetchEta(url)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getEtaMinutesByName(
        originLat: Double,
        originLng: Double,
        destinationName: String,
        apiKey: String
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val origin = "$originLat,$originLng"
            val url = buildUrl(origin, destinationName, apiKey)
            fetchEta(url)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildUrl(origin: String, destination: String, apiKey: String): String {
        val encodedOrigin = java.net.URLEncoder.encode(origin, "UTF-8")
        val encodedDestination = java.net.URLEncoder.encode(destination, "UTF-8")
        return "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=$encodedOrigin" +
                "&destination=$encodedDestination" +
                "&departure_time=now" +
                "&traffic_model=best_guess" +
                "&key=$apiKey"
    }

    private fun fetchEta(url: String): Int? {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val json = JsonParser.parseString(body).asJsonObject

        val status = json.get("status")?.asString
        if (status != "OK") return null

        val routes = json.getAsJsonArray("routes") ?: return null
        if (routes.size() == 0) return null

        val legs = routes[0].asJsonObject.getAsJsonArray("legs") ?: return null
        if (legs.size() == 0) return null

        val leg = legs[0].asJsonObject

        val durationInTraffic = leg.getAsJsonObject("duration_in_traffic")
        val durationSeconds = durationInTraffic?.get("value")?.asInt
            ?: leg.getAsJsonObject("duration")?.get("value")?.asInt
            ?: return null

        return (durationSeconds / 60.0).let { minutes ->
            if (durationSeconds % 60 >= 30) minutes.toInt() + 1 else minutes.toInt()
        }
    }
}
