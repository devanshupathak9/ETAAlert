package com.etaalert.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PlacesRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getSuggestions(
        input: String,
        apiKey: String,
        lat: Double? = null,
        lng: Double? = null
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val encodedInput = java.net.URLEncoder.encode(input, "UTF-8")
            val locationParam = if (lat != null && lng != null) "&location=$lat,$lng&radius=50000" else ""
            val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                    "?input=$encodedInput$locationParam&key=$apiKey"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JsonParser.parseString(body).asJsonObject
            if (json.get("status")?.asString != "OK") return@withContext emptyList()

            val predictions = json.getAsJsonArray("predictions") ?: return@withContext emptyList()
            predictions.map { it.asJsonObject.get("description")?.asString ?: "" }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun reverseGeocode(lat: Double, lng: Double, apiKey: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JsonParser.parseString(body).asJsonObject
            if (json.get("status")?.asString != "OK") return@withContext null

            val results = json.getAsJsonArray("results") ?: return@withContext null
            if (results.size() == 0) return@withContext null
            results[0].asJsonObject.get("formatted_address")?.asString
        } catch (e: Exception) {
            null
        }
    }
}
