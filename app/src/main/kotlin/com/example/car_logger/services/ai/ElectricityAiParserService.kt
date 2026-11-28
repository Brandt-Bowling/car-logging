package com.example.car_logger.services.ai

import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.ParsedBillLineItem
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedChargingSessionDraft
import com.example.car_logger.services.ParsedUtilityBillDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

internal class ElectricityAiParserService(
    private val preferencesManager: PreferencesManager? = null,
    private val httpClient: GeminiHttpClient
) {
    companion object {
        private const val TAG = "ElectricityAiParserService"

        internal fun parseChargerUsageJsonResponse(
            rawJson: String,
            fileName: String,
            fallbackDateIso: String = LocalDate.now().toString()
        ): List<ParsedChargingSessionDraft> {
            val root = GeminiHttpClient.json.parseToJsonElement(rawJson).jsonObject
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
            val root = GeminiHttpClient.json.parseToJsonElement(rawJson).jsonObject
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
            val rawJson = httpClient.executeMultimodalJsonRequest(apiKey, prompt, mimeType, fileBytes)
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
            val rawJson = httpClient.executeMultimodalJsonRequest(apiKey, prompt, effectiveMime, fileBytes)
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
}
