package com.etaalert.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class InvalidApiKeyException : Exception("API key is invalid or not authorized")

class DirectionsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()
    private val routesUrl = "https://routes.googleapis.com/directions/v2:computeRoutes"

    suspend fun getEtaMinutes(
        originLat: Double,
        originLng: Double,
        destinationLat: Double,
        destinationLng: Double,
        apiKey: String
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val body = """
                {
                  "origin": {"location": {"latLng": {"latitude": $originLat, "longitude": $originLng}}},
                  "destination": {"location": {"latLng": {"latitude": $destinationLat, "longitude": $destinationLng}}},
                  "travelMode": "DRIVE",
                  "routingPreference": "TRAFFIC_AWARE"
                }
            """.trimIndent()
            fetchEta(body, apiKey)
        } catch (e: InvalidApiKeyException) {
            throw e
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
            val escapedDest = destinationName.replace("\"", "\\\"")
            val body = """
                {
                  "origin": {"location": {"latLng": {"latitude": $originLat, "longitude": $originLng}}},
                  "destination": {"address": "$escapedDest"},
                  "travelMode": "DRIVE",
                  "routingPreference": "TRAFFIC_AWARE"
                }
            """.trimIndent()
            fetchEta(body, apiKey)
        } catch (e: InvalidApiKeyException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchEta(jsonBody: String, apiKey: String): Int? {
        val request = Request.Builder()
            .url(routesUrl)
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader("X-Goog-FieldMask", "routes.duration")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()

        if (response.code == 403) throw InvalidApiKeyException()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val json = JsonParser.parseString(body).asJsonObject

        val routes = json.getAsJsonArray("routes") ?: return null
        if (routes.size() == 0) return null

        // Duration is returned as a string like "2930s"
        val durationStr = routes[0].asJsonObject.get("duration")?.asString ?: return null
        val durationSeconds = durationStr.removeSuffix("s").toIntOrNull() ?: return null

        return (durationSeconds / 60.0).let { minutes ->
            if (durationSeconds % 60 >= 30) minutes.toInt() + 1 else minutes.toInt()
        }
    }
}
