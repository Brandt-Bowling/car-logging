package com.example.car_logger.services

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.car_logger.BuildConfig
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class WallConnectorType(val label: String, val defaultChargerName: String) {
    GEN_3("Gen 3 Wall Connector", "Tesla Wall Connector (Gen 3)"),
    UNIVERSAL("Universal Wall Connector", "Tesla Universal Wall Connector")
}

data class TeslaWallConnectorDevice(
    val energySiteId: String,
    val din: String? = null,
    val partNumber: String? = null,
    val siteName: String? = null,
    val connectorType: WallConnectorType
)

data class TeslaWallConnectorChargeSession(
    val energySiteId: String,
    val din: String? = null,
    val vin: String? = null,
    val startDateMs: Long,
    val endDateMs: Long,
    val energyWh: Double
)

sealed class WallConnectorSyncResult {
    data class Synced(
        val insertedCount: Int,
        val totalKwhAdded: Double,
        val totalSessionsScanned: Int
    ) : WallConnectorSyncResult()
    data object NotConfigured : WallConnectorSyncResult()
    data class MissingScope(val message: String) : WallConnectorSyncResult()
    data class Error(val message: String) : WallConnectorSyncResult()
}

sealed class OdometerSyncResult {
    data class Updated(val carId: String, val odometerMiles: Int, val provider: String) : OdometerSyncResult()
    data class SkippedAsleep(val carId: String) : OdometerSyncResult()
    data class SkippedCooldown(val carId: String, val hoursRemaining: Int) : OdometerSyncResult()
    data class SkippedPolicy(val carId: String, val policy: WakeProtectionPolicy) : OdometerSyncResult()
    data class NotConfigured(val carId: String) : OdometerSyncResult()
    data class Error(val carId: String, val message: String) : OdometerSyncResult()
}

