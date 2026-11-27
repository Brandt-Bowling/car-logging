package com.example.car_logger.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.di.AppContainer
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Serializable
data class ParsedReceiptResult(
    val title: String,
    val date: String,
    val odometer: Int,
    val cost: Double? = null,
    val description: String? = null,
    val source: String = "filename",
    val error: String? = null
)

class GeminiAiService(
    private val preferencesManager: PreferencesManager? = null,
    internal val client: OkHttpClient = AppContainer.newSharedHttpClient(
        connectTimeoutSeconds = AppContainer.GEMINI_CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds = AppContainer.READ_TIMEOUT_SECONDS
    )
) {
    companion object {
        private const val TAG = "GeminiAiService"
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val IMAGE_MODEL = "gemini-2.5-flash-image"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
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

        internal fun parseChargerUsageJsonResponse(
            rawJson: String,
            fileName: String,
            fallbackDateIso: String = LocalDate.now().toString()
        ): List<ParsedChargingSessionDraft> {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val sessionsArray = root["sessions"]?.jsonArray.orEmpty()

            return sessionsArray.mapNotNull { item ->
                val obj = item.jsonObject
                val kwh = obj["kwh"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                if (kwh <= 0.0) return@mapNotNull null
                val startIso = ElectricityImportParser.parseDateToLocalDate(
                    obj["startDate"]?.jsonPrimitive?.contentOrNull
                )?.toString() ?: fallbackDateIso
                val endIso = ElectricityImportParser.parseDateToLocalDate(
                    obj["endDate"]?.jsonPrimitive?.contentOrNull
                )?.toString() ?: startIso
                val chargerName = obj["chargerName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val parsedCost = obj["directCost"]?.jsonPrimitive?.doubleOrNull?.takeIf { it >= 0.0 }
                val rawVenue = obj["venue"]?.jsonPrimitive?.contentOrNull?.uppercase()?.trim()
                val venue = when (rawVenue) {
                    ElectricityImportParser.VENUE_HOME,
                    ElectricityImportParser.VENUE_WORK,
                    ElectricityImportParser.VENUE_PUBLIC -> rawVenue
                    else -> ElectricityImportParser.classifyVenue(chargerName, parsedCost)
                }
                val isSummary = obj["isSummary"]?.jsonPrimitive?.booleanOrNull
                    ?: (endIso != startIso)

                ParsedChargingSessionDraft(
                    startDateIso = startIso,
                    endDateIso = endIso,
                    kwh = kwh,
                    venue = venue,
                    directCost = when (venue) {
                        ElectricityImportParser.VENUE_WORK -> 0.0
                        ElectricityImportParser.VENUE_PUBLIC -> parsedCost
                        else -> null
                    },
                    chargerName = chargerName ?: fileName.substringBeforeLast("."),
                    isSummary = isSummary
                )
            }
        }

        internal fun parseUtilityBillJsonResponse(
            rawJson: String,
            fileName: String
        ): ParsedUtilityBillDraft {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val fallback = ElectricityImportParser.fallbackBillDraftFromFilename(fileName)

            val providerName = root["providerName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: fallback.providerName
            val startDate = ElectricityImportParser.parseDateToLocalDate(
                root["billingStartDate"]?.jsonPrimitive?.contentOrNull
            )?.toString() ?: fallback.startDateIso
            val endDate = ElectricityImportParser.parseDateToLocalDate(
                root["billingEndDate"]?.jsonPrimitive?.contentOrNull
            )?.toString() ?: fallback.endDateIso
            val totalKwh = root["totalKwh"]?.jsonPrimitive?.doubleOrNull?.coerceAtLeast(0.0) ?: 0.0
            val totalAmount = root["totalAmount"]?.jsonPrimitive?.doubleOrNull?.coerceAtLeast(0.0) ?: 0.0
            val fixedCharges = root["fixedCharges"]?.jsonPrimitive?.doubleOrNull?.coerceAtLeast(0.0) ?: 0.0

            val offPeakSupplyRate = (root["offPeakSupplyRate"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0.0 }
            val onPeakSupplyRate = (root["onPeakSupplyRate"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0.0 }
            val distributionRatePerKwh = (root["distributionRatePerKwh"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0.0 }
            val offPeakKwh = (root["offPeakKwh"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0.0 }
            val onPeakKwh = (root["onPeakKwh"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0.0 }
            val isTouPlan = (root["isTouPlan"] as? JsonPrimitive)?.booleanOrNull
                ?: (offPeakSupplyRate != null || onPeakSupplyRate != null)

            val breakdownItems = (root["breakdownItems"] as? JsonArray)
                ?.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val label = (obj["label"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val rawCategory = (obj["category"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?.uppercase() ?: "TAX_OR_OTHER"
                    val normalizedCategory = when (rawCategory) {
                        "OFF_PEAK_SUPPLY", "ON_PEAK_SUPPLY", "DISTRIBUTION", "FIXED", "TAX_OR_OTHER" -> rawCategory
                        else -> when {
                            rawCategory.contains("OFF") && rawCategory.contains("SUPPLY") -> "OFF_PEAK_SUPPLY"
                            rawCategory.contains("ON") && rawCategory.contains("SUPPLY") -> "ON_PEAK_SUPPLY"
                            rawCategory.contains("DIST") || rawCategory.contains("DELIV") -> "DISTRIBUTION"
                            rawCategory.contains("FIX") -> "FIXED"
                            else -> "TAX_OR_OTHER"
                        }
                    }
                    val itemKwh = (obj["kwh"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0.0 }
                    val itemRate = (obj["ratePerKwh"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0.0 }
                    val itemAmount = (obj["amount"] as? JsonPrimitive)?.doubleOrNull
                    ParsedBillLineItem(
                        label = label,
                        category = normalizedCategory,
                        kwh = itemKwh,
                        ratePerKwh = itemRate,
                        amount = itemAmount
                    )
                }
                .orEmpty()

            return ParsedUtilityBillDraft(
                providerName = providerName,
                startDateIso = startDate,
                endDateIso = endDate,
                totalKwh = totalKwh,
                totalAmount = totalAmount,
                fixedCharges = fixedCharges,
                source = "gemini",
                fileName = fileName,
                isTouPlan = isTouPlan,
                offPeakSupplyRate = offPeakSupplyRate,
                onPeakSupplyRate = onPeakSupplyRate,
                distributionRatePerKwh = distributionRatePerKwh,
                offPeakKwh = offPeakKwh,
                onPeakKwh = onPeakKwh,
                breakdownItems = breakdownItems
            )
        }

        fun parseFromFileName(fileName: String): ParsedReceiptResult {
            val cleanName = fileName.substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")

            // 1. Try to extract Date (YYYY MM DD or MMDDYYYY)
            var date = LocalDate.now().toString()
            val datePattern = Pattern.compile("""(?<!\d)(20\d{2})[-_/.](0?[1-9]|1[0-2])[-_/.](0?[1-9]|[12]\d|3[01])(?!\d)""")
            val dateMatcher = datePattern.matcher(fileName)
            if (dateMatcher.find()) {
                val y = dateMatcher.group(1)
                val m = dateMatcher.group(2)?.padStart(2, '0') ?: "01"
                val d = dateMatcher.group(3)?.padStart(2, '0') ?: "01"
                date = "$y-$m-$d"
            }

            // 2. Try to extract Odometer (e.g. 45000mi, 45k, 45000 mi, odo 45000)
            var odometer = 0
            val odoPattern = Pattern.compile("""(?i)\b(?:odo(?:meter)?\s*[:=]?\s*)?(\d{2,6})\s*(?:k|mi|miles|km)?\b""")
            val odoMatcher = odoPattern.matcher(cleanName)
            while (odoMatcher.find()) {
                val numStr = odoMatcher.group(1)
                val num = numStr?.toIntOrNull() ?: 0
                if (num in 500..500000 && num > odometer) {
                    odometer = num
                }
            }

            // 3. Try to extract Cost (e.g. $65.40, $65, 65.40 dollars)
            var cost: Double? = null
            val costPattern = Pattern.compile("""\$(?:[0-9]+(?:\.[0-9]{2})?)""")
            val costMatcher = costPattern.matcher(cleanName)
            if (costMatcher.find()) {
                cost = costMatcher.group(0)?.removePrefix("$")?.toDoubleOrNull()
            }

            // 4. Extract Title based on common keywords
            val lower = cleanName.lowercase()
            val title = when {
                lower.contains("oil") -> "Oil & Filter Change"
                lower.contains("tire") && lower.contains("rotat") -> "Tire Rotation"
                lower.contains("brake") -> "Brake Service"
                lower.contains("battery") -> "Battery Replacement"
                lower.contains("inspect") -> "State Inspection"
                lower.contains("alignment") -> "Wheel Alignment"
                lower.contains("transmission") -> "Transmission Service"
                lower.contains("spark") -> "Spark Plug Replacement"
                lower.contains("coolant") || lower.contains("flush") -> "Coolant Flush"
                else -> cleanName.take(30).trim().ifEmpty { "Maintenance Service" }
            }

            return ParsedReceiptResult(
                title = title,
                date = date,
                odometer = odometer,
                cost = cost,
                description = "Imported from $fileName"
            )
        }
    }

    suspend fun parseReceipt(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray?
    ): ParsedReceiptResult = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager?.getGeminiApiKey()

        if (!apiKey.isNullOrBlank() && fileBytes != null && fileBytes.isNotEmpty()) {
            try {
                val geminiResult = parseWithGemini(apiKey, mimeType, fileBytes)
                return@withContext geminiResult.copy(source = "gemini")
            } catch (e: Exception) {
                Log.w(TAG, "Gemini parsing failed, falling back to filename: ${e.message}")
                val fallback = parseFromFileName(fileName)
                return@withContext fallback.copy(source = "filename", error = e.message)
            }
        }

        return@withContext parseFromFileName(fileName)
    }

    private suspend fun parseWithGemini(
        apiKey: String,
        mimeType: String,
        fileBytes: ByteArray
    ): ParsedReceiptResult {
        val config = generationConfig {
            responseMimeType = "application/json"
        }
        val generativeModel = GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = config
        )

        val systemPrompt = """
            You are an expert car maintenance assistant. Analyze the provided receipt image or document and extract the relevant service fields as a structured JSON object.
            
            1. title: Concise title of the primary service (e.g. "Oil Change", "Brake Pad Replacement", "Tire Rotation", "State Inspection", "Battery Replacement").
            2. date: Service date in YYYY-MM-DD format. If not found, use today's date.
            3. odometer: Odometer mileage as an integer (no units, decimals, or commas). If not found, output null.
            4. cost: Total grand cost charged as a double. If not found, output null.
            5. description: Brief summary of work performed (max 2 sentences).
            
            Return ONLY a valid JSON object matching:
            {
              "title": "...",
              "date": "YYYY-MM-DD",
              "odometer": 12345,
              "cost": 45.67,
              "description": "..."
            }
        """.trimIndent()

        val bitmap: Bitmap? = if (mimeType.startsWith("image/")) {
            BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
        } else {
            null
        }

        val promptContent = content {
            text(systemPrompt)
            if (bitmap != null) {
                image(bitmap)
            }
        }

        val response = generativeModel.generateContent(promptContent)
        val text = response.text?.trim() ?: throw IllegalStateException("Empty response from Gemini")

        var cleanedText = text
        if (cleanedText.startsWith("```")) {
            val lines = cleanedText.lines().toMutableList()
            if (lines.isNotEmpty() && lines.first().startsWith("```")) lines.removeAt(0)
            if (lines.isNotEmpty() && lines.last().startsWith("```")) lines.removeAt(lines.size - 1)
            cleanedText = lines.joinToString("\n").trim()
        }

        val root = json.parseToJsonElement(cleanedText).jsonObject
        val title = root["title"]?.jsonPrimitive?.content ?: "Maintenance Service"
        val date = root["date"]?.jsonPrimitive?.content ?: LocalDate.now().toString()
        val odometer = root["odometer"]?.jsonPrimitive?.intOrNull ?: 0
        val cost = root["cost"]?.jsonPrimitive?.doubleOrNull
        val description = root["description"]?.jsonPrimitive?.content

        return ParsedReceiptResult(
            title = title,
            date = date,
            odometer = odometer,
            cost = cost,
            description = description
        )
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
            var response = executeImageRequest(apiKey, prompt, aspectRatio)
            var bodyString = response.body?.string() ?: ""

            // If imageConfig is rejected by API/proxy, retry once without imageConfig
            if (!response.isSuccessful && response.code == 400 && bodyString.contains("imageConfig")) {
                response.close()
                response = executeImageRequest(apiKey, prompt, aspectRatio = null)
                bodyString = response.body?.string() ?: ""
            }

            response.use {
                if (!response.isSuccessful) {
                    val friendlyMsg = parseImageGenerationError(response.code, bodyString)
                    return@withContext Result.failure(Exception(friendlyMsg))
                }

                val root = json.parseToJsonElement(bodyString).jsonObject

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

    private fun executeImageRequest(
        apiKey: String,
        prompt: String,
        aspectRatio: String?
    ): okhttp3.Response {
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

    suspend fun parseUtilityBill(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray?
    ): ParsedUtilityBillDraft = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager?.getGeminiApiKey()
        if (apiKey.isNullOrBlank() || fileBytes == null || fileBytes.isEmpty()) {
            val hint = if (apiKey.isNullOrBlank()) {
                "Add a Gemini API key in Settings to scan electric bills automatically."
            } else {
                "Couldn't read bill file."
            }
            return@withContext ElectricityImportParser.fallbackBillDraftFromFilename(fileName, error = hint)
        }

        val prompt = """
            You are an expert utility bill analyzer. Analyze the attached residential electric utility bill (PDF or image) and extract the electricity usage, billing amounts, Time-of-Use (TOU) rates, and supply vs. delivery/distribution charges as a JSON object:
            1. providerName: Utility company name (e.g., "DTE Energy", "PG&E", "Consumers Energy", "Con Edison").
            2. billingStartDate: Billing period start date in YYYY-MM-DD format.
            3. billingEndDate: Billing period end date in YYYY-MM-DD format.
            4. totalKwh: Total billed electricity consumption in kWh as a number.
            5. totalAmount: Total electric charges for this billing period in USD (excluding prior balance, late fees, or separate natural gas charges if combined bill) as a number.
            6. fixedCharges: ONLY flat monthly non-volumetric charges that do NOT vary with kWh (such as customer charge, service availability fee, meter fee, connection fee) in USD as a number. Do NOT include per-kWh distribution/delivery charges here. Output 0.0 if none listed.
            7. isTouPlan: true if the bill uses Time-of-Use (On-Peak / Off-Peak / Mid-Peak) energy or supply rates; false if it uses a flat or single rate.
            8. offPeakSupplyRate: Off-peak per-kWh power supply / energy rate in USD/kWh as a number (include any per-kWh power supply cost recovery or fuel adjustment rider if itemized; null if not TOU or not listed).
            9. onPeakSupplyRate: On-peak per-kWh power supply / energy rate in USD/kWh as a number (null if not TOU or not listed).
            10. distributionRatePerKwh: Sum of ALL per-kWh volumetric distribution / delivery / transmission / energy waste reduction / volumetric surcharge rates in USD/kWh as a number. If the bill shows volumetric distribution/delivery dollars instead of a printed rate, divide volumetric distribution dollars by totalKwh. Output 0.0 if bundled into supply rate, or null if unknown.
            11. offPeakKwh: Off-peak consumption in kWh as a number if listed, otherwise null.
            12. onPeakKwh: On-peak consumption in kWh as a number if listed, otherwise null.
            13. breakdownItems: An array of the itemized electric charges/credits shown on the bill so the user can see how the bill was broken down. Each item must have:
                - label: Exact or concise line-item name from the bill (e.g., "Off-Peak Power Supply", "On-Peak Power Supply", "Power Supply Cost Recovery", "Distribution Charge", "Customer Charge", "State Sales Tax").
                - category: One of "OFF_PEAK_SUPPLY", "ON_PEAK_SUPPLY", "DISTRIBUTION", "FIXED", or "TAX_OR_OTHER".
                - kwh: Usage in kWh if volumetric, otherwise null.
                - ratePerKwh: Rate in USD/kWh if printed or applicable, otherwise null.
                - amount: Dollar total in USD for that line item as a number (negative if credit), otherwise null.

            Return ONLY a valid JSON object matching:
            {
              "providerName": "...",
              "billingStartDate": "YYYY-MM-DD",
              "billingEndDate": "YYYY-MM-DD",
              "totalKwh": 1250.0,
              "totalAmount": 195.40,
              "fixedCharges": 12.50,
              "isTouPlan": true,
              "offPeakSupplyRate": 0.082,
              "onPeakSupplyRate": 0.185,
              "distributionRatePerKwh": 0.068,
              "offPeakKwh": 980.0,
              "onPeakKwh": 270.0,
              "breakdownItems": [
                { "label": "Off-Peak Power Supply", "category": "OFF_PEAK_SUPPLY", "kwh": 980.0, "ratePerKwh": 0.082, "amount": 80.36 },
                { "label": "On-Peak Power Supply", "category": "ON_PEAK_SUPPLY", "kwh": 270.0, "ratePerKwh": 0.185, "amount": 49.95 },
                { "label": "Distribution Charge", "category": "DISTRIBUTION", "kwh": 1250.0, "ratePerKwh": 0.068, "amount": 85.00 },
                { "label": "Customer Charge", "category": "FIXED", "kwh": null, "ratePerKwh": null, "amount": 12.50 }
              ]
            }
        """.trimIndent()

        try {
            val rawJson = executeMultimodalJsonRequest(apiKey, prompt, mimeType, fileBytes)
            return@withContext parseUtilityBillJsonResponse(rawJson, fileName)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini utility bill parsing failed: ${e.message}")
            return@withContext ElectricityImportParser.fallbackBillDraftFromFilename(
                fileName = fileName,
                error = "Couldn't scan bill. Enter the details manually."
            )
        }
    }

    suspend fun parseChargerUsage(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray?
    ): ParsedChargerImportResult = withContext(Dispatchers.IO) {
        if (fileBytes == null || fileBytes.isEmpty()) {
            return@withContext ParsedChargerImportResult(
                sessions = emptyList(),
                fileName = fileName,
                source = "manual",
                error = "Selected file was empty."
            )
        }

        val looksLikeCsv = mimeType.contains("csv", ignoreCase = true) ||
            mimeType.startsWith("text/", ignoreCase = true) ||
            fileName.endsWith(".csv", ignoreCase = true)

        if (looksLikeCsv) {
            val csvText = fileBytes.toString(Charsets.UTF_8)
            val parsedCsv = ElectricityImportParser.parseChargerCsv(fileName, csvText)
            if (parsedCsv.isNotEmpty()) {
                return@withContext ParsedChargerImportResult(
                    sessions = parsedCsv,
                    fileName = fileName,
                    source = "csv"
                )
            }
        }

        val apiKey = preferencesManager?.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext ParsedChargerImportResult(
                sessions = emptyList(),
                fileName = fileName,
                source = "manual",
                error = if (looksLikeCsv) {
                    "Couldn't detect date and kWh columns in CSV. Add a Gemini API key in Settings or enter usage manually."
                } else {
                    "Add a Gemini API key in Settings to scan screenshots or PDFs automatically."
                }
            )
        }

        val prompt = """
            You are an EV charging data extractor. Analyze the attached charger export (screenshot, PDF, or CSV) and extract EV charging usage as JSON.
            - If individual charging sessions are listed, return one entry per session with startDate == endDate.
            - If a period breakdown or summary is shown (such as the Tesla app "Charge Stats" screen showing Home, Work, Supercharger, and Other totals for a month or last 31 days), return one entry per non-zero category with startDate and endDate covering the displayed period and isSummary = true.
            - Set venue to:
              - "HOME" for residential/home chargers (e.g., Home, Wall Connector, Garage).
              - "WORK" for free workplace/office/company J1772 charging or the "Work" category in Tesla Charge Stats (set directCost to 0.0, and for Charge Stats name it "Work (Tesla Charge Stats)").
              - "PUBLIC" for commercial/public fast chargers (e.g., Tesla Supercharger, Electrify America, EVgo, ChargePoint, Shell Recharge, or paid "Other" sessions).
            - Include directCost in USD for "PUBLIC" sessions (0.0 if free), 0.0 for "WORK" sessions, and null for "HOME" sessions.

            Return ONLY a valid JSON object matching:
            {
              "sessions": [
                {
                  "startDate": "YYYY-MM-DD",
                  "endDate": "YYYY-MM-DD",
                  "kwh": 42.5,
                  "venue": "HOME",
                  "directCost": null,
                  "chargerName": "Home Wall Connector",
                  "isSummary": false
                }
              ]
            }
        """.trimIndent()

        try {
            val effectiveMime = if (looksLikeCsv) "text/plain" else mimeType
            val rawJson = executeMultimodalJsonRequest(apiKey, prompt, effectiveMime, fileBytes)
            val sessions = parseChargerUsageJsonResponse(rawJson = rawJson, fileName = fileName)

            return@withContext ParsedChargerImportResult(
                sessions = sessions,
                fileName = fileName,
                source = "gemini",
                error = if (sessions.isEmpty()) "No charging sessions found. Enter them manually." else null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Gemini charger usage parsing failed: ${e.message}")
            return@withContext ParsedChargerImportResult(
                sessions = emptyList(),
                fileName = fileName,
                source = "manual",
                error = "Couldn't scan file. Enter usage manually."
            )
        }
    }

    private fun executeMultimodalJsonRequest(
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
                part.jsonObject["text"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            } ?: throw IllegalStateException("No text part returned from Gemini")

            var cleaned = rawText
            if (cleaned.startsWith("```")) {
                val lines = cleaned.lines().toMutableList()
                if (lines.isNotEmpty() && lines.first().startsWith("```")) lines.removeAt(0)
                if (lines.isNotEmpty() && lines.last().startsWith("```")) lines.removeAt(lines.size - 1)
                cleaned = lines.joinToString("\n").trim()
            }
            return cleaned
        }
    }
}
