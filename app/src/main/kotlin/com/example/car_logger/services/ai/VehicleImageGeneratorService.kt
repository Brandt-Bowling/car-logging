package com.example.car_logger.services.ai

import android.util.Base64
import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class VehicleImageGeneratorService(
    private val preferencesManager: PreferencesManager? = null,
    private val httpClient: GeminiHttpClient
) {
    companion object {
        private const val TAG = "VehicleImageGeneratorService"
    }

    fun buildVehiclePrompt(
        year: Int,
        make: String,
        model: String,
        color: String,
        trim: String? = null,
        environment: String = "clean paved driveway"
    ): String {
        val trimPart = trim?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        val envPart = environment.takeIf { it.isNotBlank() }?.let { " parked on a $it" } ?: ""
        return "A realistic, high-quality, professional exterior automotive photograph of a $year $make $model$trimPart in $color paint, front three-quarters angle,$envPart in bright natural daylight, clean background, sharp focus, 4k."
    }

    suspend fun generateVehicleImage(
        prompt: String,
        aspectRatio: String = "16:9"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager?.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Add a Gemini API key in Settings to generate photos.")
            )
        }

        try {
            var response = httpClient.executeImageRequest(apiKey, prompt, aspectRatio)
            var bodyString = response.body?.string() ?: ""

            // If imageConfig is rejected by API/proxy, retry once without imageConfig
            if (!response.isSuccessful && response.code == 400 && bodyString.contains("imageConfig")) {
                response.close()
                response = httpClient.executeImageRequest(apiKey, prompt, aspectRatio = null)
                bodyString = response.body?.string() ?: ""
            }

            response.use {
                if (!response.isSuccessful) {
                    val friendlyMsg = GeminiHttpClient.parseImageGenerationError(response.code, bodyString)
                    return@withContext Result.failure(Exception(friendlyMsg))
                }

                val root = GeminiHttpClient.json.parseToJsonElement(bodyString).jsonObject

                // 1. Extract base64 image data from candidates[0].content.parts[].inlineData.data
                var base64Bytes: String? = null
                var textExplanation: String? = null
                val candidates = root["candidates"]?.jsonArray
                val firstCandidate = candidates?.firstOrNull()?.jsonObject
                val parts = firstCandidate?.get("content")?.jsonObject?.get("parts")?.jsonArray

                if (parts != null) {
                    for (part in parts) {
                        val partObj = part.jsonObject
                        val inlineData = partObj["inlineData"]?.jsonObject
                        val data = inlineData?.get("data")?.jsonPrimitive?.content
                            ?: inlineData?.get("bytesBase64Encoded")?.jsonPrimitive?.content
                        if (!data.isNullOrBlank()) {
                            base64Bytes = data
                            break
                        }
                        if (partObj["text"] != null) {
                            textExplanation = partObj["text"]?.jsonPrimitive?.content
                        }
                    }
                }

                // 2. Fallback to legacy Imagen predictions format if present
                if (base64Bytes == null) {
                    val predictions = root["predictions"]?.jsonArray
                    val firstPrediction = predictions?.firstOrNull()?.jsonObject
                    base64Bytes = firstPrediction?.get("bytesBase64Encoded")?.jsonPrimitive?.content
                }

                if (base64Bytes.isNullOrBlank()) {
                    val detail = textExplanation?.let { ": $it" } ?: ""
                    return@withContext Result.failure(Exception("No image returned from Gemini AI$detail"))
                }

                val imageBytes = Base64.decode(base64Bytes, Base64.DEFAULT)
                return@withContext Result.success(imageBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating vehicle image", e)
            return@withContext Result.failure(e)
        }
    }
}
