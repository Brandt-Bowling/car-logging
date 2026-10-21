package com.example.car_logger.services

import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.vehicle.VehicleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class AutoDevVehicleInfo(
    val vin: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val trim: String? = null,
    val body: String? = null,
    val style: String? = null,
    val engine: String? = null,
    val drive: String? = null,
    val transmission: String? = null,
    val isEv: Boolean = false,
    val photoUrl: String? = null,
    val photoUrls: List<String> = emptyList(),
    val vinValid: Boolean = true,
    val errorMessage: String? = null
)

class AutoDevService(
    private val preferencesManager: PreferencesManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "AutoDevService"
        const val DEFAULT_API_KEY = "at_9348733ff052bcb41d54ce21e45503374ba5436be4b952de"
        const val BASE_URL = "https://api.auto.dev"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    private suspend fun getApiKey(): String {
        val configured = preferencesManager.getAutoDevApiKey()
        if (!configured.isNullOrBlank()) {
            return configured.trim()
        }
        return DEFAULT_API_KEY
    }

    suspend fun decodeVin(rawVin: String): AutoDevVehicleInfo = withContext(Dispatchers.IO) {
        val vin = rawVin.trim().uppercase()
        if (vin.isEmpty()) {
            return@withContext AutoDevVehicleInfo(vin = "", vinValid = false, errorMessage = "VIN cannot be empty")
        }

        val apiKey = getApiKey()
        val url = "$BASE_URL/vin/${java.net.URLEncoder.encode(vin, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("X-API-Key", apiKey)
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val root = json.parseToJsonElement(bodyString).jsonObject
                    val vehicle = root["vehicle"]?.jsonObject

                    val make = (root["make"]?.jsonPrimitive?.content ?: vehicle?.get("make")?.jsonPrimitive?.content)?.trim()
                    val model = (root["model"]?.jsonPrimitive?.content ?: vehicle?.get("model")?.jsonPrimitive?.content)?.trim()

                    var year: Int? = root["year"]?.jsonPrimitive?.intOrNull
                    if (year == null && vehicle != null) {
                        year = vehicle["year"]?.jsonPrimitive?.intOrNull
                    }
                    if (year == null && root["years"] != null) {
                        val yearsArr = root["years"]?.jsonArray
                        if (yearsArr != null && yearsArr.isNotEmpty()) {
                            year = yearsArr[0].jsonPrimitive.intOrNull
                        }
                    }

                    val trim = root["trim"]?.jsonPrimitive?.content?.trim()
                    val body = (root["body"]?.jsonPrimitive?.content ?: root["bodyStyle"]?.jsonPrimitive?.content ?: vehicle?.get("bodyStyle")?.jsonPrimitive?.content)?.trim()
                    val style = root["style"]?.jsonPrimitive?.content?.trim()
                    val engine = (root["engine"]?.jsonPrimitive?.content ?: vehicle?.get("engine")?.jsonPrimitive?.content)?.trim()
                    val drive = (root["drive"]?.jsonPrimitive?.content ?: root["drivetrain"]?.jsonPrimitive?.content ?: vehicle?.get("drivetrain")?.jsonPrimitive?.content)?.trim()
                    val transmission = (root["transmission"]?.jsonPrimitive?.content ?: vehicle?.get("transmission")?.jsonPrimitive?.content)?.trim()
                    val fuel = (root["fuel"]?.jsonPrimitive?.content ?: vehicle?.get("fuel")?.jsonPrimitive?.content)?.trim()?.lowercase()

                    val isEv = (engine != null && engine.lowercase().contains("electric")) ||
                            (style != null && style.lowercase().contains("electric")) ||
                            (fuel != null && fuel.contains("electric")) ||
                            (make != null && model != null && VehicleData.isKnownEv(make, model))

                    val vinValid = root["vinValid"]?.jsonPrimitive?.content != "false"
                    val errorMsg = root["error"]?.let {
                        if (it is kotlinx.serialization.json.JsonObject) {
                            it["message"]?.jsonPrimitive?.content ?: it["error"]?.jsonPrimitive?.content
                        } else {
                            it.jsonPrimitive.content
                        }
                    }

                    return@withContext AutoDevVehicleInfo(
                        vin = vin,
                        make = make,
                        model = model,
                        year = year,
                        trim = trim,
                        body = body,
                        style = style,
                        engine = engine,
                        drive = drive,
                        transmission = transmission,
                        isEv = isEv,
                        vinValid = vinValid && errorMsg == null,
                        errorMessage = errorMsg
                    )
                } else {
                    val code = response.code
                    val errorMsg = if (code == 401) {
                        "Authentication required. Please check your auto.dev API key."
                    } else {
                        "Error $code: Failed to decode VIN"
                    }
                    return@withContext AutoDevVehicleInfo(vin = vin, vinValid = false, errorMessage = errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "decodeVin error: ${e.message}", e)
            return@withContext AutoDevVehicleInfo(
                vin = vin,
                vinValid = false,
                errorMessage = "Failed to connect to auto.dev: ${e.message}"
            )
        }
    }

    suspend fun getVehiclePhotos(rawVin: String): List<String> = withContext(Dispatchers.IO) {
        val vin = rawVin.trim().uppercase()
        if (vin.isEmpty()) return@withContext emptyList()

        val apiKey = getApiKey()
        val url = "$BASE_URL/photos/${java.net.URLEncoder.encode(vin, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("X-API-Key", apiKey)
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val root = json.parseToJsonElement(bodyString).jsonObject
                    val data = root["data"]?.jsonObject ?: root

                    val photos = mutableListOf<String>()
                    fun addPhoto(url: String?) {
                        val trimmed = url?.trim()
                        if (!trimmed.isNullOrEmpty() && !photos.contains(trimmed)) {
                            photos.add(trimmed)
                        }
                    }

                    data["retail"]?.let { retail ->
                        if (retail is kotlinx.serialization.json.JsonArray) {
                            for (item in retail) addPhoto(item.jsonPrimitive.content)
                        }
                    }
                    data["wholesale"]?.let { wholesale ->
                        if (wholesale is kotlinx.serialization.json.JsonArray) {
                            for (item in wholesale) addPhoto(item.jsonPrimitive.content)
                        }
                    }
                    return@withContext photos
                }
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getVehiclePhotos error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun decodeAndFetchDetails(rawVin: String): AutoDevVehicleInfo = coroutineScope {
        val vin = rawVin.trim().uppercase()
        val decodedDeferred = async { decodeVin(vin) }
        val photosDeferred = async { getVehiclePhotos(vin) }

        val decoded = decodedDeferred.await()
        val photos = photosDeferred.await().toMutableList()

        if (decoded.make != null && decoded.model != null) {
            val stock = VehicleData.getDefaultImageUrl(decoded.make, decoded.model)
            if (stock != null && !photos.contains(stock)) {
                photos.add(stock)
            }
        }

        val primaryPhoto = photos.firstOrNull()
        decoded.copy(
            photoUrl = primaryPhoto,
            photoUrls = photos
        )
    }
}
