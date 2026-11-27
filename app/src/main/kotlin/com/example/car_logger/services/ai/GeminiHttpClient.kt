package com.example.car_logger.services.ai

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class GeminiHttpClient(
    internal val client: OkHttpClient
) {
    companion object {
        const val MODEL_NAME = "gemini-2.5-flash"
        const val IMAGE_MODEL = "gemini-2.5-flash-image"
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun stripMarkdownCodeFences(rawText: String): String {
            var cleaned = rawText.trim()
            if (cleaned.startsWith("```")) {
                val lines = cleaned.lines().toMutableList()
                if (lines.isNotEmpty() && lines.first().startsWith("```")) lines.removeAt(0)
                if (lines.isNotEmpty() && lines.last().startsWith("```")) lines.removeAt(lines.size - 1)
                cleaned = lines.joinToString("\n").trim()
            }
            return cleaned
        }

        fun parseImageGenerationError(statusCode: Int, rawBody: String): String {
            val rawErrorMsg = try {
                val errorRoot = json.parseToJsonElement(rawBody).jsonObject
                errorRoot["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: rawBody
            } catch (e: Exception) {
                rawBody.ifBlank { "HTTP $statusCode" }
            }

            return when {
                rawErrorMsg.contains("free_tier_requests", ignoreCase = true) ||
                    (rawErrorMsg.contains("limit: 0") && rawErrorMsg.contains("quota", ignoreCase = true)) -> {
                    "Gemini image generation requires a Google AI Studio project with billing enabled. The Free Tier has a limit of 0. Enable billing in Google AI Studio or use 'Choose from Photos'."
                }
                statusCode == 429 -> {
                    "Rate limit or quota exceeded. Image generation requires a Google AI Studio project with billing enabled."
                }
                statusCode == 403 || statusCode == 401 -> {
                    "Invalid or unauthorized Gemini API key. Check your key in Settings."
                }
                else -> "Image generation failed: $rawErrorMsg"
            }
        }
    }

    fun executeImageRequest(
        apiKey: String,
        prompt: String,
        aspectRatio: String?
    ): Response {
        val url = "$GEMINI_BASE_URL/$IMAGE_MODEL:generateContent?key=$apiKey"
        val requestJson = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray {
                    add(JsonPrimitive("IMAGE"))
                })
                if (!aspectRatio.isNullOrBlank()) {
                    put("imageConfig", buildJsonObject {
                        put("aspectRatio", aspectRatio)
                    })
                }
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        return client.newCall(request).execute()
    }

    fun executeMultimodalJsonRequest(
        apiKey: String,
        prompt: String,
        mimeType: String,
        fileBytes: ByteArray
    ): String {
        val url = "$GEMINI_BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
        val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
        val normalizedMime = mimeType.ifBlank { "application/octet-stream" }

        val requestJson = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", normalizedMime)
                                put("data", base64Data)
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${parseImageGenerationError(response.code, bodyString)}")
            }
            val root = json.parseToJsonElement(bodyString).jsonObject
            val parts = root["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?: throw IllegalStateException("Empty candidates from Gemini")

            val rawText = parts.firstNotNullOfOrNull { part ->
                part.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            } ?: throw IllegalStateException("No text part returned from Gemini")

            return stripMarkdownCodeFences(rawText)
        }
    }
}