class OdometerSyncService(
    private val preferencesManager: PreferencesManager,
    private val carDao: CarDao,
    private val odometerLogDao: OdometerLogDao? = null,
    private val chargingSessionDao: ChargingSessionDao? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    internal suspend fun recordOdometerReading(carId: String, odometer: Int, provider: String, syncTime: Long) {
        recordOdometerReading(carDao, odometerLogDao, carId, odometer, provider, syncTime)
    }
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    companion object {
        private const val TAG = "OdometerSyncService"

        internal suspend fun recordOdometerReading(
            carDao: CarDao,
            odometerLogDao: OdometerLogDao?,
            carId: String,
            odometer: Int,
            provider: String,
            syncTime: Long
        ) {
            val existingCar = carDao.getCarById(carId)
            val prevOdo = existingCar?.odometer
            val prevSyncTime = existingCar?.lastOdometerSyncTime

            carDao.updateOdometerFromTelemetry(
                carId = carId,
                odometer = odometer,
                provider = provider,
                status = "Synced",
                syncTime = syncTime
            )
            try {
                val latest = odometerLogDao?.getLatestLogForCar(carId)
                if (latest == null) {
                    // If there were no prior logs for this car, record baseline if car had a prior verified odometer
                    if (prevOdo != null && prevOdo > 0 && prevOdo < odometer) {
                        val baselineTime = if (prevSyncTime != null && prevSyncTime < syncTime) prevSyncTime else (syncTime - 86_400_000L)
                        odometerLogDao?.insertLog(
                            OdometerLogEntity(
                                id = UUID.randomUUID().toString(),
                                carId = carId,
                                odometer = prevOdo,
                                timestamp = baselineTime,
                                source = provider
                            )
                        )
                    }
                    odometerLogDao?.insertLog(
                        OdometerLogEntity(
                            id = UUID.randomUUID().toString(),
                            carId = carId,
                            odometer = odometer,
                            timestamp = syncTime,
                            source = provider
                        )
                    )
                } else if (shouldRecordOdometerLog(latest, odometer, syncTime)) {
                    odometerLogDao?.insertLog(
                        OdometerLogEntity(
                            id = UUID.randomUUID().toString(),
                            carId = carId,
                            odometer = odometer,
                            timestamp = syncTime,
                            source = provider
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        const val PROVIDER_SMARTCAR = "SMARTCAR"
        const val PROVIDER_TESLA = "TESLA_FLEET"

        const val FALLBACK_SMARTCAR_APPLICATION_ID = "c6e28e0b-2199-47e6-b367-51cb96965b30"

        // Injected at build time from git-ignored local.properties (or environment variables)
        val DEFAULT_SMARTCAR_APPLICATION_ID: String = BuildConfig.SMARTCAR_APPLICATION_ID.ifBlank {
            FALLBACK_SMARTCAR_APPLICATION_ID
        }
        val DEFAULT_SMARTCAR_CLIENT_ID: String = BuildConfig.SMARTCAR_CLIENT_ID
        val DEFAULT_SMARTCAR_CLIENT_SECRET: String = BuildConfig.SMARTCAR_CLIENT_SECRET
        val DEFAULT_TESLA_CLIENT_ID: String = BuildConfig.TESLA_CLIENT_ID
        val DEFAULT_TESLA_CLIENT_SECRET: String = BuildConfig.TESLA_CLIENT_SECRET

        const val HOSTING_DOMAIN = "car-logger-track.web.app"
        const val SMARTCAR_REDIRECT_URI = "https://$HOSTING_DOMAIN/oauth/smartcar"
        const val TESLA_REDIRECT_URI = "https://$HOSTING_DOMAIN/oauth/tesla"

        const val SMARTCAR_API_BASE = "https://api.smartcar.com/v2.0"
        const val SMARTCAR_V3_API_BASE = "https://vehicle.api.smartcar.com/v3"
        const val SMARTCAR_V3_MANAGEMENT_API_BASE = "https://management.api.smartcar.com/v3"
        const val SMARTCAR_AUTH_TOKEN_URL = "https://auth.smartcar.com/oauth/token"
        const val SMARTCAR_V3_IAM_TOKEN_URL = "https://iam.smartcar.com/oauth2/token"

        const val TESLA_API_BASE = "https://fleet-api.prd.na.vn.cloud.tesla.com"
        const val TESLA_AUTH_TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun isCooldownActive(
            lastSyncMs: Long?,
            cooldownHours: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (lastSyncMs == null || lastSyncMs <= 0L || cooldownHours <= 0) return false
            val elapsedMs = nowMs - lastSyncMs
            return elapsedMs in 0 until (cooldownHours * 3_600_000L)
        }

        fun remainingCooldownHours(
            lastSyncMs: Long?,
            cooldownHours: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Int {
            if (!isCooldownActive(lastSyncMs, cooldownHours, nowMs)) return 0
            val remainingMs = (cooldownHours * 3_600_000L) - (nowMs - (lastSyncMs ?: 0L))
            return ceil(remainingMs / 3_600_000.0).toInt().coerceAtLeast(1)
        }

        fun formatRelativeTime(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
            if (timestampMs == null || timestampMs <= 0L) return null
            val diffMinutes = ((nowMs - timestampMs) / 60_000L).coerceAtLeast(0L)
            return when {
                diffMinutes < 1 -> "just now"
                diffMinutes < 60 -> "${diffMinutes}m ago"
                diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
                else -> "${diffMinutes / 1440}d ago"
            }
        }

        fun parseSmartcarOdometerMiles(jsonBody: String): Int? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                // Check v3 JSON:API structure: { "data": { "attributes": { "body": { "value": 24899, "unit": "km" } } } }
                // or root["body"]["value"]
                val dataObj = root["data"]?.jsonObject
                val attrsObj = dataObj?.get("attributes")?.jsonObject
                val bodyObj = attrsObj?.get("body")?.jsonObject
                    ?: dataObj?.get("body")?.jsonObject
                    ?: root["body"]?.jsonObject

                if (bodyObj != null) {
                    val rawVal = bodyObj["value"]?.jsonPrimitive?.doubleOrNull
                        ?: bodyObj["distance"]?.jsonPrimitive?.doubleOrNull
                    val unit = (bodyObj["unit"]?.jsonPrimitive?.content
                        ?: attrsObj?.get("unit")?.jsonPrimitive?.content
                        ?: dataObj?.get("unit")?.jsonPrimitive?.content
                        ?: root["unit"]?.jsonPrimitive?.content
                        ?: "").lowercase()
                    if (rawVal != null && rawVal >= 0) {
                        val miles = if (unit.startsWith("km") || unit.contains("kilometer") || unit == "metric") {
                            rawVal * 0.621371192
                        } else {
                            rawVal
                        }
                        return miles.roundToInt()
                    }
                }

                // Fallback v2.0 response: { "distance": 14820.4 }
                val distance = root["distance"]?.jsonPrimitive?.doubleOrNull
                    ?: root["value"]?.jsonPrimitive?.doubleOrNull
                    ?: dataObj?.get("distance")?.jsonPrimitive?.doubleOrNull
                    ?: attrsObj?.get("distance")?.jsonPrimitive?.doubleOrNull
                distance?.roundToInt()?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarV3Connection(jsonBody: String, preferredUserId: String? = null): Pair<String, String?>? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val dataArr = root["data"]?.jsonArray ?: return null
                if (dataArr.isEmpty()) return null

                fun extractPair(item: kotlinx.serialization.json.JsonObject): Pair<String, String?>? {
                    val attrs = item["attributes"]?.jsonObject
                    val rels = item["relationships"]?.jsonObject

                    val vehicleId = attrs?.get("vehicleId")?.jsonPrimitive?.content
                        ?: rels?.get("vehicle")?.jsonObject?.get("data")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: item["vehicleId"]?.jsonPrimitive?.content
                        ?: item["id"]?.jsonPrimitive?.content
                        ?: return null

                    val userId = attrs?.get("userId")?.jsonPrimitive?.content
                        ?: attrs?.get("user")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: rels?.get("user")?.jsonObject?.get("data")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: item["userId"]?.jsonPrimitive?.content

                    return Pair(vehicleId, userId)
                }

                if (!preferredUserId.isNullOrBlank()) {
                    for (elem in dataArr) {
                        val pair = extractPair(elem.jsonObject) ?: continue
                        if (pair.second == preferredUserId) {
                            return pair
                        }
                    }
                }

                val first = dataArr.firstOrNull()?.jsonObject ?: return null
                extractPair(first)
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarV3ApplicationId(jsonBody: String): String? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val dataArr = root["data"]?.jsonArray ?: return null
                val first = dataArr.firstOrNull()?.jsonObject ?: return null
                first["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && !it.startsWith("client_") }
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarErrorDetail(jsonBody: String): String? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val firstErr = root["errors"]?.jsonArray?.firstOrNull()?.jsonObject
                if (firstErr != null) {
                    val title = firstErr["title"]?.jsonPrimitive?.content
                    val code = firstErr["code"]?.jsonPrimitive?.content
                    val detail = firstErr["detail"]?.jsonPrimitive?.content
                    return title ?: code ?: detail
                }
                val dataObj = root["data"]?.jsonObject
                val attrsObj = dataObj?.get("attributes")?.jsonObject
                val statusObj = attrsObj?.get("status")?.jsonObject ?: root["status"]?.jsonObject
                val statusVal = statusObj?.get("value")?.jsonPrimitive?.content
                if (!statusVal.isNullOrBlank() && !statusVal.equals("SUCCESS", ignoreCase = true)) {
                    val errObj = statusObj["error"]?.jsonObject
                    if (errObj != null) {
                        val errType = errObj["type"]?.jsonPrimitive?.content
                        val resType = errObj["resolution"]?.jsonObject?.get("type")?.jsonPrimitive?.content
                        if (errType.equals("PERMISSION", ignoreCase = true) ||
                            resType.equals("REAUTHENTICATE", ignoreCase = true)
                        ) {
                            return "Missing odometer permission — tap Reconnect in Settings"
                        }
                        val errDetail = errObj["detail"]?.jsonPrimitive?.content
                        val errTitle = errObj["title"]?.jsonPrimitive?.content
                        val errCode = errObj["code"]?.jsonPrimitive?.content
                        val resolved = errDetail ?: errTitle ?: errCode
                        if (!resolved.isNullOrBlank()) return resolved
                    }
                    val reason = statusObj["messages"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    return if (!reason.isNullOrBlank()) "$statusVal ($reason)" else statusVal
                }
                root["message"]?.jsonPrimitive?.content ?: root["error"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                null
            }
        }

        fun formatSyncResultMessage(car: Car, result: OdometerSyncResult): String {
            val name = car.displayName
            return when (result) {
                is OdometerSyncResult.Updated -> {
                    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(result.odometerMiles)
                    "Synced $name: $formatted mi"
                }
                is OdometerSyncResult.SkippedAsleep ->
                    "$name is asleep (wake skipped)"
                is OdometerSyncResult.SkippedCooldown ->
                    "$name is in cooldown (${result.hoursRemaining}h remaining)"
                is OdometerSyncResult.SkippedPolicy ->
                    "$name sync skipped (${result.policy.label} mode)"
                is OdometerSyncResult.NotConfigured ->
                    "$name telemetry not linked — connect in Settings"
                is OdometerSyncResult.Error ->
                    "Sync failed for $name: ${result.message}"
            }
        }

        fun formatDailyPollSummary(
            cars: List<Car>,
            results: List<OdometerSyncResult>,
            chargerResult: WallConnectorSyncResult? = null
        ): String {
            if (cars.isEmpty() && results.isEmpty()) {
                return "No vehicles configured"
            }
            val updatedCount = results.count { it is OdometerSyncResult.Updated }
            val baseSummary = if (updatedCount == results.size && updatedCount > 0) {
                if (updatedCount == 1) "Synced 1 vehicle" else "Synced $updatedCount vehicles"
            } else {
                val carMap = cars.associateBy { it.id }
                val details = results.mapNotNull { res ->
                    when (res) {
                        is OdometerSyncResult.Updated -> carMap[res.carId]?.model?.let { "$it: synced" }
                        is OdometerSyncResult.SkippedAsleep -> carMap[res.carId]?.model?.let { "$it: asleep" }
                        is OdometerSyncResult.NotConfigured -> carMap[res.carId]?.model?.let { "$it: not linked" }
                        is OdometerSyncResult.Error -> carMap[res.carId]?.model?.let { "$it: error" }
                        else -> null
                    }
                }
                if (details.isNotEmpty()) details.joinToString(", ") else "Daily sync completed"
            }

            return if (chargerResult is WallConnectorSyncResult.Synced && chargerResult.insertedCount > 0) {
                val kwhStr = String.format(Locale.US, "%.1f", chargerResult.totalKwhAdded)
                "$baseSummary • +$kwhStr kWh"
            } else {
                baseSummary
            }
        }

        fun formatWallConnectorSyncMessage(result: WallConnectorSyncResult): String {
            return when (result) {
                is WallConnectorSyncResult.Synced -> {
                    when {
                        result.insertedCount > 0 -> {
                            val kwhStr = String.format(Locale.US, "%.1f", result.totalKwhAdded)
                            val label = if (result.insertedCount == 1) "session" else "sessions"
                            "Synced ${result.insertedCount} Wall Connector $label (+$kwhStr kWh)"
                        }
                        result.totalSessionsScanned > 0 -> {
                            val label = if (result.totalSessionsScanned == 1) "session" else "sessions"
                            "Wall Connectors up to date (${result.totalSessionsScanned} $label checked)"
                        }
                        else -> "Wall Connectors synced (no new sessions)"
                    }
                }
                is WallConnectorSyncResult.NotConfigured ->
                    "Tesla Fleet API not linked — connect in Settings"
                is WallConnectorSyncResult.MissingScope ->
                    result.message
                is WallConnectorSyncResult.Error ->
                    "Wall Connector sync failed: ${result.message}"
            }
        }

        private fun extractPartNumberFromDin(din: String?): String? {
            if (din.isNullOrBlank()) return null
            val trimmed = din.trim()
            return if (trimmed.contains("--")) {
                trimmed.substringBefore("--").trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }

        fun classifyWallConnectorType(
            din: String? = null,
            partNumber: String? = null,
            siteName: String? = null,
            sessionVin: String? = null,
            index: Int = 0
        ): WallConnectorType {
            val effectivePart = (partNumber?.takeIf { it.isNotBlank() } ?: extractPartNumberFromDin(din) ?: din)
                ?.trim()
                ?.uppercase(Locale.US)
                .orEmpty()
            val nameLower = siteName.orEmpty().lowercase(Locale.US)
            val vinUpper = sessionVin.orEmpty().trim().uppercase(Locale.US)

            // Tesla Universal Wall Connector part numbers begin with 1734412-
            if (effectivePart.startsWith("1734412") ||
                effectivePart.contains("UNIVERSAL") ||
                nameLower.contains("universal") ||
                nameLower.contains("j1772") ||
                nameLower.contains("blazer") ||
                nameLower.contains("chevy") ||
                nameLower.contains("chevrolet") ||
                nameLower.contains("gm") ||
                (vinUpper.length == 17 && !vinUpper.startsWith("5YJ") && !vinUpper.startsWith("7SA") && !vinUpper.startsWith("7G2"))
            ) {
                return WallConnectorType.UNIVERSAL
            }

            // Tesla Gen 3 Wall Connector part numbers begin with 1457768-, 1529455-, or 1509549-
            if (effectivePart.startsWith("1457768") ||
                effectivePart.startsWith("1529455") ||
                effectivePart.startsWith("1509549") ||
                effectivePart.contains("GEN3") ||
                nameLower.contains("gen 3") ||
                nameLower.contains("gen3") ||
                nameLower.contains("model 3") ||
                nameLower.contains("tesla") ||
                nameLower.contains("nacs") ||
                vinUpper.startsWith("5YJ") ||
                vinUpper.startsWith("7SA") ||
                vinUpper.startsWith("7G2")
            ) {
                return WallConnectorType.GEN_3
            }

            return if (index % 2 == 1) WallConnectorType.UNIVERSAL else WallConnectorType.GEN_3
        }

        fun parseTeslaEnergySites(productsJson: String): List<TeslaWallConnectorDevice> {
            return try {
                val root = json.parseToJsonElement(productsJson).jsonObject
                val responseArr = root["response"]?.jsonArray ?: return emptyList()
                val devices = mutableListOf<TeslaWallConnectorDevice>()

                for (element in responseArr) {
                    val obj = element as? JsonObject ?: continue
                    // Skip pure vehicle objects from /api/1/products
                    val hasVin = !obj["vin"]?.jsonPrimitive?.content.isNullOrBlank()
                    val energySiteId = obj["energy_site_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val resourceType = obj["resource_type"]?.jsonPrimitive?.content?.lowercase(Locale.US).orEmpty()
                    val componentsObj = obj["components"] as? JsonObject
                    val wallConnectorsArr = (componentsObj?.get("wall_connectors") as? JsonArray)
                        ?: (obj["wall_connectors"] as? JsonArray)

                    if (hasVin && energySiteId == null && resourceType.isEmpty() && wallConnectorsArr == null) {
                        continue
                    }

                    val siteId = energySiteId
                        ?: obj["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: continue
                    val siteName = obj["site_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

                    if (wallConnectorsArr != null && wallConnectorsArr.isNotEmpty()) {
                        for (wcElem in wallConnectorsArr) {
                            val wcObj = wcElem as? JsonObject ?: continue
                            val din = wcObj["din"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                ?: wcObj["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            val partNumber = wcObj["part_number"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                ?: extractPartNumberFromDin(din)
                            val wcName = wcObj["site_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                ?: wcObj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                ?: siteName
                            val type = classifyWallConnectorType(
                                din = din,
                                partNumber = partNumber,
                                siteName = wcName,
                                index = devices.size
                            )
                            devices.add(
                                TeslaWallConnectorDevice(
                                    energySiteId = siteId,
                                    din = din,
                                    partNumber = partNumber,
                                    siteName = wcName,
                                    connectorType = type
                                )
                            )
                        }
                    } else {
                        val rawId = obj["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        val din = obj["din"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: rawId?.takeIf { it != siteId || it.contains("--") || it.contains("-") }
                        val partNumber = obj["part_number"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: extractPartNumberFromDin(din)
                        val type = classifyWallConnectorType(
                            din = din,
                            partNumber = partNumber,
                            siteName = siteName,
                            index = devices.size
                        )
                        devices.add(
                            TeslaWallConnectorDevice(
                                energySiteId = siteId,
                                din = din,
                                partNumber = partNumber,
                                siteName = siteName,
                                connectorType = type
                            )
                        )
                    }
                }
                devices
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun parseTeslaSiteInfoWallConnectors(
            siteInfoJson: String,
            energySiteId: String,
            fallbackSiteName: String? = null,
            startingIndex: Int = 0
        ): List<TeslaWallConnectorDevice> {
            return try {
                val root = json.parseToJsonElement(siteInfoJson).jsonObject
                val response = (root["response"] as? JsonObject) ?: root
                val siteName = response["site_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: fallbackSiteName
                val components = response["components"] as? JsonObject
                val wallConnectors = (components?.get("wall_connectors") as? JsonArray)
                    ?: (response["wall_connectors"] as? JsonArray)

                val devices = mutableListOf<TeslaWallConnectorDevice>()
                if (wallConnectors != null && wallConnectors.isNotEmpty()) {
                    for (wcElem in wallConnectors) {
                        val wcObj = wcElem as? JsonObject ?: continue
                        val din = wcObj["din"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: wcObj["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        val partNumber = wcObj["part_number"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: extractPartNumberFromDin(din)
                        val wcName = wcObj["site_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: wcObj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: siteName
                        val type = classifyWallConnectorType(
                            din = din,
                            partNumber = partNumber,
                            siteName = wcName,
                            index = startingIndex + devices.size
                        )
                        devices.add(
                            TeslaWallConnectorDevice(
                                energySiteId = energySiteId,
                                din = din,
                                partNumber = partNumber,
                                siteName = wcName,
                                connectorType = type
                            )
                        )
                    }
                } else {
                    val din = response["din"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: response["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != energySiteId }
                    val partNumber = response["part_number"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: extractPartNumberFromDin(din)
                    if (din != null || partNumber != null || siteName != null) {
                        devices.add(
                            TeslaWallConnectorDevice(
                                energySiteId = energySiteId,
                                din = din,
                                partNumber = partNumber,
                                siteName = siteName,
                                connectorType = classifyWallConnectorType(
                                    din = din,
                                    partNumber = partNumber,
                                    siteName = siteName,
                                    index = startingIndex
                                )
                            )
                        )
                    }
                }
                devices
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun resolveCarForWallConnector(
            connectorType: WallConnectorType,
            sessionVin: String? = null,
            cars: List<Car>,
            gen3OverrideCarId: String? = null,
            universalOverrideCarId: String? = null
        ): Car? {
            if (cars.isEmpty()) return null
            val activeCars = cars.filter { !it.isArchived }.ifEmpty { cars }

            // 1. Explicit user override in PreferencesManager takes highest priority
            when (connectorType) {
                WallConnectorType.GEN_3 -> {
                    if (!gen3OverrideCarId.isNullOrBlank()) {
                        activeCars.firstOrNull { it.id == gen3OverrideCarId }?.let { return it }
                    }
                }
                WallConnectorType.UNIVERSAL -> {
                    if (!universalOverrideCarId.isNullOrBlank()) {
                        activeCars.firstOrNull { it.id == universalOverrideCarId }?.let { return it }
                    }
                }
            }

            // 2. Direct VIN match if the Wall Connector session reported the vehicle VIN
            if (!sessionVin.isNullOrBlank()) {
                val cleanVin = sessionVin.trim().uppercase(Locale.US)
                activeCars.firstOrNull { it.vin?.trim()?.uppercase(Locale.US) == cleanVin }?.let { return it }
            }

            // 3. Auto-assign Gen 3 -> Model 3 (Tesla) and Universal -> Blazer EV (GM / Smartcar)
            return when (connectorType) {
                WallConnectorType.GEN_3 -> {
                    activeCars.firstOrNull { it.model.contains("Model 3", ignoreCase = true) }
                        ?: activeCars.firstOrNull {
                            resolveProviderForCar(it) == PROVIDER_TESLA || it.make.contains("Tesla", ignoreCase = true)
                        }
                        ?: activeCars.firstOrNull { it.isEv }
                }
                WallConnectorType.UNIVERSAL -> {
                    activeCars.firstOrNull { it.model.contains("Blazer", ignoreCase = true) }
                        ?: activeCars.firstOrNull { resolveProviderForCar(it) == PROVIDER_SMARTCAR }
                        ?: activeCars.firstOrNull { it.isEv && !it.make.contains("Tesla", ignoreCase = true) }
                        ?: activeCars.lastOrNull { it.isEv }
                }
            }
        }

        private fun parseFlexibleTimestampMs(raw: JsonElement?): Long? {
            val prim = raw?.jsonPrimitive ?: return null
            prim.longOrNull?.let { num ->
                // Distinguish epoch seconds vs epoch milliseconds
                return if (num < 100_000_000_000L) num * 1000L else num
            }
            val text = prim.content.trim()
            if (text.isEmpty()) return null
            text.toLongOrNull()?.let { num ->
                return if (num < 100_000_000_000L) num * 1000L else num
            }
            return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
                ?: ElectricityImportParser.parseDateToStartOfDayMs(text)
        }

        fun parseTeslaWallConnectorChargeHistory(
            jsonBody: String,
            energySiteId: String
        ): List<TeslaWallConnectorChargeSession> {
            return try {
                val rootElement = json.parseToJsonElement(jsonBody)
                val seriesArray: JsonArray = when (rootElement) {
                    is JsonArray -> rootElement
                    is JsonObject -> {
                        val respElem = rootElement["response"]
                        when (respElem) {
                            is JsonArray -> respElem
                            is JsonObject -> {
                                (respElem["time_series"] as? JsonArray)
                                    ?: (respElem["charge_history"] as? JsonArray)
                                    ?: (respElem["sessions"] as? JsonArray)
                                    ?: (respElem["data"] as? JsonArray)
                                    ?: return emptyList()
                            }
                            else -> {
                                (rootElement["time_series"] as? JsonArray)
                                    ?: (rootElement["charge_history"] as? JsonArray)
                                    ?: return emptyList()
                            }
                        }
                    }
                    else -> return emptyList()
                }

                val sessions = mutableListOf<TeslaWallConnectorChargeSession>()
                for (elem in seriesArray) {
                    val obj = elem as? JsonObject ?: continue

                    val energyWh = obj["charge_energy_imported"]?.jsonPrimitive?.doubleOrNull
                        ?: obj["energy_wh"]?.jsonPrimitive?.doubleOrNull
                        ?: obj["energy_added_wh"]?.jsonPrimitive?.doubleOrNull
                        ?: obj["grid_energy_imported"]?.jsonPrimitive?.doubleOrNull
                        ?: (obj["charge_energy_imported_kwh"]?.jsonPrimitive?.doubleOrNull?.times(1000.0))
                        ?: (obj["energy_kwh"]?.jsonPrimitive?.doubleOrNull?.times(1000.0))
                        ?: continue

                    // Filter out zero/standby noise (< 50 Wh = 0.05 kWh)
                    if (energyWh < 50.0) continue

                    val startMs = parseFlexibleTimestampMs(
                        obj["charge_start_time"]
                            ?: obj["start_date"]
                            ?: obj["start_time"]
                            ?: obj["timestamp"]
                            ?: obj["datetime"]
                    ) ?: continue

                    val explicitEndMs = parseFlexibleTimestampMs(
                        obj["charge_stop_time"]
                            ?: obj["end_date"]
                            ?: obj["stop_time"]
                            ?: obj["end_time"]
                    )
                    val durationSec = obj["charge_duration"]?.jsonPrimitive?.longOrNull
                        ?: obj["duration_seconds"]?.jsonPrimitive?.longOrNull
                        ?: obj["duration"]?.jsonPrimitive?.longOrNull

                    val endMs = when {
                        explicitEndMs != null && explicitEndMs >= startMs -> explicitEndMs
                        durationSec != null && durationSec > 0L -> startMs + (durationSec * 1000L)
                        else -> startMs
                    }

                    val din = obj["din"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: obj["wall_connector_din"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: obj["device_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val vin = obj["vin"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: obj["vehicle_vin"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

                    sessions.add(
                        TeslaWallConnectorChargeSession(
                            energySiteId = energySiteId,
                            din = din,
                            vin = vin,
                            startDateMs = startMs,
                            endDateMs = endMs,
                            energyWh = energyWh
                        )
                    )
                }
                sessions.sortedBy { it.startDateMs }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun buildWallConnectorSessionEntities(
            rawSessions: List<TeslaWallConnectorChargeSession>,
            devices: List<TeslaWallConnectorDevice>,
            cars: List<Car>,
            gen3OverrideCarId: String? = null,
            universalOverrideCarId: String? = null,
            existingIds: Set<String> = emptySet(),
            existingFingerprints: Set<String> = emptySet(),
            uploadedAtMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): List<ChargingSessionEntity> {
            if (rawSessions.isEmpty()) return emptyList()
            val seenIds = existingIds.toMutableSet()
            val seenFingerprints = existingFingerprints.toMutableSet()
            val entities = mutableListOf<ChargingSessionEntity>()

            for (raw in rawSessions) {
                val kwh = kotlin.math.round((raw.energyWh / 1000.0) * 1000.0) / 1000.0
                if (kwh <= 0.0) continue

                val matchedDevice = devices.firstOrNull { dev ->
                    !raw.din.isNullOrBlank() && dev.din.equals(raw.din, ignoreCase = true)
                } ?: devices.firstOrNull { dev ->
                    dev.energySiteId == raw.energySiteId
                }

                val connectorType = if (!raw.din.isNullOrBlank() || !raw.vin.isNullOrBlank()) {
                    classifyWallConnectorType(
                        din = raw.din ?: matchedDevice?.din,
                        partNumber = matchedDevice?.partNumber,
                        siteName = matchedDevice?.siteName,
                        sessionVin = raw.vin
                    )
                } else {
                    matchedDevice?.connectorType ?: WallConnectorType.GEN_3
                }

                val targetCar = resolveCarForWallConnector(
                    connectorType = connectorType,
                    sessionVin = raw.vin,
                    cars = cars,
                    gen3OverrideCarId = gen3OverrideCarId,
                    universalOverrideCarId = universalOverrideCarId
                )

                val chargerKey = (raw.din ?: matchedDevice?.din ?: "${raw.energySiteId}_${connectorType.name}")
                    .replace(Regex("[^A-Za-z0-9_-]"), "_")
                val startEpochSec = raw.startDateMs / 1000L
                val sessionId = "twc_${chargerKey}_${startEpochSec}"

                if (!seenIds.add(sessionId)) continue

                val baseFingerprint = ElectricityImportParser.buildFingerprint(
                    startDateMs = raw.startDateMs,
                    endDateMs = raw.endDateMs,
                    kwh = kwh,
                    venue = "HOME",
                    carId = targetCar?.id,
                    zoneId = zoneId
                )
                if (!seenFingerprints.add(baseFingerprint)) continue

                val chargerLabel = matchedDevice?.siteName
                    ?.takeIf { it.isNotBlank() && !it.equals(connectorType.defaultChargerName, ignoreCase = true) }
                    ?.let { "${connectorType.defaultChargerName} ($it)" }
                    ?: connectorType.defaultChargerName

                entities.add(
                    ChargingSessionEntity(
                        id = sessionId,
                        carId = targetCar?.id,
                        startDateMs = raw.startDateMs,
                        endDateMs = raw.endDateMs,
                        kwh = kwh,
                        venue = "HOME",
                        directCost = null,
                        chargerName = chargerLabel,
                        isSummary = false,
                        fingerprint = baseFingerprint,
                        uploadBatchId = ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID,
                        uploadedAtMs = uploadedAtMs
                    )
                )
            }
            return entities
        }

        fun parseTeslaVehicleListState(jsonBody: String, targetVin: String?): Pair<String, String>? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val responseArr = root["response"]?.jsonArray ?: return null
                for (element in responseArr) {
                    val obj = element.jsonObject
                    val vin = obj["vin"]?.jsonPrimitive?.content?.uppercase() ?: continue
                    val state = obj["state"]?.jsonPrimitive?.content?.lowercase() ?: "unknown"
                    if (targetVin.isNullOrBlank() || vin == targetVin.trim().uppercase()) {
                        return Pair(vin, state)
                    }
                }
                val first = responseArr.firstOrNull()?.jsonObject ?: return null
                val vin = first["vin"]?.jsonPrimitive?.content ?: return null
                val state = first["state"]?.jsonPrimitive?.content?.lowercase() ?: "unknown"
                Pair(vin, state)
            } catch (_: Exception) {
                null
            }
        }

        fun parseTeslaOdometerMiles(jsonBody: String): Int? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val response = root["response"]?.jsonObject ?: root
                val vehicleState = response["vehicle_state"]?.jsonObject ?: response
                val odo = vehicleState["odometer"]?.jsonPrimitive?.doubleOrNull
                odo?.roundToInt()?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }

        fun parseTeslaWakeState(jsonBody: String): String? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val response = root["response"]?.jsonObject ?: root
                response["state"]?.jsonPrimitive?.content?.lowercase()
            } catch (_: Exception) {
                null
            }
        }

        suspend fun waitForTeslaWakeUp(
            maxAttempts: Int = 6,
            delayMs: Long = 5_000L,
            pollWakeState: suspend () -> String?
        ): Boolean {
            for (attempt in 1..maxAttempts) {
                val currentState = try {
                    pollWakeState()
                } catch (_: Exception) {
                    null
                }
                if (currentState == "online") {
                    return true
                }
                if (delayMs > 0L) {
                    delay(delayMs)
                }
            }
            return false
        }

        fun shouldRecordOdometerLog(
            latestLog: OdometerLogEntity?,
            newOdometer: Int,
            syncTimeMs: Long,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): Boolean {
            if (latestLog == null) return true
            if (newOdometer > latestLog.odometer) return true
            if (newOdometer < latestLog.odometer) return false
            if (syncTimeMs <= latestLog.timestamp) return false

            val prevDate = Instant.ofEpochMilli(latestLog.timestamp).atZone(zoneId).toLocalDate()
            val newDate = Instant.ofEpochMilli(syncTimeMs).atZone(zoneId).toLocalDate()
            return newDate != prevDate || (syncTimeMs - latestLog.timestamp) >= 3_600_000L
        }

        fun resolveProviderForCar(car: Car): String? {
            if (!car.telemetryProvider.isNullOrBlank()) return car.telemetryProvider
            val makeLower = car.make.lowercase()
            return when {
                makeLower.contains("tesla") -> PROVIDER_TESLA
                makeLower.contains("chevrolet") || makeLower.contains("chevy") ||
                    makeLower.contains("gmc") || makeLower.contains("cadillac") -> PROVIDER_SMARTCAR
                else -> null
            }
        }

        fun buildSmartcarAuthUrl(clientOrAppId: String): String {
            val trimmed = clientOrAppId.trim()
            val appId = if ((trimmed.isBlank() || trimmed.startsWith("client_")) && DEFAULT_SMARTCAR_APPLICATION_ID.isNotBlank()) {
                DEFAULT_SMARTCAR_APPLICATION_ID.trim()
            } else {
                trimmed
            }
            val encodedAppId = URLEncoder.encode(appId, "UTF-8")
            val encodedRedirect = URLEncoder.encode(SMARTCAR_REDIRECT_URI, "UTF-8")
            return "https://connect.smartcar.com/oauth/authorize" +
                "?application_id=$encodedAppId" +
                "&response_type=none" +
                "&redirect_uri=$encodedRedirect" +
                "&mode=live"
        }
    }

    fun buildSmartcarAuthUrl(clientOrAppId: String): String = Companion.buildSmartcarAuthUrl(clientOrAppId)

    suspend fun resolveSmartcarAuthUrl(clientOrAppId: String): String = withContext(Dispatchers.IO) {
        val trimmed = clientOrAppId.trim()
        if (trimmed.isNotBlank() && !trimmed.startsWith("client_")) {
            return@withContext buildSmartcarAuthUrl(trimmed)
        }
        val cachedAppId = preferencesManager.getSmartcarApplicationId()
        if (!cachedAppId.isNullOrBlank() && !cachedAppId.startsWith("client_")) {
            return@withContext buildSmartcarAuthUrl(cachedAppId)
        }
        val token = getValidSmartcarAccessToken()
        if (!token.isNullOrBlank()) {
            val appReq = Request.Builder()
                .url("$SMARTCAR_V3_MANAGEMENT_API_BASE/applications")
                .addHeader("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(appReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val resolvedId = parseSmartcarV3ApplicationId(resp.body?.string().orEmpty())
                        if (!resolvedId.isNullOrBlank()) {
                            preferencesManager.setSmartcarApplicationId(resolvedId)
                            return@withContext buildSmartcarAuthUrl(resolvedId)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        buildSmartcarAuthUrl(DEFAULT_SMARTCAR_APPLICATION_ID)
    }

    suspend fun refreshSmartcarConnectionStatus(): Boolean = withContext(Dispatchers.IO) {
        val (clientId, _) = getEffectiveSmartcarCredentials()
        if (clientId?.startsWith("client_") != true && preferencesManager.getSmartcarUserId().isNullOrBlank()) {
            return@withContext !preferencesManager.getSmartcarVehicleId().isNullOrBlank()
        }
        var token = getValidSmartcarAccessToken() ?: return@withContext false

        fun fetchConnections(bearerToken: String): Pair<Int, String> {
            val connReq = Request.Builder()
                .url("$SMARTCAR_V3_API_BASE/connections")
                .addHeader("Authorization", "Bearer $bearerToken")
                .build()
            return client.newCall(connReq).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        try {
            var (code, bodyStr) = fetchConnections(token)
            if (code == 401) {
                val refreshed = getValidSmartcarAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank() && refreshed != token) {
                    token = refreshed
                    val retry = fetchConnections(token)
                    code = retry.first
                    bodyStr = retry.second
                }
            }
            if (code !in 200..299) return@withContext false
            val preferredUserId = preferencesManager.getSmartcarUserId()
            val parsed = parseSmartcarV3Connection(bodyStr, preferredUserId)
            if (parsed != null) {
                preferencesManager.setSmartcarVehicleId(parsed.first, parsed.second)
                true
            } else {
                preferencesManager.clearSmartcarVehicleConnection()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun buildTeslaAuthUrl(clientId: String): String {
        val encodedClient = URLEncoder.encode(clientId.trim(), "UTF-8")
        val encodedRedirect = URLEncoder.encode(TESLA_REDIRECT_URI, "UTF-8")
        return "https://auth.tesla.com/oauth2/v3/authorize" +
            "?response_type=code" +
            "&client_id=$encodedClient" +
            "&redirect_uri=$encodedRedirect" +
            "&scope=openid%20offline_access%20vehicle_device_data%20energy_device_data" +
            "&state=carlogger_tesla"
    }

    suspend fun handleOAuthCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val errorDesc = uri.getQueryParameter("error_description") ?: error
            Log.w(TAG, "OAuth callback error: $errorDesc")
            _lastSyncMessage.value = "OAuth failed: $errorDesc"
            return@withContext false
        }
        val path = uri.path.orEmpty()
        if (path.contains("smartcar", ignoreCase = true)) {
            val userId = uri.getQueryParameter("user_id")
            if (!userId.isNullOrBlank()) {
                preferencesManager.setSmartcarUserId(userId)
            }
            val vehicleId = uri.getQueryParameter("vehicle_id")
            if (!vehicleId.isNullOrBlank()) {
                preferencesManager.setSmartcarVehicleId(vehicleId, userId)
            }
            val code = uri.getQueryParameter("code")
            return@withContext if (!code.isNullOrBlank()) {
                exchangeSmartcarCode(code)
            } else {
                getValidSmartcarAccessToken(forceRefresh = true)
                val connected = refreshSmartcarConnectionStatus() || !userId.isNullOrBlank() || !vehicleId.isNullOrBlank()
                if (connected) {
                    _lastSyncMessage.value = "Smartcar connected"
                }
                connected
            }
        }
        val code = uri.getQueryParameter("code") ?: return@withContext false
        return@withContext when {
            path.contains("tesla", ignoreCase = true) -> exchangeTeslaCode(code)
            else -> false
        }
    }

    suspend fun getEffectiveSmartcarCredentials(): Pair<String?, String?> {
        val (savedId, savedSecret) = preferencesManager.getSmartcarCredentials()
        val id = savedId?.takeIf { it.isNotBlank() } ?: DEFAULT_SMARTCAR_CLIENT_ID.takeIf { it.isNotBlank() }
        val secret = savedSecret?.takeIf { it.isNotBlank() } ?: DEFAULT_SMARTCAR_CLIENT_SECRET.takeIf { it.isNotBlank() }
        return Pair(id, secret)
    }

    suspend fun getEffectiveTeslaCredentials(): Pair<String?, String?> {
        val (savedId, savedSecret) = preferencesManager.getTeslaCredentials()
        val id = savedId?.takeIf { it.isNotBlank() } ?: DEFAULT_TESLA_CLIENT_ID.takeIf { it.isNotBlank() }
        val secret = savedSecret?.takeIf { it.isNotBlank() } ?: DEFAULT_TESLA_CLIENT_SECRET.takeIf { it.isNotBlank() }
        return Pair(id, secret)
    }

    private suspend fun exchangeSmartcarCode(code: String): Boolean {
        val (clientId, clientSecret) = getEffectiveSmartcarCredentials()
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) return false

        val basicAuth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", SMARTCAR_REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(SMARTCAR_AUTH_TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val access = obj["access_token"]?.jsonPrimitive?.content ?: return false
                val refresh = obj["refresh_token"]?.jsonPrimitive?.content
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L
                preferencesManager.saveSmartcarTokens(access, refresh, expires)
                _lastSyncMessage.value = "Smartcar connected"
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Smartcar code exchange failed", e)
            false
        }
    }

    private suspend fun exchangeTeslaCode(code: String): Boolean {
        val (clientId, clientSecret) = getEffectiveTeslaCredentials()
        if (clientId.isNullOrBlank()) return false

        val formBuilder = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code)
            .add("redirect_uri", TESLA_REDIRECT_URI)
            .add("audience", TESLA_API_BASE)
        if (!clientSecret.isNullOrBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(TESLA_AUTH_TOKEN_URL)
            .post(formBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val access = obj["access_token"]?.jsonPrimitive?.content ?: return false
                val refresh = obj["refresh_token"]?.jsonPrimitive?.content
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 28800L
                preferencesManager.saveTeslaTokens(access, refresh, expires)
                _lastSyncMessage.value = "Tesla connected"
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tesla code exchange failed", e)
            false
        }
    }

    suspend fun syncAllEligibleVehicles(
        cars: List<Car>,
        isPostDrive: Boolean = false
    ): List<OdometerSyncResult> = withContext(Dispatchers.IO) {
        val policy = preferencesManager.getWakePolicy()
        if (policy == WakeProtectionPolicy.MANUAL_ONLY) {
            return@withContext cars.map { OdometerSyncResult.SkippedPolicy(it.id, policy) }
        }
        if (policy == WakeProtectionPolicy.POST_DRIVE && !isPostDrive) {
            return@withContext cars.map { OdometerSyncResult.SkippedPolicy(it.id, policy) }
        }

        val results = mutableListOf<OdometerSyncResult>()
        for (car in cars) {
            if (car.isArchived) continue
            val provider = resolveProviderForCar(car) ?: continue
            // In Awake-Only startup mode, never auto-poll Smartcar unless cooldown expired;
            // Tesla checks cloud state passively without waking.
            val res = syncVehicle(car = car, isManual = false, forceWake = false)
            results.add(res)
        }
        results
    }

    suspend fun syncDailyEndOfDay(cars: List<Car>): List<OdometerSyncResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OdometerSyncResult>()
        val eligibleCars = cars.filter { !it.isArchived && resolveProviderForCar(it) != null }
        for (car in eligibleCars) {
            val res = syncVehicle(car = car, isManual = false, forceWake = true, bypassCooldown = true)
            results.add(res)
        }
        val chargerResult = if (chargingSessionDao != null) {
            syncWallConnectorCharging(cars)
        } else {
            null
        }
        val nowMs = System.currentTimeMillis()
        val summary = formatDailyPollSummary(eligibleCars, results, chargerResult)
        preferencesManager.recordDailyPollResult(nowMs, summary)
        results
    }

    suspend fun syncVehicle(
        car: Car,
        isManual: Boolean = false,
        forceWake: Boolean = false,
        bypassCooldown: Boolean = false
    ): OdometerSyncResult = withContext(Dispatchers.IO) {
        val provider = resolveProviderForCar(car)
            ?: return@withContext OdometerSyncResult.NotConfigured(car.id)

        val cooldownHours = preferencesManager.getSyncCooldownHours()
        val nowMs = System.currentTimeMillis()

        // Enforce cooldown unless the user explicitly forced wake/poll OR it is a scheduled daily poll
        if (!forceWake && !bypassCooldown && isCooldownActive(car.lastOdometerSyncTime, cooldownHours, nowMs)) {
            val remaining = remainingCooldownHours(car.lastOdometerSyncTime, cooldownHours, nowMs)
            return@withContext OdometerSyncResult.SkippedCooldown(car.id, remaining)
        }

        _isSyncing.value = true
        try {
            return@withContext when (provider) {
                PROVIDER_TESLA -> syncTeslaVehicle(car, forceWake)
                PROVIDER_SMARTCAR -> {
                    // For Smartcar (OnStar), querying odometer wakes telematics if asleep.
                    // In Awake-Only background mode (not manual & not forceWake), respect cooldown strictly.
                    syncSmartcarVehicle(car)
                }
                else -> OdometerSyncResult.NotConfigured(car.id)
            }
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun getValidTeslaAccessToken(): String? {
        val (access, refresh, expiry) = preferencesManager.getTeslaTokens()
        if (access.isNullOrBlank()) return null
        if (expiry == 0L || System.currentTimeMillis() < expiry - 60_000L) {
            return access
        }
        val (clientId, clientSecret) = getEffectiveTeslaCredentials()
        if (clientId.isNullOrBlank() || refresh.isNullOrBlank()) return access

        val formBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refresh)
        if (!clientSecret.isNullOrBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(TESLA_AUTH_TOKEN_URL)
            .post(formBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return access
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return access
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content ?: refresh
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 28800L
                preferencesManager.saveTeslaTokens(newAccess, newRefresh, expires)
                newAccess
            }
        } catch (_: Exception) {
            access
        }
    }

    private suspend fun syncTeslaVehicle(car: Car, forceWake: Boolean): OdometerSyncResult {
        val token = getValidTeslaAccessToken()
            ?: return OdometerSyncResult.NotConfigured(car.id)

        // Step 1: Zero-Wake Cloud State Check
        val listReq = Request.Builder()
            .url("$TESLA_API_BASE/api/1/vehicles")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val (vin, state) = try {
            client.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return OdometerSyncResult.Error(car.id, "Tesla API (${resp.code})")
                }
                parseTeslaVehicleListState(resp.body?.string().orEmpty(), car.vin)
                    ?: return OdometerSyncResult.Error(car.id, "No Tesla found")
            }
        } catch (e: Exception) {
            return OdometerSyncResult.Error(car.id, "Network error")
        }

        // Step 2: If asleep and forceWake is false, NEVER wake the vehicle
        if (state != "online") {
            if (!forceWake) {
                carDao.updateTelemetryStatus(car.id, PROVIDER_TESLA, "Asleep")
                _lastSyncMessage.value = "${car.model} asleep (wake skipped)"
                return OdometerSyncResult.SkippedAsleep(car.id)
            }

            // Explicit or Nightly Force Wake: poll wake_up up to 6 times (30s) until vehicle reports online
            waitForTeslaWakeUp(maxAttempts = 6, delayMs = 5_000L) {
                val wakeReq = Request.Builder()
                    .url("$TESLA_API_BASE/api/1/vehicles/$vin/wake_up")
                    .addHeader("Authorization", "Bearer $token")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(wakeReq).execute().use { resp ->
                    parseTeslaWakeState(resp.body?.string().orEmpty())
                }
            }
        }

        // Step 3: Fetch vehicle_state.odometer (retry 408 timeouts when forceWake is true)
        val maxDataAttempts = if (forceWake) 3 else 1
        for (attempt in 1..maxDataAttempts) {
            val dataReq = Request.Builder()
                .url("$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=vehicle_state")
                .addHeader("Authorization", "Bearer $token")
                .build()

            try {
                val result = client.newCall(dataReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 408) {
                            if (attempt < maxDataAttempts) {
                                null // Signal retry after delay
                            } else {
                                carDao.updateTelemetryStatus(car.id, PROVIDER_TESLA, "Asleep")
                                OdometerSyncResult.SkippedAsleep(car.id)
                            }
                        } else {
                            OdometerSyncResult.Error(car.id, "Tesla data (${resp.code})")
                        }
                    } else {
                        val newOdo = parseTeslaOdometerMiles(resp.body?.string().orEmpty())
                            ?: return OdometerSyncResult.Error(car.id, "Invalid odometer payload")

                        val finalOdo = maxOf(newOdo, car.odometer ?: 0)
                        val now = System.currentTimeMillis()
                        recordOdometerReading(
                            carId = car.id,
                            odometer = finalOdo,
                            provider = PROVIDER_TESLA,
                            syncTime = now
                        )
                        _lastSyncMessage.value = "${car.model}: $finalOdo mi"
                        OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_TESLA)
                    }
                }
                if (result != null) {
                    return result
                }
                delay(5_000L)
            } catch (e: Exception) {
                return OdometerSyncResult.Error(car.id, "Failed to read Tesla odometer")
            }
        }
        carDao.updateTelemetryStatus(car.id, PROVIDER_TESLA, "Asleep")
        return OdometerSyncResult.SkippedAsleep(car.id)
    }

    private suspend fun getValidSmartcarAccessToken(forceRefresh: Boolean = false): String? {
        val (access, refresh, expiry) = preferencesManager.getSmartcarTokens()
        val (clientId, clientSecret) = getEffectiveSmartcarCredentials()
        val isV3Client = clientId?.startsWith("client_") == true
        val now = System.currentTimeMillis()
        val isUnexpired = !access.isNullOrBlank() && (
            (!isV3Client && expiry == 0L) || (expiry > 0L && now < expiry - 60_000L)
        )
        if (!forceRefresh && isUnexpired) {
            return access
        }
        val fallbackIfUnexpired = access?.takeIf { expiry == 0L || now < expiry }
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            return fallbackIfUnexpired
        }

        // Smartcar API v3 IAM Client Credentials Flow (client_...)
        if (isV3Client || refresh.isNullOrBlank()) {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()

            val request = Request.Builder()
                .url(SMARTCAR_V3_IAM_TOKEN_URL)
                .post(body)
                .build()

            return try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return fallbackIfUnexpired
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return fallbackIfUnexpired
                    val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                    preferencesManager.saveSmartcarTokens(newAccess, null, expires)
                    newAccess
                }
            } catch (_: Exception) {
                fallbackIfUnexpired
            }
        }

        val basicAuth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()

        val request = Request.Builder()
            .url(SMARTCAR_AUTH_TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return fallbackIfUnexpired
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return fallbackIfUnexpired
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content ?: refresh
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L
                preferencesManager.saveSmartcarTokens(newAccess, newRefresh, expires)
                newAccess
            }
        } catch (_: Exception) {
            fallbackIfUnexpired
        }
    }

    private suspend fun syncSmartcarVehicle(car: Car): OdometerSyncResult {
        val (clientId, clientSecret) = getEffectiveSmartcarCredentials()
        val hasLinkedAccount = !preferencesManager.getSmartcarUserId().isNullOrBlank() ||
            !preferencesManager.getSmartcarVehicleId().isNullOrBlank()
        val token = getValidSmartcarAccessToken()
        if (token.isNullOrBlank()) {
            if (hasLinkedAccount && (clientId.isNullOrBlank() || clientSecret.isNullOrBlank())) {
                return OdometerSyncResult.Error(car.id, "Missing Smartcar Client ID/Secret in build or Settings")
            }
            return OdometerSyncResult.NotConfigured(car.id)
        }
        val isV3 = clientId?.startsWith("client_") == true ||
            !preferencesManager.getSmartcarUserId().isNullOrBlank()

        if (isV3) {
            return syncSmartcarV3Vehicle(car, token)
        }

        var vehicleId = preferencesManager.getSmartcarVehicleId()
        if (vehicleId.isNullOrBlank()) {
            val listReq = Request.Builder()
                .url("$SMARTCAR_API_BASE/vehicles")
                .addHeader("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(listReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return OdometerSyncResult.Error(car.id, "Smartcar (${resp.code})")
                    }
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val firstId = root["vehicles"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    if (firstId.isNullOrBlank()) {
                        return OdometerSyncResult.Error(car.id, "No Smartcar vehicle linked")
                    }
                    vehicleId = firstId
                    preferencesManager.setSmartcarVehicleId(firstId)
                }
            } catch (e: Exception) {
                return OdometerSyncResult.Error(car.id, "Network error")
            }
        }

        val odoReq = Request.Builder()
            .url("$SMARTCAR_API_BASE/vehicles/$vehicleId/odometer")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("sc-unit-system", "imperial")
            .build()

        return try {
            client.newCall(odoReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return OdometerSyncResult.Error(car.id, "Smartcar (${resp.code})")
                }
                val newOdo = parseSmartcarOdometerMiles(resp.body?.string().orEmpty())
                    ?: return OdometerSyncResult.Error(car.id, "Invalid odometer response")

                val finalOdo = maxOf(newOdo, car.odometer ?: 0)
                val now = System.currentTimeMillis()
                recordOdometerReading(
                    carId = car.id,
                    odometer = finalOdo,
                    provider = PROVIDER_SMARTCAR,
                    syncTime = now
                )
                _lastSyncMessage.value = "${car.model}: $finalOdo mi"
                OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_SMARTCAR)
            }
        } catch (e: Exception) {
            OdometerSyncResult.Error(car.id, "Failed to read Smartcar odometer")
        }
    }

    private suspend fun syncSmartcarV3Vehicle(car: Car, initialToken: String): OdometerSyncResult {
        var token = initialToken

        fun executeConnectionsCall(bearerToken: String): Pair<Int, String> {
            val connReq = Request.Builder()
                .url("$SMARTCAR_V3_API_BASE/connections")
                .addHeader("Authorization", "Bearer $bearerToken")
                .build()
            return client.newCall(connReq).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        val (vehicleId, userId) = try {
            var (code, bodyStr) = executeConnectionsCall(token)
            if (code == 401) {
                val refreshed = getValidSmartcarAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank() && refreshed != token) {
                    token = refreshed
                    val retry = executeConnectionsCall(token)
                    code = retry.first
                    bodyStr = retry.second
                }
            }
            if (code !in 200..299) {
                val detail = parseSmartcarErrorDetail(bodyStr)
                val msg = if (detail != null) "Smartcar v3 ($code: $detail)" else "Smartcar v3 ($code)"
                _lastSyncMessage.value = msg
                return OdometerSyncResult.Error(car.id, msg)
            }
            val preferredUserId = preferencesManager.getSmartcarUserId()
            val parsed = parseSmartcarV3Connection(bodyStr, preferredUserId)
            if (parsed == null) {
                preferencesManager.clearSmartcarVehicleConnection()
                val msg = "No vehicle linked in Smartcar — tap Connect in Settings"
                _lastSyncMessage.value = msg
                return OdometerSyncResult.Error(car.id, msg)
            }
            parsed
        } catch (e: Exception) {
            val msg = "Network error contacting Smartcar"
            _lastSyncMessage.value = msg
            return OdometerSyncResult.Error(car.id, msg)
        }

        preferencesManager.setSmartcarVehicleId(vehicleId, userId)

        fun executeOdometerSignalCall(bearerToken: String): Pair<Int, String> {
            val odoReqBuilder = Request.Builder()
                .url("$SMARTCAR_V3_API_BASE/vehicles/$vehicleId/signals/odometer-traveleddistance")
                .addHeader("Authorization", "Bearer $bearerToken")
                .addHeader("sc-unit-system", "imperial")
                .addHeader("Smartcar-Unit-System", "imperial")
            if (!userId.isNullOrBlank()) {
                odoReqBuilder.addHeader("sc-user-id", userId)
            }
            return client.newCall(odoReqBuilder.build()).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        return try {
            var (code, bodyStr) = executeOdometerSignalCall(token)
            if (code == 401) {
                val refreshed = getValidSmartcarAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank() && refreshed != token) {
                    token = refreshed
                    val retry = executeOdometerSignalCall(token)
                    code = retry.first
                    bodyStr = retry.second
                }
            }
            if (code !in 200..299) {
                val detail = parseSmartcarErrorDetail(bodyStr)
                val msg = if (detail != null) "Smartcar ($code: $detail)" else "Smartcar signal ($code)"
                _lastSyncMessage.value = msg
                return OdometerSyncResult.Error(car.id, msg)
            }
            val newOdo = parseSmartcarOdometerMiles(bodyStr)
            if (newOdo == null) {
                val detail = parseSmartcarErrorDetail(bodyStr) ?: "Invalid v3 odometer response"
                _lastSyncMessage.value = detail
                return OdometerSyncResult.Error(car.id, detail)
            }

            val finalOdo = maxOf(newOdo, car.odometer ?: 0)
            val now = System.currentTimeMillis()
            recordOdometerReading(
                carId = car.id,
                odometer = finalOdo,
                provider = PROVIDER_SMARTCAR,
                syncTime = now
            )
            _lastSyncMessage.value = "${car.model}: $finalOdo mi"
            OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_SMARTCAR)
        } catch (e: Exception) {
            val msg = "Failed to read Smartcar v3 odometer (${e.javaClass.simpleName})"
            _lastSyncMessage.value = msg
            OdometerSyncResult.Error(car.id, msg)
        }
    }

    suspend fun syncWallConnectorCharging(
        cars: List<Car>,
        lookbackDays: Int? = null
    ): WallConnectorSyncResult = withContext(Dispatchers.IO) {
        val dao = chargingSessionDao ?: return@withContext WallConnectorSyncResult.Error("Charging database unavailable")
        val token = getValidTeslaAccessToken() ?: return@withContext WallConnectorSyncResult.NotConfigured

        _isSyncing.value = true
        try {
            val productsReq = Request.Builder()
                .url("$TESLA_API_BASE/api/1/products")
                .addHeader("Authorization", "Bearer $token")
                .build()

            val (productsCode, productsBody) = try {
                client.newCall(productsReq).execute().use { resp ->
                    Pair(resp.code, resp.body?.string().orEmpty())
                }
            } catch (e: Exception) {
                val res = WallConnectorSyncResult.Error("Network error")
                preferencesManager.recordChargerSyncResult(
                    System.currentTimeMillis(),
                    formatWallConnectorSyncMessage(res)
                )
                return@withContext res
            }

            if (productsCode == 401 || productsCode == 403) {
                val missingScopeRes = WallConnectorSyncResult.MissingScope(
                    "Reconnect Tesla in Settings to grant energy_device_data scope"
                )
                preferencesManager.recordChargerSyncResult(
                    System.currentTimeMillis(),
                    missingScopeRes.message
                )
                return@withContext missingScopeRes
            }
            if (productsCode !in 200..299) {
                val errRes = WallConnectorSyncResult.Error("Tesla products API ($productsCode)")
                preferencesManager.recordChargerSyncResult(
                    System.currentTimeMillis(),
                    formatWallConnectorSyncMessage(errRes)
                )
                return@withContext errRes
            }

            val discoveredDevices = parseTeslaEnergySites(productsBody).toMutableList()
            val uniqueSiteIds = discoveredDevices.map { it.energySiteId }.distinct()

            if (uniqueSiteIds.isEmpty()) {
                val nowMs = System.currentTimeMillis()
                preferencesManager.recordChargerSyncResult(nowMs, "No Wall Connectors found on Tesla account")
                return@withContext WallConnectorSyncResult.Synced(
                    insertedCount = 0,
                    totalKwhAdded = 0.0,
                    totalSessionsScanned = 0
                )
            }

            // Enrich device metadata via /site_info when DIN or partNumber wasn't included in /products
            val enrichedDevices = mutableListOf<TeslaWallConnectorDevice>()
            for (siteId in uniqueSiteIds) {
                val siteDevices = discoveredDevices.filter { it.energySiteId == siteId }
                val needsSiteInfo = siteDevices.all { it.din.isNullOrBlank() && it.partNumber.isNullOrBlank() }
                if (needsSiteInfo) {
                    val infoReq = Request.Builder()
                        .url("$TESLA_API_BASE/api/1/energy_sites/$siteId/site_info")
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    val parsedInfo = try {
                        client.newCall(infoReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                parseTeslaSiteInfoWallConnectors(
                                    siteInfoJson = resp.body?.string().orEmpty(),
                                    energySiteId = siteId,
                                    fallbackSiteName = siteDevices.firstOrNull()?.siteName,
                                    startingIndex = enrichedDevices.size
                                )
                            } else {
                                emptyList()
                            }
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (parsedInfo.isNotEmpty()) {
                        enrichedDevices.addAll(parsedInfo)
                    } else {
                        enrichedDevices.addAll(siteDevices)
                    }
                } else {
                    enrichedDevices.addAll(siteDevices)
                }
            }

            val lastChargerSyncMs = preferencesManager.getLastChargerSyncTime()
            val effectiveLookbackDays = lookbackDays ?: if (lastChargerSyncMs == 0L) 30 else 7
            val zoneId = ZoneId.systemDefault()
            val endZdt = ZonedDateTime.now(zoneId)
            val startZdt = endZdt.minusDays(effectiveLookbackDays.toLong())
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
            val rfc3339Formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val encodedStart = URLEncoder.encode(startZdt.format(rfc3339Formatter), "UTF-8")
            val encodedEnd = URLEncoder.encode(endZdt.format(rfc3339Formatter), "UTF-8")
            val encodedTz = URLEncoder.encode(zoneId.id, "UTF-8")

            val allRawSessions = mutableListOf<TeslaWallConnectorChargeSession>()
            for (siteId in uniqueSiteIds) {
                val historyUrl = "$TESLA_API_BASE/api/1/energy_sites/$siteId/telemetry_history" +
                    "?kind=charge&start_date=$encodedStart&end_date=$encodedEnd&time_zone=$encodedTz"
                val historyReq = Request.Builder()
                    .url(historyUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                try {
                    client.newCall(historyReq).execute().use { resp ->
                        if (resp.code == 401 || resp.code == 403) {
                            val missingScopeRes = WallConnectorSyncResult.MissingScope(
                                "Reconnect Tesla in Settings to grant energy_device_data scope"
                            )
                            preferencesManager.recordChargerSyncResult(
                                System.currentTimeMillis(),
                                missingScopeRes.message
                            )
                            return@withContext missingScopeRes
                        }
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string().orEmpty()
                            allRawSessions.addAll(parseTeslaWallConnectorChargeHistory(bodyStr, siteId))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Wall Connector telemetry_history fetch failed for $siteId: ${e.message}")
                }
            }

            val existingSessions = dao.getAllSessionsList()
            val existingIds = existingSessions.map { it.id }.toSet()
            val existingFingerprints = existingSessions.map { it.fingerprint }.toSet()
            val (gen3OverrideCarId, universalOverrideCarId) = preferencesManager.getWallConnectorCarMapping()
            val nowMs = System.currentTimeMillis()

            val newEntities = buildWallConnectorSessionEntities(
                rawSessions = allRawSessions,
                devices = enrichedDevices,
                cars = cars,
                gen3OverrideCarId = gen3OverrideCarId,
                universalOverrideCarId = universalOverrideCarId,
                existingIds = existingIds,
                existingFingerprints = existingFingerprints,
                uploadedAtMs = nowMs,
                zoneId = zoneId
            )

            if (newEntities.isNotEmpty()) {
                dao.insertSessions(newEntities)
            }

            val totalKwhAdded = newEntities.sumOf { it.kwh }
            val result = WallConnectorSyncResult.Synced(
                insertedCount = newEntities.size,
                totalKwhAdded = totalKwhAdded,
                totalSessionsScanned = allRawSessions.size
            )
            val statusMsg = formatWallConnectorSyncMessage(result)
            preferencesManager.recordChargerSyncResult(nowMs, statusMsg)
            _lastSyncMessage.value = statusMsg
            result
        } finally {
            _isSyncing.value = false
        }
    }
}
