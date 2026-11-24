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
import java.time.temporal.ChronoUnit
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

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
    data class NoDevices(val message: String = "No Wall Connectors found on Tesla account") : WallConnectorSyncResult()
    data class MissingScope(val message: String) : WallConnectorSyncResult()
    data class Error(val message: String) : WallConnectorSyncResult()
}

data class TeslaChargeSnapshot(
    val vin: String,
    val chargeEnergyAddedKwh: Double,
    val chargingState: String? = null,
    val fastChargerPresent: Boolean = false,
    val fastChargerType: String? = null,
    val connChargeCable: String? = null,
    val batteryLevel: Int? = null,
    val timestampMs: Long? = null,
    val odometerMiles: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isCachedResponse: Boolean = false
)

data class WorkLocationConfig(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = PreferencesManager.DEFAULT_WORK_LOCATION_RADIUS_METERS
)

sealed class WorkChargeDecision {
    data class CaptureNew(
        val energyKwh: Double,
        val sessionId: String,
        val verifiedByGps: Boolean
    ) : WorkChargeDecision()

    data class UpdateExisting(
        val existingSession: ChargingSessionEntity,
        val updatedEnergyKwh: Double,
        val verifiedByGps: Boolean
    ) : WorkChargeDecision()

    data class AlreadyLogged(val energyKwh: Double) : WorkChargeDecision()

    data class Skip(
        val reason: String,
        val watermarkVenue: String?
    ) : WorkChargeDecision()
}

sealed class WorkChargingSyncResult {
    data class Captured(
        val kwh: Double,
        val updatedExisting: Boolean,
        val verifiedByGps: Boolean
    ) : WorkChargingSyncResult()

    data class UpToDate(val message: String) : WorkChargingSyncResult()
    data class SkippedAsleep(val message: String = "Tesla asleep (zero-wake check skipped)") : WorkChargingSyncResult()
    data object NotConfigured : WorkChargingSyncResult()
    data class Error(val message: String) : WorkChargingSyncResult()
}

sealed class WorkLocationCaptureResult {
    data class Saved(val latitude: Double, val longitude: Double) : WorkLocationCaptureResult()
    data class MissingScope(val message: String) : WorkLocationCaptureResult()
    data class SkippedAsleep(val message: String = "Tesla is asleep — wake vehicle to save work location") : WorkLocationCaptureResult()
    data object NotConfigured : WorkLocationCaptureResult()
    data class Error(val message: String) : WorkLocationCaptureResult()
}

data class TeslaSuperchargerSession(
    val sessionId: String,
    val vin: String? = null,
    val vehicleMakeType: String? = null,
    val siteLocationName: String? = null,
    val startDateMs: Long,
    val endDateMs: Long,
    val energyKwh: Double,
    val totalPaidCost: Double,
    val currencyCode: String? = null
)

sealed class SuperchargerSyncResult {
    data class Synced(
        val insertedCount: Int,
        val totalKwhAdded: Double,
        val totalCostAdded: Double,
        val totalSessionsScanned: Int
    ) : SuperchargerSyncResult()

    data object NotConfigured : SuperchargerSyncResult()
    data class MissingScope(val message: String) : SuperchargerSyncResult()
    data class Error(val message: String) : SuperchargerSyncResult()
}

data class SmartcarChargeRecord(
    val id: String,
    val startDateMs: Long,
    val endDateMs: Long,
    val energyKwh: Double,
    val chargingType: String? = null,
    val isPublicCharger: Boolean? = null,
    val costTotal: Double? = null,
    val locationName: String? = null
)

data class SmartcarLiveDcSnapshot(
    val isFastChargerPresent: Boolean? = null,
    val fastChargerType: String? = null,
    val wattageWatts: Double? = null,
    val energyAddedKwh: Double? = null,
    val isCharging: Boolean? = null
)

sealed class SmartcarDcSyncResult {
    data class Synced(
        val insertedCount: Int,
        val totalKwhAdded: Double,
        val totalCostAdded: Double,
        val source: String
    ) : SmartcarDcSyncResult()

    data object NotConfigured : SmartcarDcSyncResult()
    data class MissingPermission(val message: String) : SmartcarDcSyncResult()
    data class UnsupportedByVehicle(val message: String) : SmartcarDcSyncResult()
    data class NotCurrentlyFastCharging(val message: String) : SmartcarDcSyncResult()
    data class Error(val message: String) : SmartcarDcSyncResult()
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
        const val TESLA_AUTH_TOKEN_URL = "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token"
        const val TESLA_SCOPE_ENERGY_DEVICE_DATA = "energy_device_data"
        const val TESLA_SCOPE_VEHICLE_LOCATION = "vehicle_location"
        const val TESLA_SCOPE_VEHICLE_CHARGING_CMDS = "vehicle_charging_cmds"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private fun JsonElement?.asStringOrNull(): String? =
            (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonElement?.asDoubleOrNull(): Double? =
            (this as? JsonPrimitive)?.doubleOrNull

        private fun JsonElement?.asLongOrNull(): Long? =
            (this as? JsonPrimitive)?.longOrNull

        private fun JsonElement?.asIntOrNull(): Int? =
            (this as? JsonPrimitive)?.intOrNull

        private fun JsonElement?.asBooleanOrNull(): Boolean? =
            (this as? JsonPrimitive)?.booleanOrNull

        internal fun parseJwtClaims(token: String): JsonObject? {
            val parts = token.trim().split(".")
            if (parts.size != 3 || parts[1].isBlank()) return null
            return try {
                val decoded = java.util.Base64.getUrlDecoder().decode(parts[1])
                json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as? JsonObject
            } catch (_: Exception) {
                null
            }
        }

        internal fun extractJwtExpiryMs(token: String): Long? {
            val expSec = parseJwtClaims(token)?.get("exp").asLongOrNull() ?: return null
            return if (expSec > 0L) expSec * 1000L else null
        }

        internal fun extractJwtScopes(token: String): Set<String>? {
            val claims = parseJwtClaims(token) ?: return null
            val scpElem = claims["scp"] ?: claims["scope"] ?: return null
            val scopes = when (scpElem) {
                is JsonArray -> scpElem.mapNotNull { it.asStringOrNull() }
                is JsonPrimitive -> scpElem.contentOrNull
                    ?.split(Regex("[\\s,]+"))
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()
                else -> emptyList()
            }
            return scopes.toSet().takeIf { it.isNotEmpty() }
        }

        fun hasTeslaLocationScope(token: String?): Boolean {
            if (token.isNullOrBlank()) return false
            return extractJwtScopes(token)?.contains(TESLA_SCOPE_VEHICLE_LOCATION) == true
        }

        fun hasTeslaChargingHistoryScope(token: String?): Boolean {
            if (token.isNullOrBlank()) return false
            return extractJwtScopes(token)?.contains(TESLA_SCOPE_VEHICLE_CHARGING_CMDS) == true
        }

        internal fun parseTeslaErrorDetail(rawBody: String): String? {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return null
            return try {
                val root = json.parseToJsonElement(trimmed) as? JsonObject ?: return trimmed.take(160)
                val err = root["error"].asStringOrNull()
                val desc = root["error_description"].asStringOrNull()
                val msg = root["message"].asStringOrNull()
                when {
                    desc != null && err != null && !desc.equals(err, ignoreCase = true) -> "$err: $desc"
                    desc != null -> desc
                    err != null -> err
                    msg != null -> msg
                    else -> null
                }
            } catch (_: Exception) {
                trimmed.take(160)
            }
        }

        internal fun isTeslaScopeError(httpCode: Int, errorDetail: String?): Boolean {
            if (httpCode != 403) return false
            if (errorDetail.isNullOrBlank()) return true
            val lower = errorDetail.lowercase(Locale.US)
            return lower.contains("scope") ||
                lower.contains("consent") ||
                lower.contains("permission") ||
                lower.contains("forbidden") ||
                lower.contains("energy_device_data") ||
                lower.contains("vehicle_location") ||
                lower.contains("vehicle_charging_cmds") ||
                lower.contains("unauthorized") ||
                lower.contains("insufficient")
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
                    "$name is asleep"
                is OdometerSyncResult.SkippedCooldown ->
                    "$name synced recently (${result.hoursRemaining}h cooldown)"
                is OdometerSyncResult.SkippedPolicy ->
                    "$name sync skipped (${result.policy.label})"
                is OdometerSyncResult.NotConfigured ->
                    "$name isn't connected — set up in Settings"
                is OdometerSyncResult.Error ->
                    "Couldn't sync $name: ${result.message}"
            }
        }

        fun formatDailyPollSummary(
            cars: List<Car>,
            results: List<OdometerSyncResult>,
            chargerResult: WallConnectorSyncResult? = null
        ): String {
            if (cars.isEmpty() && results.isEmpty()) {
                return "No vehicles set up"
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
                        is OdometerSyncResult.NotConfigured -> carMap[res.carId]?.model?.let { "$it: not connected" }
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
                    "Tesla isn't connected — set up in Settings"
                is WallConnectorSyncResult.NoDevices ->
                    result.message
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
                effectivePart.contains("GEN 3") ||
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
                val root = json.parseToJsonElement(productsJson) as? JsonObject ?: return emptyList()
                val responseArr = root["response"] as? JsonArray ?: return emptyList()
                val devices = mutableListOf<TeslaWallConnectorDevice>()

                for (element in responseArr) {
                    val obj = element as? JsonObject ?: continue
                    // Skip pure vehicle objects from /api/1/products
                    val hasVin = obj["vin"].asStringOrNull() != null
                    val energySiteId = obj["energy_site_id"].asStringOrNull()
                    val resourceType = obj["resource_type"].asStringOrNull()?.lowercase(Locale.US).orEmpty()
                    val componentsObj = obj["components"] as? JsonObject
                    val wallConnectorsArr = (componentsObj?.get("wall_connectors") as? JsonArray)
                        ?: (obj["wall_connectors"] as? JsonArray)

                    if (energySiteId == null && wallConnectorsArr == null &&
                        (resourceType == "vehicle" || (hasVin && resourceType.isEmpty()))
                    ) {
                        continue
                    }

                    val siteId = energySiteId
                        ?: obj["id"].asStringOrNull()
                        ?: continue
                    val siteName = obj["site_name"].asStringOrNull()

                    if (wallConnectorsArr != null && wallConnectorsArr.isNotEmpty()) {
                        for (wcElem in wallConnectorsArr) {
                            when (wcElem) {
                                is JsonObject -> {
                                    val din = wcElem["din"].asStringOrNull()
                                        ?: wcElem["id"].asStringOrNull()
                                        ?: wcElem["device_id"].asStringOrNull()
                                    val partNumber = wcElem["part_number"].asStringOrNull()
                                        ?: extractPartNumberFromDin(din)
                                    val partName = wcElem["part_name"].asStringOrNull()
                                    val wcName = wcElem["site_name"].asStringOrNull()
                                        ?: wcElem["name"].asStringOrNull()
                                        ?: siteName
                                    val classificationHint = listOfNotNull(wcName, partName)
                                        .joinToString(" ")
                                        .takeIf { it.isNotBlank() }
                                    val type = classifyWallConnectorType(
                                        din = din,
                                        partNumber = partNumber,
                                        siteName = classificationHint,
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
                                is JsonPrimitive -> {
                                    val din = wcElem.asStringOrNull() ?: continue
                                    val partNumber = extractPartNumberFromDin(din)
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
                                else -> continue
                            }
                        }
                    } else {
                        val rawId = obj["id"].asStringOrNull()
                        val din = obj["din"].asStringOrNull()
                            ?: rawId?.takeIf { it != siteId && it.contains("--") }
                        val partNumber = obj["part_number"].asStringOrNull()
                            ?: extractPartNumberFromDin(din)
                        val partName = obj["part_name"].asStringOrNull()
                        val classificationHint = listOfNotNull(siteName, partName)
                            .joinToString(" ")
                            .takeIf { it.isNotBlank() }
                        val type = classifyWallConnectorType(
                            din = din,
                            partNumber = partNumber,
                            siteName = classificationHint,
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
                val root = json.parseToJsonElement(siteInfoJson) as? JsonObject ?: return emptyList()
                val response = (root["response"] as? JsonObject) ?: root
                val siteName = response["site_name"].asStringOrNull()
                    ?: fallbackSiteName
                val components = response["components"] as? JsonObject
                val wallConnectors = (components?.get("wall_connectors") as? JsonArray)
                    ?: (response["wall_connectors"] as? JsonArray)

                val devices = mutableListOf<TeslaWallConnectorDevice>()
                if (wallConnectors != null && wallConnectors.isNotEmpty()) {
                    for (wcElem in wallConnectors) {
                        when (wcElem) {
                            is JsonObject -> {
                                val din = wcElem["din"].asStringOrNull()
                                    ?: wcElem["id"].asStringOrNull()
                                    ?: wcElem["device_id"].asStringOrNull()
                                val partNumber = wcElem["part_number"].asStringOrNull()
                                    ?: extractPartNumberFromDin(din)
                                val partName = wcElem["part_name"].asStringOrNull()
                                val wcName = wcElem["site_name"].asStringOrNull()
                                    ?: wcElem["name"].asStringOrNull()
                                    ?: siteName
                                val classificationHint = listOfNotNull(wcName, partName)
                                    .joinToString(" ")
                                    .takeIf { it.isNotBlank() }
                                val type = classifyWallConnectorType(
                                    din = din,
                                    partNumber = partNumber,
                                    siteName = classificationHint,
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
                            is JsonPrimitive -> {
                                val din = wcElem.asStringOrNull() ?: continue
                                val partNumber = extractPartNumberFromDin(din)
                                val type = classifyWallConnectorType(
                                    din = din,
                                    partNumber = partNumber,
                                    siteName = siteName,
                                    index = startingIndex + devices.size
                                )
                                devices.add(
                                    TeslaWallConnectorDevice(
                                        energySiteId = energySiteId,
                                        din = din,
                                        partNumber = partNumber,
                                        siteName = siteName,
                                        connectorType = type
                                    )
                                )
                            }
                            else -> continue
                        }
                    }
                } else {
                    val din = response["din"].asStringOrNull()
                        ?: response["id"].asStringOrNull()?.takeIf { it != energySiteId }
                    val partNumber = response["part_number"].asStringOrNull()
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
            if (raw is JsonObject) {
                val secElem = raw["seconds"]
                    ?: raw["epoch_seconds"]
                    ?: raw["unix"]
                    ?: raw["timestamp"]
                    ?: raw["ms"]
                    ?: raw["millis"]
                    ?: raw["value"]
                    ?: raw["iso"]
                    ?: raw["date"]
                val baseMs = parseFlexibleTimestampMs(secElem) ?: return null
                val nanos = raw["nanos"].asLongOrNull()
                    ?: raw["nanoseconds"].asLongOrNull()
                    ?: 0L
                return baseMs + (nanos / 1_000_000L)
            }
            val prim = raw as? JsonPrimitive ?: return null
            prim.longOrNull?.let { num ->
                // Distinguish epoch seconds vs epoch milliseconds
                return if (num < 100_000_000_000L) num * 1000L else num
            }
            val text = prim.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) return null
            text.toLongOrNull()?.let { num ->
                return if (num < 100_000_000_000L) num * 1000L else num
            }
            (prim.doubleOrNull ?: text.toDoubleOrNull())?.let { num ->
                if (num.isFinite() && num > 0.0) {
                    return if (num < 100_000_000_000.0) (num * 1000.0).roundToLong() else num.roundToLong()
                }
            }
            return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
                ?: ElectricityImportParser.parseDateToStartOfDayMs(text)
        }

        private fun parseFlexibleDurationSeconds(raw: JsonElement?): Long? {
            if (raw is JsonObject) {
                val sec = raw["seconds"].asDoubleOrNull()
                    ?: raw["value"].asDoubleOrNull()
                    ?: raw["duration_seconds"].asDoubleOrNull()
                    ?: raw["duration"].asDoubleOrNull()
                if (sec != null && sec.isFinite() && sec >= 0.0) {
                    val nanos = raw["nanos"].asLongOrNull()
                        ?: raw["nanoseconds"].asLongOrNull()
                        ?: 0L
                    return (sec + (nanos / 1_000_000_000.0)).roundToLong().takeIf { it >= 0L }
                }
                val nested = raw["seconds"] ?: raw["value"] ?: raw["duration"]
                return parseFlexibleDurationSeconds(nested)
            }
            val prim = raw as? JsonPrimitive ?: return null
            prim.longOrNull?.let { num -> return num.takeIf { it >= 0L } }
            prim.doubleOrNull?.let { num ->
                if (num.isFinite() && num >= 0.0) return num.roundToLong()
            }
            val text = prim.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) return null
            text.toLongOrNull()?.let { return it.takeIf { v -> v >= 0L } }
            text.toDoubleOrNull()?.let { num ->
                if (num.isFinite() && num >= 0.0) return num.roundToLong()
            }
            return runCatching { java.time.Duration.parse(text).seconds }.getOrNull()?.takeIf { it >= 0L }
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
                                (respElem["charge_history"] as? JsonArray)
                                    ?: (respElem["time_series"] as? JsonArray)
                                    ?: (respElem["sessions"] as? JsonArray)
                                    ?: (respElem["data"] as? JsonArray)
                                    ?: (respElem["charging_history"] as? JsonArray)
                                    ?: (respElem["events"] as? JsonArray)
                                    ?: (respElem["items"] as? JsonArray)
                                    ?: return emptyList()
                            }
                            else -> {
                                (rootElement["charge_history"] as? JsonArray)
                                    ?: (rootElement["time_series"] as? JsonArray)
                                    ?: (rootElement["sessions"] as? JsonArray)
                                    ?: (rootElement["data"] as? JsonArray)
                                    ?: return emptyList()
                            }
                        }
                    }
                    else -> return emptyList()
                }

                val sessions = mutableListOf<TeslaWallConnectorChargeSession>()
                for (elem in seriesArray) {
                    val obj = elem as? JsonObject ?: continue

                    val energyWh = obj["charge_energy_imported"].asDoubleOrNull()
                        ?: obj["energy_wh"].asDoubleOrNull()
                        ?: obj["energy_added_wh"].asDoubleOrNull()
                        ?: obj["charge_energy_added_wh"].asDoubleOrNull()
                        ?: obj["energy_imported_wh"].asDoubleOrNull()
                        ?: obj["total_energy_wh"].asDoubleOrNull()
                        ?: obj["grid_energy_imported"].asDoubleOrNull()
                        ?: obj["total_energy"].asDoubleOrNull()
                        ?: obj["value"].asDoubleOrNull()
                        ?: (obj["charge_energy_imported_kwh"].asDoubleOrNull()?.times(1000.0))
                        ?: (obj["energy_kwh"].asDoubleOrNull()?.times(1000.0))
                        ?: (obj["energy_added_kwh"].asDoubleOrNull()?.times(1000.0))
                        ?: (obj["charge_energy_added_kwh"].asDoubleOrNull()?.times(1000.0))
                        ?: continue

                    // Filter out zero/standby noise (< 50 Wh = 0.05 kWh)
                    if (energyWh < 50.0) continue

                    val startMs = parseFlexibleTimestampMs(
                        obj["charge_start_time"]
                            ?: obj["charging_start_time"]
                            ?: obj["start_date"]
                            ?: obj["start_time"]
                            ?: obj["timestamp"]
                            ?: obj["datetime"]
                            ?: obj["time"]
                    ) ?: continue

                    val explicitEndMs = parseFlexibleTimestampMs(
                        obj["charge_stop_time"]
                            ?: obj["charging_stop_time"]
                            ?: obj["end_date"]
                            ?: obj["stop_time"]
                            ?: obj["end_time"]
                    )
                    val durationSec = parseFlexibleDurationSeconds(
                        obj["charge_duration"]
                            ?: obj["charging_duration"]
                            ?: obj["duration_seconds"]
                            ?: obj["duration"]
                    )

                    val endMs = when {
                        explicitEndMs != null && explicitEndMs >= startMs -> explicitEndMs
                        durationSec != null && durationSec > 0L -> startMs + (durationSec * 1000L)
                        else -> startMs
                    }

                    val din = obj["din"].asStringOrNull()
                        ?: obj["wall_connector_din"].asStringOrNull()
                        ?: obj["wall_connector_id"].asStringOrNull()
                        ?: obj["device_id"].asStringOrNull()
                        ?: obj["serial_number"].asStringOrNull()
                    val vin = obj["vin"].asStringOrNull()
                        ?: obj["vehicle_vin"].asStringOrNull()

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
                val root = json.parseToJsonElement(jsonBody) as? JsonObject ?: return null
                val responseArr = root["response"] as? JsonArray ?: return null
                for (element in responseArr) {
                    val obj = element as? JsonObject ?: continue
                    val vin = obj["vin"].asStringOrNull()?.uppercase(Locale.US) ?: continue
                    val state = obj["state"].asStringOrNull()?.lowercase(Locale.US) ?: "unknown"
                    if (targetVin.isNullOrBlank() || vin == targetVin.trim().uppercase(Locale.US)) {
                        return Pair(vin, state)
                    }
                }
                val first = responseArr.firstOrNull() as? JsonObject ?: return null
                val vin = first["vin"].asStringOrNull() ?: return null
                val state = first["state"].asStringOrNull()?.lowercase(Locale.US) ?: "unknown"
                Pair(vin, state)
            } catch (_: Exception) {
                null
            }
        }

        fun parseTeslaOdometerMiles(jsonBody: String): Int? {
            return try {
                val root = json.parseToJsonElement(jsonBody) as? JsonObject ?: return null
                val response = (root["response"] as? JsonObject) ?: root
                val dataObj = (response["data"] as? JsonObject) ?: response
                val vehicleState = (dataObj["vehicle_state"] as? JsonObject) ?: dataObj
                val odo = vehicleState["odometer"].asDoubleOrNull()
                odo?.roundToInt()?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }

        fun parseTeslaWakeState(jsonBody: String): String? {
            return try {
                val root = json.parseToJsonElement(jsonBody) as? JsonObject ?: return null
                val response = (root["response"] as? JsonObject) ?: root
                response["state"].asStringOrNull()?.lowercase(Locale.US)
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

        /**
         * Sends at most ONE paid POST /wake_up request ($0.02), then polls vehicle connectivity
         * state (GET /api/1/vehicles/{vin}) until online instead of repeating POST /wake_up.
         */
        suspend fun wakeAndPollUntilOnline(
            maxStatePolls: Int = 6,
            delayMs: Long = 5_000L,
            sendWake: suspend () -> String?,
            pollState: suspend () -> String?
        ): Boolean {
            val initialState = try {
                sendWake()
            } catch (_: Exception) {
                null
            }
            if (initialState == "online") {
                return true
            }
            for (attempt in 1 until maxStatePolls.coerceAtLeast(1)) {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                val currentState = try {
                    pollState()
                } catch (_: Exception) {
                    null
                }
                if (currentState == "online") {
                    return true
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

        fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val earthRadiusMeters = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2.0) * sin(dLng / 2.0)
            val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
            return earthRadiusMeters * c
        }

        fun parseTeslaChargeSnapshot(
            jsonBody: String,
            vin: String,
            isCachedResponse: Boolean = false
        ): TeslaChargeSnapshot? {
            return try {
                val root = json.parseToJsonElement(jsonBody) as? JsonObject ?: return null
                val response = (root["response"] as? JsonObject) ?: root
                val payload = (response["data"] as? JsonObject) ?: response
                val chargeState = (payload["charge_state"] as? JsonObject) ?: return null
                val energyAdded = chargeState["charge_energy_added"].asDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= 0.0 } ?: return null

                val vehicleState = payload["vehicle_state"] as? JsonObject
                val driveState = payload["drive_state"] as? JsonObject
                val locationData = payload["location_data"] as? JsonObject
                val fastChargerPresent = chargeState["fast_charger_present"].asBooleanOrNull()
                    ?: chargeState["fast_charger_present"].asStringOrNull().equals("true", ignoreCase = true)

                val lat = driveState?.get("latitude").asDoubleOrNull()
                    ?: locationData?.get("latitude").asDoubleOrNull()
                val lng = driveState?.get("longitude").asDoubleOrNull()
                    ?: locationData?.get("longitude").asDoubleOrNull()

                TeslaChargeSnapshot(
                    vin = vin.trim().uppercase(Locale.US),
                    chargeEnergyAddedKwh = energyAdded,
                    chargingState = chargeState["charging_state"].asStringOrNull(),
                    fastChargerPresent = fastChargerPresent,
                    fastChargerType = chargeState["fast_charger_type"].asStringOrNull(),
                    connChargeCable = chargeState["conn_charge_cable"].asStringOrNull(),
                    batteryLevel = chargeState["battery_level"].asIntOrNull(),
                    timestampMs = parseFlexibleTimestampMs(chargeState["timestamp"])
                        ?: parseFlexibleTimestampMs(vehicleState?.get("timestamp")),
                    odometerMiles = vehicleState?.get("odometer").asDoubleOrNull()?.roundToInt()?.takeIf { it >= 0 },
                    latitude = lat,
                    longitude = lng,
                    isCachedResponse = isCachedResponse
                )
            } catch (_: Exception) {
                null
            }
        }

        fun evaluateWorkChargeCandidate(
            snapshot: TeslaChargeSnapshot,
            targetCarId: String?,
            existingSessions: List<ChargingSessionEntity>,
            workLocation: WorkLocationConfig? = null,
            lastWatermarkEnergy: Double = -1.0,
            lastWatermarkDateIso: String? = null,
            lastWatermarkVenue: String? = null,
            nowMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): WorkChargeDecision {
            val energy = round(snapshot.chargeEnergyAddedKwh * 100.0) / 100.0
            if (snapshot.fastChargerPresent) {
                return WorkChargeDecision.Skip(
                    reason = "Fast charger / Supercharger session",
                    watermarkVenue = ElectricityImportParser.VENUE_PUBLIC
                )
            }
            if (energy < 0.5) {
                return WorkChargeDecision.Skip(
                    reason = "No work charge detected (< 0.5 kWh)",
                    watermarkVenue = null
                )
            }

            val stateLower = snapshot.chargingState?.trim()?.lowercase(Locale.US)
            val isPluggedIn = stateLower == "charging" || stateLower == "complete" || stateLower == "stopped"
            val isActivelyChargedHere = stateLower == "charging" || stateLower == "complete"
            var verifiedByGps = false

            if (isPluggedIn && workLocation != null && snapshot.latitude != null && snapshot.longitude != null) {
                val distMeters = haversineMeters(
                    lat1 = snapshot.latitude,
                    lng1 = snapshot.longitude,
                    lat2 = workLocation.latitude,
                    lng2 = workLocation.longitude
                )
                if (distMeters <= workLocation.radiusMeters) {
                    verifiedByGps = true
                } else if (isActivelyChargedHere) {
                    return WorkChargeDecision.Skip(
                        reason = "Plugged in away from Work (${distMeters.roundToInt()}m away)",
                        watermarkVenue = "SKIPPED"
                    )
                }
                // Note: If state == "Stopped" outside Work (e.g., got home early and plugged in
                // waiting for overnight off-peak TOU charging), fall through to the 36h Home Wall
                // Connector echo check so an earlier work charge still in charge_energy_added is preserved.
            }

            if (!verifiedByGps) {
                val windowStart = nowMs - 36L * 3_600_000L
                val windowEnd = nowMs + 3_600_000L
                val matchesRecentHome = existingSessions.any { session ->
                    !session.isSummary &&
                        ElectricityImportParser.normalizeVenue(session.venue) == ElectricityImportParser.VENUE_HOME &&
                        (session.carId == null || targetCarId == null || session.carId == targetCarId) &&
                        maxOf(session.startDateMs, session.endDateMs) in windowStart..windowEnd &&
                        session.kwh > 0.0 &&
                        energy >= session.kwh * 0.75 &&
                        energy <= session.kwh * 1.12
                }
                if (matchesRecentHome) {
                    return WorkChargeDecision.Skip(
                        reason = "Matches recent Home Wall Connector session",
                        watermarkVenue = ElectricityImportParser.VENUE_HOME
                    )
                }
            }

            val todayIso = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toString()
            val cleanVin = snapshot.vin.replace(Regex("[^A-Za-z0-9]"), "").uppercase(Locale.US).ifBlank { "TESLA" }
            val sessionId = "tesla_work_${cleanVin}_${todayIso}"

            val existingToday = existingSessions.firstOrNull { it.id == sessionId }
            if (existingToday != null) {
                return if (energy > existingToday.kwh + 0.1) {
                    WorkChargeDecision.UpdateExisting(
                        existingSession = existingToday,
                        updatedEnergyKwh = energy,
                        verifiedByGps = verifiedByGps
                    )
                } else {
                    WorkChargeDecision.AlreadyLogged(existingToday.kwh)
                }
            }

            if (stateLower != "charging" &&
                lastWatermarkEnergy >= 0.0 &&
                abs(energy - lastWatermarkEnergy) < 0.05 &&
                !lastWatermarkDateIso.isNullOrBlank() &&
                lastWatermarkDateIso != todayIso
            ) {
                return WorkChargeDecision.Skip(
                    reason = "Previous day's charge already processed",
                    watermarkVenue = lastWatermarkVenue
                )
            }

            return WorkChargeDecision.CaptureNew(
                energyKwh = energy,
                sessionId = sessionId,
                verifiedByGps = verifiedByGps
            )
        }

        fun formatWorkChargingSyncMessage(result: WorkChargingSyncResult): String = when (result) {
            is WorkChargingSyncResult.Captured -> {
                val kwhStr = String.format(Locale.US, "%.1f", result.kwh)
                val suffix = if (result.verifiedByGps) " · GPS verified" else ""
                if (result.updatedExisting) {
                    "Updated work charge: $kwhStr kWh (Free$suffix)"
                } else {
                    "Logged work charge: $kwhStr kWh (Free$suffix)"
                }
            }
            is WorkChargingSyncResult.UpToDate -> result.message
            is WorkChargingSyncResult.SkippedAsleep -> result.message
            WorkChargingSyncResult.NotConfigured -> "Connect Tesla in Settings to capture work charging"
            is WorkChargingSyncResult.Error -> "Work charge sync failed: ${result.message}"
        }

        fun parseTeslaSuperchargerHistory(rawJson: String): Pair<List<TeslaSuperchargerSession>, Boolean> {
            return try {
                val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return Pair(emptyList(), false)
                val responseElem = root["response"]
                val containerObj = (responseElem as? JsonObject) ?: root
                val dataArr = (responseElem as? JsonArray)
                    ?: (containerObj["data"] as? JsonArray)
                    ?: return Pair(emptyList(), false)

                val explicitHasMore = containerObj["hasMore"].asBooleanOrNull()
                    ?: containerObj["has_more"].asBooleanOrNull()
                    ?: false

                val sessions = mutableListOf<TeslaSuperchargerSession>()
                for (elem in dataArr) {
                    val obj = elem as? JsonObject ?: continue
                    val sessionId = obj["sessionId"].asStringOrNull()
                        ?: obj["chargeSessionId"].asStringOrNull()
                        ?: continue
                    val startMs = parseFlexibleTimestampMs(obj["chargeStartDateTime"]) ?: continue
                    val rawEndMs = parseFlexibleTimestampMs(obj["chargeStopDateTime"])
                        ?: parseFlexibleTimestampMs(obj["unlatchDateTime"])
                    val endMs = if (rawEndMs != null && rawEndMs >= startMs) rawEndMs else startMs

                    val feesArr = (obj["fees"] as? JsonArray).orEmpty()
                    var totalKwh = 0.0
                    var totalCost = 0.0
                    var currency: String? = null

                    for (feeElem in feesArr) {
                        val feeObj = feeElem as? JsonObject ?: continue
                        currency = currency ?: feeObj["currencyCode"].asStringOrNull()
                        val feeType = feeObj["feeType"].asStringOrNull()?.uppercase(Locale.US).orEmpty()
                        val uom = feeObj["uom"].asStringOrNull()?.lowercase(Locale.US).orEmpty()

                        val netDue = feeObj["netDue"].asDoubleOrNull()
                        val totalDue = feeObj["totalDue"].asDoubleOrNull()
                        val tierTotalsSum = listOf(
                            "totalBase", "totalTier1", "totalTier2", "totalTier3", "totalTier4"
                        ).sumOf { feeObj[it].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
                        val feeCost = (netDue ?: totalDue ?: tierTotalsSum).coerceAtLeast(0.0)
                        totalCost += feeCost

                        val isTimeUom = uom == "min" || uom.startsWith("min") ||
                            uom.startsWith("sec") || uom.startsWith("hour")
                        if ((feeType == "CHARGING" || uom == "kwh") && !isTimeUom) {
                            val usageBase = feeObj["usageBase"].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            var feeKwh = usageBase
                            for (tier in 1..4) {
                                val usageT = feeObj["usageTier$tier"].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                val rateT = feeObj["rateTier$tier"].asDoubleOrNull() ?: 0.0
                                val totalT = feeObj["totalTier$tier"].asDoubleOrNull() ?: 0.0
                                // Only count usageTierN when actually billed to avoid counting unbilled minute tiers
                                if (usageT > 0.0 && (rateT > 0.0 || totalT > 0.0)) {
                                    feeKwh += usageT
                                }
                            }
                            if (feeKwh <= 0.0 && uom == "kwh") {
                                feeKwh = (1..4).sumOf {
                                    feeObj["usageTier$it"].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                                }
                            }
                            totalKwh += feeKwh
                        }
                    }

                    val roundedKwh = round(totalKwh * 1000.0) / 1000.0
                    if (roundedKwh < 0.1) continue
                    val roundedCost = round(totalCost * 100.0) / 100.0

                    sessions.add(
                        TeslaSuperchargerSession(
                            sessionId = sessionId,
                            vin = obj["vin"].asStringOrNull()?.uppercase(Locale.US),
                            vehicleMakeType = obj["vehicleMakeType"].asStringOrNull()?.uppercase(Locale.US),
                            siteLocationName = obj["siteLocationName"].asStringOrNull(),
                            startDateMs = startMs,
                            endDateMs = endMs,
                            energyKwh = roundedKwh,
                            totalPaidCost = roundedCost,
                            currencyCode = currency
                        )
                    )
                }
                Pair(sessions.sortedBy { it.startDateMs }, explicitHasMore)
            } catch (_: Exception) {
                Pair(emptyList(), false)
            }
        }

        private fun hasOverlappingPublicSession(
            existingSessions: List<ChargingSessionEntity>,
            carId: String?,
            startDateMs: Long,
            endDateMs: Long,
            kwh: Double
        ): Boolean {
            val candidateMid = (startDateMs + endDateMs) / 2L
            val kwhTolerance = maxOf(0.5, kwh * 0.10)
            return existingSessions.any { existing ->
                !existing.isSummary &&
                    ElectricityImportParser.normalizeVenue(existing.venue) == ElectricityImportParser.VENUE_PUBLIC &&
                    (existing.carId == null || carId == null || existing.carId == carId) &&
                    abs(((existing.startDateMs + existing.endDateMs) / 2L) - candidateMid) <= 30L * 60_000L &&
                    abs(existing.kwh - kwh) <= kwhTolerance
            }
        }

        private fun resolveCarForSupercharger(
            session: TeslaSuperchargerSession,
            cars: List<Car>
        ): Car? {
            val activeCars = cars.filter { !it.isArchived }
            if (activeCars.isEmpty()) return null

            val vinUpper = session.vin?.trim()?.uppercase(Locale.US)
            if (!vinUpper.isNullOrBlank()) {
                activeCars.firstOrNull { it.vin?.trim()?.uppercase(Locale.US) == vinUpper }?.let { return it }
            }

            val teslaEvs = activeCars.filter { resolveProviderForCar(it) == PROVIDER_TESLA }
            val nonTeslaEvs = activeCars.filter { it.isEv && resolveProviderForCar(it) != PROVIDER_TESLA }

            val makeType = session.vehicleMakeType?.trim()?.uppercase(Locale.US)
            val isExplicitTeslaMake = makeType == "TSLA" || makeType == "TESLA"
            val isExplicitNonTesla = when {
                !makeType.isNullOrBlank() && (
                    makeType.contains("NON_TESLA") ||
                        makeType.contains("NONTESLA") ||
                        makeType.contains("NON-TESLA") ||
                        !isExplicitTeslaMake
                    ) -> true
                !vinUpper.isNullOrBlank() && vinUpper.length == 17 &&
                    !vinUpper.startsWith("5YJ") && !vinUpper.startsWith("7SA") && !vinUpper.startsWith("7G2") &&
                    !vinUpper.startsWith("LRW") && !vinUpper.startsWith("XP7") -> true
                else -> false
            }

            if (isExplicitNonTesla) {
                return nonTeslaEvs.singleOrNull()
            }
            if (isExplicitTeslaMake || !vinUpper.isNullOrBlank()) {
                return teslaEvs.singleOrNull()
            }
            return teslaEvs.singleOrNull()
        }

        fun buildSuperchargerSessionEntities(
            rawSessions: List<TeslaSuperchargerSession>,
            cars: List<Car>,
            existingSessions: List<ChargingSessionEntity> = emptyList(),
            uploadedAtMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): List<ChargingSessionEntity> {
            if (rawSessions.isEmpty()) return emptyList()
            val seenIds = existingSessions.map { it.id }.toMutableSet()
            val seenFingerprints = existingSessions.map { it.fingerprint }.toMutableSet()
            val combinedPublicForOverlap = existingSessions.toMutableList()
            val entities = mutableListOf<ChargingSessionEntity>()

            for (raw in rawSessions) {
                if (raw.energyKwh < 0.1) continue
                val cleanSessionId = raw.sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val entityId = "sc_tesla_$cleanSessionId"
                if (!seenIds.add(entityId)) continue

                val targetCar = resolveCarForSupercharger(raw, cars)
                val fingerprint = ElectricityImportParser.buildFingerprint(
                    startDateMs = raw.startDateMs,
                    endDateMs = raw.endDateMs,
                    kwh = raw.energyKwh,
                    venue = ElectricityImportParser.VENUE_PUBLIC,
                    carId = targetCar?.id,
                    zoneId = zoneId
                )
                if (!seenFingerprints.add(fingerprint)) continue
                if (hasOverlappingPublicSession(combinedPublicForOverlap, targetCar?.id, raw.startDateMs, raw.endDateMs, raw.energyKwh)) {
                    continue
                }

                val siteLabel = raw.siteLocationName?.takeIf { it.isNotBlank() }
                    ?.let { "Supercharger · $it" }
                    ?: "Tesla Supercharger"

                val entity = ChargingSessionEntity(
                    id = entityId,
                    carId = targetCar?.id,
                    startDateMs = raw.startDateMs,
                    endDateMs = raw.endDateMs,
                    kwh = raw.energyKwh,
                    venue = ElectricityImportParser.VENUE_PUBLIC,
                    directCost = raw.totalPaidCost,
                    chargerName = siteLabel,
                    isSummary = false,
                    fingerprint = fingerprint,
                    uploadBatchId = ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID,
                    uploadedAtMs = uploadedAtMs
                )
                entities.add(entity)
                combinedPublicForOverlap.add(entity)
            }
            return entities
        }

        fun formatSuperchargerSyncMessage(result: SuperchargerSyncResult): String = when (result) {
            is SuperchargerSyncResult.Synced -> {
                if (result.insertedCount > 0) {
                    val label = if (result.insertedCount == 1) "session" else "sessions"
                    val costStr = String.format(Locale.US, "$%.2f", result.totalCostAdded)
                    val kwhStr = String.format(Locale.US, "%.1f", result.totalKwhAdded)
                    "Synced ${result.insertedCount} Supercharger $label ($kwhStr kWh · $costStr)"
                } else {
                    "Superchargers up to date"
                }
            }
            SuperchargerSyncResult.NotConfigured -> "Connect Tesla in Settings to sync Superchargers"
            is SuperchargerSyncResult.MissingScope -> result.message
            is SuperchargerSyncResult.Error -> "Supercharger sync failed: ${result.message}"
        }

        fun parseSmartcarV3ErrorCode(rawBody: String): Pair<String?, String?> {
            return try {
                val root = json.parseToJsonElement(rawBody) as? JsonObject ?: return Pair(null, null)
                val firstErr = (root["errors"] as? JsonArray)?.firstOrNull() as? JsonObject
                if (firstErr != null) {
                    return Pair(
                        firstErr["type"].asStringOrNull()?.uppercase(Locale.US),
                        firstErr["code"].asStringOrNull()?.uppercase(Locale.US)
                    )
                }
                val dataObj = root["data"] as? JsonObject
                val attrsObj = dataObj?.get("attributes") as? JsonObject
                val statusObj = (attrsObj?.get("status") as? JsonObject) ?: (root["status"] as? JsonObject)
                val errObj = statusObj?.get("error") as? JsonObject
                if (errObj != null) {
                    return Pair(
                        errObj["type"].asStringOrNull()?.uppercase(Locale.US),
                        errObj["code"].asStringOrNull()?.uppercase(Locale.US)
                    )
                }
                Pair(null, null)
            } catch (_: Exception) {
                Pair(null, null)
            }
        }

        fun isSmartcarNotCapableError(httpCode: Int, rawBody: String): Boolean {
            if (httpCode == 501) return true
            val (type, code) = parseSmartcarV3ErrorCode(rawBody)
            return type == "COMPATIBILITY" ||
                code == "VEHICLE_NOT_CAPABLE" ||
                rawBody.contains("VEHICLE_NOT_CAPABLE", ignoreCase = true)
        }

        fun isSmartcarPermissionError(httpCode: Int, rawBody: String): Boolean {
            val (type, code) = parseSmartcarV3ErrorCode(rawBody)
            if (type == "PERMISSION" || code == "FORBIDDEN" || code == "PERMISSION") return true
            return httpCode == 403 && !isSmartcarNotCapableError(httpCode, rawBody)
        }

        fun parseSmartcarChargeRecords(rawJson: String): List<SmartcarChargeRecord> {
            return try {
                val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return emptyList()
                val dataObj = root["data"] as? JsonObject
                val attrsObj = dataObj?.get("attributes") as? JsonObject
                val bodyObj = (attrsObj?.get("body") as? JsonObject)
                    ?: (dataObj?.get("body") as? JsonObject)
                    ?: (root["body"] as? JsonObject)
                    ?: root
                val valuesArr = (bodyObj["values"] as? JsonArray)
                    ?: (root["values"] as? JsonArray)
                    ?: return emptyList()

                val records = mutableListOf<SmartcarChargeRecord>()
                for ((idx, elem) in valuesArr.withIndex()) {
                    val obj = elem as? JsonObject ?: continue
                    val chargingType = obj["chargingType"].asStringOrNull()?.uppercase(Locale.US)
                    val isPublic = obj["isPublicCharger"].asBooleanOrNull()
                    // Only import DC fast charging or explicitly public sessions (home AC is metered by Wall Connectors)
                    if (chargingType != "DC" && isPublic != true) continue

                    val energyKwh = obj["energyAdded"].asDoubleOrNull()
                        ?.takeIf { it.isFinite() && it >= 0.1 } ?: continue
                    val startMs = parseFlexibleTimestampMs(obj["startTime"]) ?: continue
                    val rawEndMs = parseFlexibleTimestampMs(obj["endTime"])
                    val endMs = if (rawEndMs != null && rawEndMs >= startMs) rawEndMs else startMs

                    val costObj = obj["cost"] as? JsonObject
                    val costTotal = if (costObj != null) {
                        val energyAmt = costObj["energyAmount"].asDoubleOrNull()
                        val otherAmt = costObj["otherAmount"].asDoubleOrNull()
                        if (energyAmt != null || otherAmt != null) {
                            round(((energyAmt ?: 0.0) + (otherAmt ?: 0.0)).coerceAtLeast(0.0) * 100.0) / 100.0
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    val locationObj = obj["location"] as? JsonObject
                    val locationName = locationObj?.get("name").asStringOrNull()
                        ?: locationObj?.get("address").asStringOrNull()

                    val recordId = obj["id"].asStringOrNull()
                        ?: "rec_${startMs / 1000L}_${(energyKwh * 10.0).roundToLong()}_$idx"

                    records.add(
                        SmartcarChargeRecord(
                            id = recordId,
                            startDateMs = startMs,
                            endDateMs = endMs,
                            energyKwh = round(energyKwh * 1000.0) / 1000.0,
                            chargingType = chargingType,
                            isPublicCharger = isPublic,
                            costTotal = costTotal,
                            locationName = locationName
                        )
                    )
                }
                records.sortedBy { it.startDateMs }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun parseSmartcarSignalBoolean(rawJson: String): Boolean? {
            return try {
                val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return null
                val dataObj = root["data"] as? JsonObject
                val attrsObj = dataObj?.get("attributes") as? JsonObject
                val bodyObj = (attrsObj?.get("body") as? JsonObject) ?: (root["body"] as? JsonObject) ?: root
                bodyObj["value"].asBooleanOrNull()
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarSignalDouble(rawJson: String): Double? {
            return try {
                val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return null
                val dataObj = root["data"] as? JsonObject
                val attrsObj = dataObj?.get("attributes") as? JsonObject
                val bodyObj = (attrsObj?.get("body") as? JsonObject) ?: (root["body"] as? JsonObject) ?: root
                bodyObj["value"].asDoubleOrNull()?.takeIf { it.isFinite() }
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarSignalString(rawJson: String): String? {
            return try {
                val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return null
                val dataObj = root["data"] as? JsonObject
                val attrsObj = dataObj?.get("attributes") as? JsonObject
                val bodyObj = (attrsObj?.get("body") as? JsonObject) ?: (root["body"] as? JsonObject) ?: root
                bodyObj["value"].asStringOrNull()
            } catch (_: Exception) {
                null
            }
        }

        fun buildSmartcarDcSessionEntities(
            records: List<SmartcarChargeRecord>,
            targetCar: Car?,
            existingSessions: List<ChargingSessionEntity> = emptyList(),
            uploadedAtMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): List<ChargingSessionEntity> {
            if (records.isEmpty()) return emptyList()
            val seenIds = existingSessions.map { it.id }.toMutableSet()
            val seenFingerprints = existingSessions.map { it.fingerprint }.toMutableSet()
            val combinedPublicForOverlap = existingSessions.toMutableList()
            val entities = mutableListOf<ChargingSessionEntity>()

            for (record in records) {
                if (record.energyKwh < 0.1) continue
                val cleanId = record.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val entityId = "sc_smartcar_$cleanId"
                if (!seenIds.add(entityId)) continue

                val fingerprint = ElectricityImportParser.buildFingerprint(
                    startDateMs = record.startDateMs,
                    endDateMs = record.endDateMs,
                    kwh = record.energyKwh,
                    venue = ElectricityImportParser.VENUE_PUBLIC,
                    carId = targetCar?.id,
                    zoneId = zoneId
                )
                if (!seenFingerprints.add(fingerprint)) continue
                if (hasOverlappingPublicSession(combinedPublicForOverlap, targetCar?.id, record.startDateMs, record.endDateMs, record.energyKwh)) {
                    continue
                }

                val label = record.locationName?.takeIf { it.isNotBlank() }
                    ?: if (record.chargingType == "DC") "DC Fast Charger" else "Public Charger"

                val entity = ChargingSessionEntity(
                    id = entityId,
                    carId = targetCar?.id,
                    startDateMs = record.startDateMs,
                    endDateMs = record.endDateMs,
                    kwh = record.energyKwh,
                    venue = ElectricityImportParser.VENUE_PUBLIC,
                    directCost = record.costTotal,
                    chargerName = label,
                    isSummary = false,
                    fingerprint = fingerprint,
                    uploadBatchId = ElectricityImportParser.AUTO_SMARTCAR_CHARGING_BATCH_ID,
                    uploadedAtMs = uploadedAtMs
                )
                entities.add(entity)
                combinedPublicForOverlap.add(entity)
            }
            return entities
        }

        fun formatSmartcarDcSyncMessage(result: SmartcarDcSyncResult): String = when (result) {
            is SmartcarDcSyncResult.Synced -> {
                if (result.insertedCount > 0) {
                    val label = if (result.insertedCount == 1) "DC session" else "DC sessions"
                    val kwhStr = String.format(Locale.US, "%.1f", result.totalKwhAdded)
                    val costPart = if (result.totalCostAdded > 0.0) {
                        " · ${String.format(Locale.US, "$%.2f", result.totalCostAdded)}"
                    } else {
                        ""
                    }
                    "Synced ${result.insertedCount} Smartcar $label ($kwhStr kWh$costPart)"
                } else {
                    "Smartcar DC charging up to date"
                }
            }
            SmartcarDcSyncResult.NotConfigured -> "Connect Smartcar in Settings to check DC charging"
            is SmartcarDcSyncResult.MissingPermission -> result.message
            is SmartcarDcSyncResult.UnsupportedByVehicle -> result.message
            is SmartcarDcSyncResult.NotCurrentlyFastCharging -> result.message
            is SmartcarDcSyncResult.Error -> "Smartcar DC check failed: ${result.message}"
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

        fun buildTeslaAuthUrl(
            clientId: String,
            includeLocationScope: Boolean = false,
            includeChargingHistoryScope: Boolean = false
        ): String {
            val encodedClient = URLEncoder.encode(clientId.trim(), "UTF-8")
            val encodedRedirect = URLEncoder.encode(TESLA_REDIRECT_URI, "UTF-8")
            val scopes = mutableListOf(
                "openid",
                "offline_access",
                "vehicle_device_data",
                TESLA_SCOPE_ENERGY_DEVICE_DATA
            )
            if (includeLocationScope) {
                scopes.add(TESLA_SCOPE_VEHICLE_LOCATION)
            }
            if (includeChargingHistoryScope) {
                scopes.add(TESLA_SCOPE_VEHICLE_CHARGING_CMDS)
            }
            val scopeParam = scopes.joinToString("%20")
            return "https://auth.tesla.com/oauth2/v3/authorize" +
                "?response_type=code" +
                "&client_id=$encodedClient" +
                "&redirect_uri=$encodedRedirect" +
                "&scope=$scopeParam" +
                "&prompt_missing_scopes=true" +
                "&require_requested_scopes=true" +
                "&state=carlogger_tesla"
        }
    }

    fun buildSmartcarAuthUrl(clientOrAppId: String): String = Companion.buildSmartcarAuthUrl(clientOrAppId)

    fun buildTeslaAuthUrl(
        clientId: String,
        includeLocationScope: Boolean = false,
        includeChargingHistoryScope: Boolean = false
    ): String = Companion.buildTeslaAuthUrl(
        clientId = clientId,
        includeLocationScope = includeLocationScope,
        includeChargingHistoryScope = includeChargingHistoryScope
    )

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
            preferencesManager.setSmartcarDcCapability(PreferencesManager.SMARTCAR_CAPABILITY_UNKNOWN)
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
        if (clientId.isNullOrBlank()) {
            _lastSyncMessage.value = "Tesla Client ID not configured"
            return false
        }

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
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = parseTeslaErrorDetail(rawBody)
                    val msg = detail?.let { "Tesla connect failed (${resp.code}: $it)" }
                        ?: "Tesla connect failed (${resp.code})"
                    _lastSyncMessage.value = msg
                    return false
                }
                val obj = json.parseToJsonElement(rawBody) as? JsonObject
                if (obj == null) {
                    _lastSyncMessage.value = "Tesla connect failed: invalid token response"
                    return false
                }
                val access = obj["access_token"].asStringOrNull()
                if (access == null) {
                    _lastSyncMessage.value = "Tesla connect failed: missing access_token"
                    return false
                }
                val refresh = obj["refresh_token"].asStringOrNull()
                val expires = obj["expires_in"].asLongOrNull() ?: 28800L
                preferencesManager.saveTeslaTokens(access, refresh, expires)
                _lastSyncMessage.value = "Tesla connected"
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tesla code exchange failed", e)
            _lastSyncMessage.value = "Tesla connect failed: ${e.message ?: e.javaClass.simpleName}"
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
        val nowMs = System.currentTimeMillis()
        val summary = formatDailyPollSummary(eligibleCars, results)
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
                PROVIDER_TESLA -> syncTeslaVehicle(car, isManual = isManual, forceWake = forceWake)
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

    internal var wakePollDelayMs: Long = 5_000L

    private suspend fun getValidTeslaAccessToken(forceRefresh: Boolean = false): String? {
        val (access, refresh, expiry) = preferencesManager.getTeslaTokens()
        if (access.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        val effectiveExpiryMs = if (expiry > 0L) expiry else (extractJwtExpiryMs(access) ?: 0L)
        if (!forceRefresh && (effectiveExpiryMs == 0L || now < effectiveExpiryMs - 60_000L)) {
            return access
        }
        val fallbackIfUnexpired = access.takeIf { !forceRefresh && (effectiveExpiryMs == 0L || now < effectiveExpiryMs) }
        val (clientId, clientSecret) = getEffectiveTeslaCredentials()
        if (clientId.isNullOrBlank() || refresh.isNullOrBlank()) {
            return fallbackIfUnexpired
        }

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
                if (!resp.isSuccessful) return fallbackIfUnexpired
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
                    ?: return fallbackIfUnexpired
                val newAccess = obj["access_token"].asStringOrNull() ?: return fallbackIfUnexpired
                val newRefresh = obj["refresh_token"].asStringOrNull() ?: refresh
                val expires = obj["expires_in"].asLongOrNull() ?: 28800L
                preferencesManager.saveTeslaTokens(newAccess, newRefresh, expires)
                newAccess
            }
        } catch (_: Exception) {
            fallbackIfUnexpired
        }
    }

    private suspend fun executeTeslaGetWithRefresh(url: String, initialToken: String): Triple<Int, String, String> {
        var activeToken = initialToken
        fun doGet(bearer: String): Pair<Int, String> {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $bearer")
                .build()
            return client.newCall(req).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        var (code, body) = doGet(activeToken)
        if (code == 401) {
            val refreshed = getValidTeslaAccessToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank() && refreshed != activeToken) {
                activeToken = refreshed
                val retry = doGet(activeToken)
                code = retry.first
                body = retry.second
            }
        }
        return Triple(code, body, activeToken)
    }

    private suspend fun executeTeslaPostWithRefresh(
        url: String,
        jsonPayload: String,
        initialToken: String
    ): Triple<Int, String, String> {
        var activeToken = initialToken
        fun doPost(bearer: String): Pair<Int, String> {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $bearer")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()
            return client.newCall(req).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        var (code, body) = doPost(activeToken)
        if (code == 401) {
            val refreshed = getValidTeslaAccessToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank() && refreshed != activeToken) {
                activeToken = refreshed
                val retry = doPost(activeToken)
                code = retry.first
                body = retry.second
            }
        }
        return Triple(code, body, activeToken)
    }

    private suspend fun wakeTeslaVehicleOnce(
        vin: String,
        initialToken: String,
        maxStatePolls: Int = 6
    ): Pair<Boolean, String> {
        var activeToken = initialToken
        val online = wakeAndPollUntilOnline(
            maxStatePolls = maxStatePolls,
            delayMs = wakePollDelayMs,
            sendWake = {
                val (_, wakeBody, updated) = executeTeslaPostWithRefresh(
                    url = "$TESLA_API_BASE/api/1/vehicles/$vin/wake_up",
                    jsonPayload = "{}",
                    initialToken = activeToken
                )
                activeToken = updated
                parseTeslaWakeState(wakeBody)
            },
            pollState = {
                val (stateCode, stateBody, updated) = executeTeslaGetWithRefresh(
                    url = "$TESLA_API_BASE/api/1/vehicles/$vin",
                    initialToken = activeToken
                )
                activeToken = updated
                if (stateCode in 200..299) parseTeslaWakeState(stateBody) else null
            }
        )
        return Pair(online, activeToken)
    }

    private suspend fun syncTeslaVehicle(
        car: Car,
        isManual: Boolean = false,
        forceWake: Boolean
    ): OdometerSyncResult {
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return OdometerSyncResult.NotConfigured(car.id)
        }
        var token = getValidTeslaAccessToken()
            ?: return OdometerSyncResult.Error(car.id, "Tesla token expired — reconnect or paste a new token in Settings")

        // Step 1: Zero-Wake Cloud State Check
        val (vin, state) = try {
            val (listCode, listBody, updatedToken) = executeTeslaGetWithRefresh("$TESLA_API_BASE/api/1/vehicles", token)
            token = updatedToken
            if (listCode !in 200..299) {
                val detail = parseTeslaErrorDetail(listBody)
                val msg = detail?.let { "Tesla API ($listCode: $it)" } ?: "Tesla API ($listCode)"
                return OdometerSyncResult.Error(car.id, msg)
            }
            parseTeslaVehicleListState(listBody, car.vin)
                ?: return OdometerSyncResult.Error(car.id, "No Tesla found")
        } catch (e: Exception) {
            return OdometerSyncResult.Error(car.id, "Network error")
        }

        // Step 2: If asleep, try zero-wake vehicle_data_cached first on automated syncs;
        // only wake when explicitly requested (or nightly fallback on cache miss), using a single wake_up.
        if (state != "online") {
            if (!isManual) {
                val cachedResult = try {
                    val (cachedCode, cachedBody, updatedToken) = executeTeslaGetWithRefresh(
                        "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data_cached",
                        token
                    )
                    token = updatedToken
                    if (cachedCode in 200..299) {
                        val cachedOdo = parseTeslaOdometerMiles(cachedBody)
                        if (cachedOdo != null) {
                            val finalOdo = maxOf(cachedOdo, car.odometer ?: 0)
                            val now = System.currentTimeMillis()
                            recordOdometerReading(
                                carId = car.id,
                                odometer = finalOdo,
                                provider = PROVIDER_TESLA,
                                syncTime = now
                            )
                            if (chargingSessionDao != null && preferencesManager.getWorkChargingSyncEnabled()) {
                                runCatching {
                                    val snapshot = parseTeslaChargeSnapshot(
                                        jsonBody = cachedBody,
                                        vin = vin,
                                        isCachedResponse = true
                                    )
                                    if (snapshot != null) {
                                        processWorkChargeSnapshot(
                                            snapshot = snapshot,
                                            targetCar = car,
                                            nowMs = now,
                                            recordSyncStatus = false
                                        )
                                    }
                                }
                            }
                            _lastSyncMessage.value = "${car.model}: $finalOdo mi"
                            OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_TESLA)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
                if (cachedResult != null) {
                    return cachedResult
                }
            }

            if (!forceWake) {
                carDao.updateTelemetryStatus(car.id, PROVIDER_TESLA, "Asleep")
                _lastSyncMessage.value = "${car.model} asleep (wake skipped)"
                return OdometerSyncResult.SkippedAsleep(car.id)
            }

            // Single POST /wake_up ($0.02) + GET /vehicles/{vin} state polling until online
            val (_, afterWakeToken) = wakeTeslaVehicleOnce(vin, token)
            token = afterWakeToken
        }

        // Step 3: Fetch vehicle_state.odometer + charge_state (single API call, zero extra cost)
        val maxDataAttempts = if (forceWake) 3 else 1
        for (attempt in 1..maxDataAttempts) {
            try {
                val (dataCode, dataBody, updatedToken) = executeTeslaGetWithRefresh(
                    "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=vehicle_state%3Bcharge_state",
                    token
                )
                token = updatedToken
                val result = if (dataCode !in 200..299) {
                    if (dataCode == 408) {
                        if (attempt < maxDataAttempts) {
                            null // Signal retry after delay
                        } else {
                            carDao.updateTelemetryStatus(car.id, PROVIDER_TESLA, "Asleep")
                            OdometerSyncResult.SkippedAsleep(car.id)
                        }
                    } else {
                        val detail = parseTeslaErrorDetail(dataBody)
                        val msg = detail?.let { "Tesla data ($dataCode: $it)" } ?: "Tesla data ($dataCode)"
                        OdometerSyncResult.Error(car.id, msg)
                    }
                } else {
                    val newOdo = parseTeslaOdometerMiles(dataBody)
                        ?: return OdometerSyncResult.Error(car.id, "Invalid odometer payload")

                    val finalOdo = maxOf(newOdo, car.odometer ?: 0)
                    val now = System.currentTimeMillis()
                    recordOdometerReading(
                        carId = car.id,
                        odometer = finalOdo,
                        provider = PROVIDER_TESLA,
                        syncTime = now
                    )
                    if (chargingSessionDao != null && preferencesManager.getWorkChargingSyncEnabled()) {
                        runCatching {
                            val snapshot = parseTeslaChargeSnapshot(
                                jsonBody = dataBody,
                                vin = vin,
                                isCachedResponse = false
                            )
                            if (snapshot != null) {
                                processWorkChargeSnapshot(
                                    snapshot = snapshot,
                                    targetCar = car,
                                    nowMs = now,
                                    recordSyncStatus = false
                                )
                            }
                        }
                    }
                    _lastSyncMessage.value = "${car.model}: $finalOdo mi"
                    OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_TESLA)
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
            val dao = chargingSessionDao
            if (dao != null &&
                preferencesManager.getSmartcarDcSyncEnabled() &&
                preferencesManager.getSmartcarDcCapability() == PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY
            ) {
                runCatching {
                    probeSmartcarLiveDcSnapshot(
                        vehicleId = vehicleId,
                        userId = userId,
                        initialToken = token,
                        targetCar = car,
                        dao = dao,
                        nowMs = now,
                        recordStatusOnIdle = false
                    )
                }
            }
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
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return@withContext WallConnectorSyncResult.NotConfigured
        }
        val initialToken = getValidTeslaAccessToken()
        if (initialToken.isNullOrBlank()) {
            val expiredMsg = "Tesla access token expired — reconnect or paste a new token in Settings"
            preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), expiredMsg)
            _lastSyncMessage.value = expiredMsg
            return@withContext WallConnectorSyncResult.Error(expiredMsg)
        }
        var token: String = initialToken

        _isSyncing.value = true
        try {
            val (productsCode, productsBody, refreshedAfterProducts) = try {
                executeTeslaGetWithRefresh("$TESLA_API_BASE/api/1/products", token)
            } catch (e: Exception) {
                val res = WallConnectorSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
                val statusMsg = formatWallConnectorSyncMessage(res)
                preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), statusMsg)
                _lastSyncMessage.value = statusMsg
                return@withContext res
            }
            token = refreshedAfterProducts

            if (productsCode == 401) {
                val detail = parseTeslaErrorDetail(productsBody)
                val msg = detail?.let { "Tesla authorization failed (401: $it) — reconnect or update token in Settings" }
                    ?: "Tesla token expired or unauthorized (401) — reconnect or update token in Settings"
                preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), msg)
                _lastSyncMessage.value = msg
                return@withContext WallConnectorSyncResult.Error(msg)
            }
            if (productsCode == 403) {
                val detail = parseTeslaErrorDetail(productsBody)
                if (isTeslaScopeError(403, detail)) {
                    val missingScopeRes = WallConnectorSyncResult.MissingScope(
                        "Tesla token is missing energy_device_data scope — tap Reconnect in Settings"
                    )
                    preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), missingScopeRes.message)
                    _lastSyncMessage.value = missingScopeRes.message
                    return@withContext missingScopeRes
                }
                val errMsg = detail?.let { "Tesla API error (403: $it)" } ?: "Tesla products API (403)"
                val errRes = WallConnectorSyncResult.Error(errMsg)
                val statusMsg = formatWallConnectorSyncMessage(errRes)
                preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), statusMsg)
                _lastSyncMessage.value = statusMsg
                return@withContext errRes
            }
            if (productsCode !in 200..299) {
                val detail = parseTeslaErrorDetail(productsBody)
                val errMsg = detail?.let { "Tesla products API ($productsCode: $it)" }
                    ?: "Tesla products API ($productsCode)"
                val errRes = WallConnectorSyncResult.Error(errMsg)
                val statusMsg = formatWallConnectorSyncMessage(errRes)
                preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), statusMsg)
                _lastSyncMessage.value = statusMsg
                return@withContext errRes
            }

            val discoveredDevices = parseTeslaEnergySites(productsBody).toMutableList()
            val uniqueSiteIds = discoveredDevices.map { it.energySiteId }.distinct()

            if (uniqueSiteIds.isEmpty()) {
                val jwtScopes = extractJwtScopes(token)
                val nowMs = System.currentTimeMillis()
                if (jwtScopes != null && !jwtScopes.contains(TESLA_SCOPE_ENERGY_DEVICE_DATA)) {
                    val missingScopeRes = WallConnectorSyncResult.MissingScope(
                        "Tesla token is missing energy_device_data scope — tap Reconnect in Settings"
                    )
                    preferencesManager.recordChargerSyncResult(nowMs, missingScopeRes.message)
                    _lastSyncMessage.value = missingScopeRes.message
                    return@withContext missingScopeRes
                }
                val noDevicesRes = WallConnectorSyncResult.NoDevices()
                preferencesManager.recordChargerSyncResult(nowMs, noDevicesRes.message)
                _lastSyncMessage.value = noDevicesRes.message
                return@withContext noDevicesRes
            }

            // Enrich device metadata via /site_info when DIN or partNumber wasn't included in /products
            val enrichedDevices = mutableListOf<TeslaWallConnectorDevice>()
            for (siteId in uniqueSiteIds) {
                val siteDevices = discoveredDevices.filter { it.energySiteId == siteId }
                val needsSiteInfo = siteDevices.all { it.din.isNullOrBlank() && it.partNumber.isNullOrBlank() }
                if (needsSiteInfo) {
                    val parsedInfo = try {
                        val (infoCode, infoBody, updatedToken) = executeTeslaGetWithRefresh(
                            "$TESLA_API_BASE/api/1/energy_sites/$siteId/site_info",
                            token
                        )
                        token = updatedToken
                        if (infoCode in 200..299) {
                            parseTeslaSiteInfoWallConnectors(
                                siteInfoJson = infoBody,
                                energySiteId = siteId,
                                fallbackSiteName = siteDevices.firstOrNull()?.siteName,
                                startingIndex = enrichedDevices.size
                            )
                        } else {
                            emptyList()
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

            val existingSessions = dao.getAllSessionsList()
            val hasImportedWallConnectorHistory = existingSessions.any {
                it.uploadBatchId == ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID
            }
            val lastChargerSyncMs = preferencesManager.getLastChargerSyncTime()
            val effectiveLookbackDays = (lookbackDays ?: if (lastChargerSyncMs == 0L || !hasImportedWallConnectorHistory) 30 else 7)
                .coerceIn(1, 30)
            val zoneId = ZoneId.systemDefault()
            val endZdt = ZonedDateTime.now(zoneId).truncatedTo(ChronoUnit.SECONDS)
            val startCandidate = endZdt.minusDays(effectiveLookbackDays.toLong())
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
            val earliestAllowedStart = Instant.ofEpochMilli(
                endZdt.toInstant().toEpochMilli() - 30L * 86_400_000L
            ).atZone(zoneId).truncatedTo(ChronoUnit.SECONDS)
            val startZdt = if (startCandidate.isBefore(earliestAllowedStart)) earliestAllowedStart else startCandidate

            val rfc3339Formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val localDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
            val startFormatted = startZdt.format(rfc3339Formatter)
            val endFormatted = endZdt.format(rfc3339Formatter)
            val encodedStart = URLEncoder.encode(startFormatted, "UTF-8")
            val encodedEnd = URLEncoder.encode(endFormatted, "UTF-8")
            val encodedStartDate = URLEncoder.encode(startZdt.toLocalDate().format(localDateFormatter), "UTF-8")
            val encodedEndDate = URLEncoder.encode(endZdt.toLocalDate().format(localDateFormatter), "UTF-8")
            val encodedTz = URLEncoder.encode(zoneId.id, "UTF-8")

            Log.i(
                TAG,
                "Wall Connector sync: sites=${uniqueSiteIds.size}, devices=${enrichedDevices.size}, " +
                    "lookback=${effectiveLookbackDays}d ($startFormatted -> $endFormatted)"
            )

            val allRawSessions = mutableListOf<TeslaWallConnectorChargeSession>()
            val siteErrors = mutableListOf<String>()
            for (siteId in uniqueSiteIds) {
                val primaryUrl = "$TESLA_API_BASE/api/1/energy_sites/$siteId/telemetry_history" +
                    "?kind=charge&start_date=$encodedStart&end_date=$encodedEnd&time_zone=$encodedTz"

                try {
                    var (code, bodyStr, updatedToken) = executeTeslaGetWithRefresh(primaryUrl, token)
                    token = updatedToken

                    if (code == 400) {
                        val fallbackUrl = "$TESLA_API_BASE/api/1/energy_sites/$siteId/telemetry_history" +
                            "?kind=charge&start_date=$encodedStartDate&end_date=$encodedEndDate&time_zone=$encodedTz"
                        val retry = executeTeslaGetWithRefresh(fallbackUrl, token)
                        code = retry.first
                        bodyStr = retry.second
                        token = retry.third
                    }

                    when {
                        code == 401 -> {
                            val detail = parseTeslaErrorDetail(bodyStr)
                            val msg = detail?.let { "Tesla authorization failed (401: $it) — reconnect or update token in Settings" }
                                ?: "Tesla token expired or unauthorized (401) — reconnect or update token in Settings"
                            preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), msg)
                            _lastSyncMessage.value = msg
                            return@withContext WallConnectorSyncResult.Error(msg)
                        }
                        code == 403 -> {
                            val detail = parseTeslaErrorDetail(bodyStr)
                            if (isTeslaScopeError(403, detail)) {
                                val missingScopeRes = WallConnectorSyncResult.MissingScope(
                                    "Tesla token is missing energy_device_data scope — tap Reconnect in Settings"
                                )
                                preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), missingScopeRes.message)
                                _lastSyncMessage.value = missingScopeRes.message
                                return@withContext missingScopeRes
                            }
                            siteErrors.add(detail?.let { "Site $siteId (403: $it)" } ?: "Site $siteId (403)")
                        }
                        code in 200..299 -> {
                            val siteSessions = parseTeslaWallConnectorChargeHistory(bodyStr, siteId)
                            if (siteSessions.isEmpty()) {
                                val keysSummary = runCatching {
                                    val rootObj = json.parseToJsonElement(bodyStr) as? JsonObject
                                    val respObj = rootObj?.get("response") as? JsonObject
                                    "top=${rootObj?.keys?.joinToString(",") ?: "non-obj"};" +
                                        "resp=${respObj?.keys?.joinToString(",") ?: "non-obj"}"
                                }.getOrElse { "unparseable" }
                                Log.i(TAG, "Site $siteId telemetry_history HTTP $code -> 0 parsed sessions ($keysSummary)")
                            } else {
                                Log.i(TAG, "Site $siteId telemetry_history HTTP $code -> ${siteSessions.size} parsed session(s)")
                            }
                            allRawSessions.addAll(siteSessions)
                        }
                        else -> {
                            val detail = parseTeslaErrorDetail(bodyStr)
                            siteErrors.add(detail?.let { "Site $siteId ($code: $it)" } ?: "Site $siteId ($code)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Wall Connector telemetry_history fetch failed for $siteId: ${e.message}")
                    siteErrors.add("Site $siteId (${e.message ?: e.javaClass.simpleName})")
                }
            }

            if (allRawSessions.isEmpty() && siteErrors.isNotEmpty()) {
                val errRes = WallConnectorSyncResult.Error(siteErrors.first())
                val statusMsg = formatWallConnectorSyncMessage(errRes)
                preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), statusMsg)
                _lastSyncMessage.value = statusMsg
                return@withContext errRes
            }

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

    private suspend fun processWorkChargeSnapshot(
        snapshot: TeslaChargeSnapshot,
        targetCar: Car?,
        nowMs: Long,
        recordSyncStatus: Boolean
    ): WorkChargingSyncResult {
        val dao = chargingSessionDao ?: return WorkChargingSyncResult.Error("Charging database unavailable")
        val existingSessions = dao.getAllSessionsList()
        val (lat, lng, radius) = preferencesManager.getWorkLocation()
        val workLocation = if (lat != null && lng != null) {
            WorkLocationConfig(latitude = lat, longitude = lng, radiusMeters = radius)
        } else {
            null
        }
        val (watermarkEnergy, watermarkDate, watermarkVenue) = preferencesManager.getTeslaChargeWatermark()
        val zoneId = ZoneId.systemDefault()
        val todayIso = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toString()

        val decision = evaluateWorkChargeCandidate(
            snapshot = snapshot,
            targetCarId = targetCar?.id,
            existingSessions = existingSessions,
            workLocation = workLocation,
            lastWatermarkEnergy = watermarkEnergy,
            lastWatermarkDateIso = watermarkDate,
            lastWatermarkVenue = watermarkVenue,
            nowMs = nowMs,
            zoneId = zoneId
        )

        val result = when (decision) {
            is WorkChargeDecision.CaptureNew -> {
                val eventMs = snapshot.timestampMs?.takeIf { abs(nowMs - it) <= 24L * 3_600_000L } ?: nowMs
                val fp = ElectricityImportParser.buildFingerprint(
                    startDateMs = eventMs,
                    endDateMs = eventMs,
                    kwh = decision.energyKwh,
                    venue = ElectricityImportParser.VENUE_WORK,
                    carId = targetCar?.id,
                    zoneId = zoneId
                )
                val chargerLabel = if (decision.verifiedByGps) "Work J1772 (GPS verified)" else "Work J1772 (Tesla Auto)"
                dao.insertSession(
                    ChargingSessionEntity(
                        id = decision.sessionId,
                        carId = targetCar?.id,
                        startDateMs = eventMs,
                        endDateMs = eventMs,
                        kwh = decision.energyKwh,
                        venue = ElectricityImportParser.VENUE_WORK,
                        directCost = 0.0,
                        chargerName = chargerLabel,
                        isSummary = false,
                        fingerprint = fp,
                        uploadBatchId = ElectricityImportParser.AUTO_WORK_CHARGING_BATCH_ID,
                        uploadedAtMs = nowMs
                    )
                )
                preferencesManager.setTeslaChargeWatermark(
                    energyAddedKwh = decision.energyKwh,
                    dateIso = todayIso,
                    venue = ElectricityImportParser.VENUE_WORK
                )
                WorkChargingSyncResult.Captured(
                    kwh = decision.energyKwh,
                    updatedExisting = false,
                    verifiedByGps = decision.verifiedByGps
                )
            }
            is WorkChargeDecision.UpdateExisting -> {
                val updatedFp = ElectricityImportParser.buildFingerprint(
                    startDateMs = decision.existingSession.startDateMs,
                    endDateMs = nowMs,
                    kwh = decision.updatedEnergyKwh,
                    venue = ElectricityImportParser.VENUE_WORK,
                    carId = decision.existingSession.carId,
                    zoneId = zoneId
                )
                val chargerLabel = if (decision.verifiedByGps) {
                    "Work J1772 (GPS verified)"
                } else {
                    decision.existingSession.chargerName ?: "Work J1772 (Tesla Auto)"
                }
                dao.insertSession(
                    decision.existingSession.copy(
                        endDateMs = maxOf(decision.existingSession.endDateMs, nowMs),
                        kwh = decision.updatedEnergyKwh,
                        directCost = 0.0,
                        chargerName = chargerLabel,
                        fingerprint = updatedFp,
                        uploadedAtMs = nowMs
                    )
                )
                preferencesManager.setTeslaChargeWatermark(
                    energyAddedKwh = decision.updatedEnergyKwh,
                    dateIso = todayIso,
                    venue = ElectricityImportParser.VENUE_WORK
                )
                WorkChargingSyncResult.Captured(
                    kwh = decision.updatedEnergyKwh,
                    updatedExisting = true,
                    verifiedByGps = decision.verifiedByGps
                )
            }
            is WorkChargeDecision.AlreadyLogged -> {
                preferencesManager.setTeslaChargeWatermark(
                    energyAddedKwh = decision.energyKwh,
                    dateIso = todayIso,
                    venue = ElectricityImportParser.VENUE_WORK
                )
                WorkChargingSyncResult.UpToDate("Work charging up to date (${String.format(Locale.US, "%.1f", decision.energyKwh)} kWh logged)")
            }
            is WorkChargeDecision.Skip -> {
                if (decision.watermarkVenue != null && snapshot.chargeEnergyAddedKwh >= 0.5) {
                    preferencesManager.setTeslaChargeWatermark(
                        energyAddedKwh = snapshot.chargeEnergyAddedKwh,
                        dateIso = todayIso,
                        venue = decision.watermarkVenue
                    )
                }
                WorkChargingSyncResult.UpToDate(decision.reason)
            }
        }

        if (recordSyncStatus) {
            val statusMsg = formatWorkChargingSyncMessage(result)
            preferencesManager.recordWorkChargingSyncResult(nowMs, statusMsg)
            _lastSyncMessage.value = statusMsg
        }
        return result
    }

    suspend fun syncTeslaWorkCharging(
        cars: List<Car>,
        forceWake: Boolean = false,
        syncHomeChargersFirst: Boolean = true
    ): WorkChargingSyncResult = withContext(Dispatchers.IO) {
        if (chargingSessionDao == null) {
            return@withContext WorkChargingSyncResult.Error("Charging database unavailable")
        }
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return@withContext WorkChargingSyncResult.NotConfigured
        }
        val initialToken = getValidTeslaAccessToken()
        if (initialToken.isNullOrBlank()) {
            val expiredMsg = "Tesla access token expired — reconnect in Settings"
            preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), expiredMsg)
            _lastSyncMessage.value = expiredMsg
            return@withContext WorkChargingSyncResult.Error(expiredMsg)
        }
        var token: String = initialToken

        val targetCar = cars.firstOrNull { !it.isArchived && resolveProviderForCar(it) == PROVIDER_TESLA }
        _isSyncing.value = true
        try {
            val (vin, state) = try {
                val (listCode, listBody, updatedToken) = executeTeslaGetWithRefresh("$TESLA_API_BASE/api/1/vehicles", token)
                token = updatedToken
                if (listCode !in 200..299) {
                    val detail = parseTeslaErrorDetail(listBody)
                    val err = WorkChargingSyncResult.Error(detail?.let { "Tesla API ($listCode: $it)" } ?: "Tesla API ($listCode)")
                    val msg = formatWorkChargingSyncMessage(err)
                    preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), msg)
                    _lastSyncMessage.value = msg
                    return@withContext err
                }
                parseTeslaVehicleListState(listBody, targetCar?.vin)
                    ?: run {
                        val err = WorkChargingSyncResult.Error("No Tesla vehicle found")
                        val msg = formatWorkChargingSyncMessage(err)
                        preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), msg)
                        _lastSyncMessage.value = msg
                        return@withContext err
                    }
            } catch (e: Exception) {
                val err = WorkChargingSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
                val msg = formatWorkChargingSyncMessage(err)
                preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), msg)
                _lastSyncMessage.value = msg
                return@withContext err
            }

            val (workLat, workLng, workRadius) = preferencesManager.getWorkLocation()
            val canRequestLocation = workLat != null && workLng != null && hasTeslaLocationScope(token)
            val baseEndpoints = "vehicle_state%3Bcharge_state"
            val endpointsWithLocation = if (canRequestLocation) "$baseEndpoints%3Blocation_data" else baseEndpoints

            var snapshot: TeslaChargeSnapshot? = null
            if (state == "online") {
                var (dataCode, dataBody, updatedToken) = executeTeslaGetWithRefresh(
                    "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=$endpointsWithLocation",
                    token
                )
                token = updatedToken
                // Tesla returns 403 for the entire request if location_data is requested without vehicle_location scope
                if (dataCode == 403 && canRequestLocation) {
                    val retry = executeTeslaGetWithRefresh(
                        "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=$baseEndpoints",
                        token
                    )
                    dataCode = retry.first
                    dataBody = retry.second
                    token = retry.third
                }
                if (dataCode !in 200..299) {
                    val res = if (dataCode == 408) {
                        WorkChargingSyncResult.SkippedAsleep()
                    } else {
                        val detail = parseTeslaErrorDetail(dataBody)
                        WorkChargingSyncResult.Error(detail?.let { "Tesla data ($dataCode: $it)" } ?: "Tesla data ($dataCode)")
                    }
                    val msg = formatWorkChargingSyncMessage(res)
                    preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), msg)
                    _lastSyncMessage.value = msg
                    return@withContext res
                }
                snapshot = parseTeslaChargeSnapshot(dataBody, vin, isCachedResponse = false)
            } else {
                // Zero-wake first: try Tesla's cached vehicle data endpoint while asleep/offline
                val (cachedCode, cachedBody, updatedToken) = executeTeslaGetWithRefresh(
                    "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data_cached",
                    token
                )
                token = updatedToken
                if (cachedCode in 200..299) {
                    snapshot = parseTeslaChargeSnapshot(cachedBody, vin, isCachedResponse = true)
                } else if (!forceWake) {
                    val asleepRes = WorkChargingSyncResult.SkippedAsleep()
                    val msg = formatWorkChargingSyncMessage(asleepRes)
                    preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), msg)
                    _lastSyncMessage.value = msg
                    return@withContext asleepRes
                } else {
                    val (_, afterWakeToken) = wakeTeslaVehicleOnce(vin, token)
                    token = afterWakeToken
                    var (dataCode, dataBody, updatedDataToken) = executeTeslaGetWithRefresh(
                        "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=$endpointsWithLocation",
                        token
                    )
                    token = updatedDataToken
                    if (dataCode == 403 && canRequestLocation) {
                        val retry = executeTeslaGetWithRefresh(
                            "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=$baseEndpoints",
                            token
                        )
                        dataCode = retry.first
                        dataBody = retry.second
                        token = retry.third
                    }
                    if (dataCode !in 200..299) {
                        val res = if (dataCode == 408) {
                            WorkChargingSyncResult.SkippedAsleep()
                        } else {
                            val detail = parseTeslaErrorDetail(dataBody)
                            WorkChargingSyncResult.Error(detail?.let { "Tesla data ($dataCode: $it)" } ?: "Tesla data ($dataCode)")
                        }
                        val msg = formatWorkChargingSyncMessage(res)
                        preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), msg)
                        _lastSyncMessage.value = msg
                        return@withContext res
                    }
                    snapshot = parseTeslaChargeSnapshot(dataBody, vin, isCachedResponse = false)
                }
            }

            if (snapshot == null) {
                val res = WorkChargingSyncResult.UpToDate("No charge state data returned")
                val msg = formatWorkChargingSyncMessage(res)
                preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), msg)
                _lastSyncMessage.value = msg
                return@withContext res
            }

            val nowMs = System.currentTimeMillis()
            // Opportunistically update odometer if returned in vehicle_state
            if (targetCar != null && snapshot.odometerMiles != null && snapshot.odometerMiles > 0) {
                val finalOdo = maxOf(snapshot.odometerMiles, targetCar.odometer ?: 0)
                runCatching {
                    recordOdometerReading(
                        carId = targetCar.id,
                        odometer = finalOdo,
                        provider = PROVIDER_TESLA,
                        syncTime = nowMs
                    )
                }
            }

            // Lazily refresh Home Wall Connectors (free Energy API) only when there's an AC charge
            // candidate that is not already GPS-confirmed inside the Work radius
            val isGpsConfirmedAtWork = workLat != null && workLng != null &&
                snapshot.latitude != null && snapshot.longitude != null &&
                snapshot.chargingState?.trim()?.lowercase(Locale.US) in listOf("charging", "complete", "stopped") &&
                haversineMeters(snapshot.latitude, snapshot.longitude, workLat, workLng) <= workRadius
            if (syncHomeChargersFirst &&
                !snapshot.fastChargerPresent &&
                snapshot.chargeEnergyAddedKwh >= 0.5 &&
                !isGpsConfirmedAtWork
            ) {
                runCatching { syncWallConnectorCharging(cars) }
            }

            processWorkChargeSnapshot(
                snapshot = snapshot,
                targetCar = targetCar,
                nowMs = nowMs,
                recordSyncStatus = true
            )
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun captureCurrentTeslaWorkLocation(
        cars: List<Car>,
        forceWake: Boolean = false
    ): WorkLocationCaptureResult = withContext(Dispatchers.IO) {
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return@withContext WorkLocationCaptureResult.NotConfigured
        }
        var token = getValidTeslaAccessToken()
            ?: return@withContext WorkLocationCaptureResult.Error("Tesla token expired — reconnect in Settings")

        val jwtScopes = extractJwtScopes(token)
        if (jwtScopes != null && !jwtScopes.contains(TESLA_SCOPE_VEHICLE_LOCATION)) {
            return@withContext WorkLocationCaptureResult.MissingScope(
                "Tesla token is missing vehicle_location scope — enable Vehicle Location in Tesla Developer Portal and reconnect"
            )
        }

        val targetCar = cars.firstOrNull { !it.isArchived && resolveProviderForCar(it) == PROVIDER_TESLA }
        _isSyncing.value = true
        try {
            val (vin, state) = try {
                val (listCode, listBody, updatedToken) = executeTeslaGetWithRefresh("$TESLA_API_BASE/api/1/vehicles", token)
                token = updatedToken
                if (listCode !in 200..299) {
                    val detail = parseTeslaErrorDetail(listBody)
                    return@withContext WorkLocationCaptureResult.Error(detail ?: "Tesla API ($listCode)")
                }
                parseTeslaVehicleListState(listBody, targetCar?.vin)
                    ?: return@withContext WorkLocationCaptureResult.Error("No Tesla vehicle found")
            } catch (e: Exception) {
                return@withContext WorkLocationCaptureResult.Error("Network error contacting Tesla")
            }

            if (state != "online") {
                if (!forceWake) {
                    return@withContext WorkLocationCaptureResult.SkippedAsleep()
                }
                val (_, afterWakeToken) = wakeTeslaVehicleOnce(vin, token)
                token = afterWakeToken
            }

            val (locCode, locBody, _) = executeTeslaGetWithRefresh(
                "$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=location_data",
                token
            )
            if (locCode == 403) {
                return@withContext WorkLocationCaptureResult.MissingScope(
                    "Tesla token is missing vehicle_location scope — enable Vehicle Location in Tesla Developer Portal and reconnect"
                )
            }
            if (locCode == 408) {
                return@withContext WorkLocationCaptureResult.SkippedAsleep()
            }
            if (locCode !in 200..299) {
                val detail = parseTeslaErrorDetail(locBody)
                return@withContext WorkLocationCaptureResult.Error(detail ?: "Tesla location API ($locCode)")
            }

            val root = runCatching { json.parseToJsonElement(locBody) as? JsonObject }.getOrNull()
            val response = (root?.get("response") as? JsonObject) ?: root
            val driveState = response?.get("drive_state") as? JsonObject
            val locationData = response?.get("location_data") as? JsonObject
            val lat = driveState?.get("latitude").asDoubleOrNull()
                ?: locationData?.get("latitude").asDoubleOrNull()
            val lng = driveState?.get("longitude").asDoubleOrNull()
                ?: locationData?.get("longitude").asDoubleOrNull()

            if (lat == null || lng == null) {
                return@withContext WorkLocationCaptureResult.Error("No GPS coordinates returned by Tesla")
            }

            preferencesManager.setWorkLocation(lat, lng)
            WorkLocationCaptureResult.Saved(lat, lng)
        } catch (e: Exception) {
            WorkLocationCaptureResult.Error("Failed to read Tesla location: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncTeslaSuperchargerCharging(
        cars: List<Car>,
        lookbackDays: Int? = null
    ): SuperchargerSyncResult = withContext(Dispatchers.IO) {
        val dao = chargingSessionDao ?: return@withContext SuperchargerSyncResult.Error("Charging database unavailable")
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return@withContext SuperchargerSyncResult.NotConfigured
        }
        val initialToken = getValidTeslaAccessToken()
        if (initialToken.isNullOrBlank()) {
            val expiredMsg = "Tesla access token expired — reconnect in Settings"
            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), expiredMsg)
            _lastSyncMessage.value = expiredMsg
            return@withContext SuperchargerSyncResult.Error(expiredMsg)
        }
        var token: String = initialToken

        val jwtScopes = extractJwtScopes(token)
        if (jwtScopes != null && !jwtScopes.contains(TESLA_SCOPE_VEHICLE_CHARGING_CMDS)) {
            val missing = SuperchargerSyncResult.MissingScope(
                "Requires Vehicle Charging Management (vehicle_charging_cmds) scope — enable in Tesla Developer Portal & reconnect"
            )
            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), missing.message)
            return@withContext missing
        }

        _isSyncing.value = true
        try {
            val existingSessions = dao.getAllSessionsList()
            val hasSuperchargerHistory = existingSessions.any {
                it.uploadBatchId == ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID
            }
            val lastSyncMs = preferencesManager.getLastSuperchargerSyncTime()
            val effectiveLookbackDays = (lookbackDays ?: if (lastSyncMs == 0L || !hasSuperchargerHistory) 365 else 30)
                .coerceIn(1, 730)

            val zoneId = ZoneId.systemDefault()
            val endZdt = ZonedDateTime.now(zoneId).truncatedTo(ChronoUnit.SECONDS)
            val startZdt = endZdt.minusDays(effectiveLookbackDays.toLong())
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
            val rfc3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val encodedStart = URLEncoder.encode(startZdt.format(rfc3339), "UTF-8")
            val encodedEnd = URLEncoder.encode(endZdt.format(rfc3339), "UTF-8")

            val pageSize = 50
            val maxPages = 5
            val allRaw = mutableListOf<TeslaSuperchargerSession>()

            for (pageNo in 1..maxPages) {
                val url = "$TESLA_API_BASE/api/1/dx/charging/history" +
                    "?startTime=$encodedStart&endTime=$encodedEnd&pageNo=$pageNo&pageSize=$pageSize"
                val (code, bodyStr, updatedToken) = try {
                    executeTeslaGetWithRefresh(url, token)
                } catch (e: Exception) {
                    val err = SuperchargerSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
                    val msg = formatSuperchargerSyncMessage(err)
                    preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                    _lastSyncMessage.value = msg
                    return@withContext err
                }
                token = updatedToken

                when {
                    code == 401 -> {
                        val detail = parseTeslaErrorDetail(bodyStr)
                        val msg = detail?.let { "Tesla authorization failed (401: $it) — reconnect in Settings" }
                            ?: "Tesla token expired (401) — reconnect in Settings"
                        preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                        _lastSyncMessage.value = msg
                        return@withContext SuperchargerSyncResult.Error(msg)
                    }
                    code == 403 -> {
                        val detail = parseTeslaErrorDetail(bodyStr)
                        if (isTeslaScopeError(403, detail)) {
                            val missing = SuperchargerSyncResult.MissingScope(
                                "Requires Vehicle Charging Management (vehicle_charging_cmds) scope — enable in Tesla Developer Portal & reconnect"
                            )
                            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), missing.message)
                            _lastSyncMessage.value = missing.message
                            return@withContext missing
                        }
                        val err = SuperchargerSyncResult.Error(detail?.let { "Tesla charging history (403: $it)" } ?: "Tesla charging history (403)")
                        val msg = formatSuperchargerSyncMessage(err)
                        preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                        _lastSyncMessage.value = msg
                        return@withContext err
                    }
                    code !in 200..299 -> {
                        val detail = parseTeslaErrorDetail(bodyStr)
                        val err = SuperchargerSyncResult.Error(detail?.let { "Tesla charging history ($code: $it)" } ?: "Tesla charging history ($code)")
                        val msg = formatSuperchargerSyncMessage(err)
                        preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                        _lastSyncMessage.value = msg
                        return@withContext err
                    }
                    else -> {
                        val (pageSessions, hasMore) = parseTeslaSuperchargerHistory(bodyStr)
                        allRaw.addAll(pageSessions)
                        if (!hasMore && pageSessions.size < pageSize) {
                            break
                        }
                    }
                }
            }

            val nowMs = System.currentTimeMillis()
            val newEntities = buildSuperchargerSessionEntities(
                rawSessions = allRaw,
                cars = cars,
                existingSessions = existingSessions,
                uploadedAtMs = nowMs,
                zoneId = zoneId
            )
            if (newEntities.isNotEmpty()) {
                dao.insertSessions(newEntities)
            }

            val result = SuperchargerSyncResult.Synced(
                insertedCount = newEntities.size,
                totalKwhAdded = newEntities.sumOf { it.kwh },
                totalCostAdded = newEntities.sumOf { it.directCost ?: 0.0 },
                totalSessionsScanned = allRaw.size
            )
            val statusMsg = formatSuperchargerSyncMessage(result)
            preferencesManager.recordSuperchargerSyncResult(nowMs, statusMsg)
            _lastSyncMessage.value = statusMsg
            result
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun executeSmartcarV3SignalGet(
        vehicleId: String,
        signalCode: String,
        userId: String?,
        initialToken: String
    ): Triple<Int, String, String> {
        var activeToken = initialToken
        fun doGet(bearer: String): Pair<Int, String> {
            val reqBuilder = Request.Builder()
                .url("$SMARTCAR_V3_API_BASE/vehicles/$vehicleId/signals/$signalCode")
                .addHeader("Authorization", "Bearer $bearer")
            if (!userId.isNullOrBlank()) {
                reqBuilder.addHeader("sc-user-id", userId)
            }
            return client.newCall(reqBuilder.build()).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        var (code, body) = doGet(activeToken)
        if (code == 401) {
            val refreshed = getValidSmartcarAccessToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank() && refreshed != activeToken) {
                activeToken = refreshed
                val retry = doGet(activeToken)
                code = retry.first
                body = retry.second
            }
        }
        return Triple(code, body, activeToken)
    }

    private suspend fun probeSmartcarLiveDcSnapshot(
        vehicleId: String,
        userId: String?,
        initialToken: String,
        targetCar: Car?,
        dao: ChargingSessionDao,
        nowMs: Long,
        recordStatusOnIdle: Boolean
    ): SmartcarDcSyncResult {
        var token = initialToken
        val (fastCode, fastBody, tokenAfterFast) = executeSmartcarV3SignalGet(
            vehicleId = vehicleId,
            signalCode = "charge-isfastchargerpresent",
            userId = userId,
            initialToken = token
        )
        token = tokenAfterFast

        val (energyCode, energyBody, tokenAfterEnergy) = executeSmartcarV3SignalGet(
            vehicleId = vehicleId,
            signalCode = "charge-energyadded",
            userId = userId,
            initialToken = token
        )
        token = tokenAfterEnergy

        if (isSmartcarPermissionError(fastCode, fastBody) || isSmartcarPermissionError(energyCode, energyBody)) {
            val msg = "Missing Smartcar charge permissions — enable Charge signals in Smartcar Dashboard → Vehicle Access & reconnect"
            preferencesManager.recordSmartcarDcSyncResult(
                timestampMs = nowMs,
                status = msg,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION
            )
            return SmartcarDcSyncResult.MissingPermission(msg)
        }

        val fastNotCapable = fastCode !in 200..299 && isSmartcarNotCapableError(fastCode, fastBody)
        val energyNotCapable = energyCode !in 200..299 && isSmartcarNotCapableError(energyCode, energyBody)

        if (energyNotCapable || (fastNotCapable && energyCode !in 200..299)) {
            val msg = "Not supported by GM/OnStar — Superchargers sync via Tesla; use Import file for EA/EVgo"
            preferencesManager.recordSmartcarDcSyncResult(
                timestampMs = nowMs,
                status = msg,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED
            )
            return SmartcarDcSyncResult.UnsupportedByVehicle(msg)
        }

        var isFastCharger = if (fastCode in 200..299) parseSmartcarSignalBoolean(fastBody) else null
        val energyAddedKwh = if (energyCode in 200..299) parseSmartcarSignalDouble(energyBody) else null

        var fastChargerType: String? = null
        if (isFastCharger != true) {
            val (wattCode, wattBody, tokenAfterWatt) = executeSmartcarV3SignalGet(
                vehicleId = vehicleId,
                signalCode = "charge-wattage",
                userId = userId,
                initialToken = token
            )
            token = tokenAfterWatt
            val watts = if (wattCode in 200..299) parseSmartcarSignalDouble(wattBody) else null
            if (watts != null && watts >= 20_000.0) {
                isFastCharger = true
            }
        }

        if (isFastCharger == true && energyAddedKwh != null && energyAddedKwh >= 0.5) {
            val (typeCode, typeBody, _) = executeSmartcarV3SignalGet(
                vehicleId = vehicleId,
                signalCode = "charge-fastchargertype",
                userId = userId,
                initialToken = token
            )
            if (typeCode in 200..299) {
                fastChargerType = parseSmartcarSignalString(typeBody)
            }

            val zoneId = ZoneId.systemDefault()
            val todayIso = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toString()
            val roundedKwh = round(energyAddedKwh * 100.0) / 100.0
            val cleanCarKey = (targetCar?.id ?: vehicleId).replace(Regex("[^A-Za-z0-9_-]"), "_")
            val sessionId = "sc_smartcar_live_${cleanCarKey}_${todayIso}"
            val fp = ElectricityImportParser.buildFingerprint(
                startDateMs = nowMs,
                endDateMs = nowMs,
                kwh = roundedKwh,
                venue = ElectricityImportParser.VENUE_PUBLIC,
                carId = targetCar?.id,
                zoneId = zoneId
            )
            val label = fastChargerType?.takeIf { it.isNotBlank() }
                ?.let { "DC Fast Charger ($it)" }
                ?: "DC Fast Charger (Smartcar)"

            val existing = dao.getAllSessionsList().firstOrNull { it.id == sessionId }
            val entity = if (existing != null) {
                existing.copy(
                    endDateMs = maxOf(existing.endDateMs, nowMs),
                    kwh = maxOf(existing.kwh, roundedKwh),
                    chargerName = label,
                    fingerprint = fp,
                    uploadedAtMs = nowMs
                )
            } else {
                ChargingSessionEntity(
                    id = sessionId,
                    carId = targetCar?.id,
                    startDateMs = nowMs,
                    endDateMs = nowMs,
                    kwh = roundedKwh,
                    venue = ElectricityImportParser.VENUE_PUBLIC,
                    directCost = null,
                    chargerName = label,
                    isSummary = false,
                    fingerprint = fp,
                    uploadBatchId = ElectricityImportParser.AUTO_SMARTCAR_CHARGING_BATCH_ID,
                    uploadedAtMs = nowMs
                )
            }
            dao.insertSession(entity)
            val result = SmartcarDcSyncResult.Synced(
                insertedCount = 1,
                totalKwhAdded = roundedKwh,
                totalCostAdded = 0.0,
                source = "live"
            )
            preferencesManager.recordSmartcarDcSyncResult(
                timestampMs = nowMs,
                status = formatSmartcarDcSyncMessage(result),
                capability = PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY
            )
            return result
        }

        val idleMsg = "Live DC detection ready — not currently fast charging"
        if (recordStatusOnIdle) {
            preferencesManager.recordSmartcarDcSyncResult(
                timestampMs = nowMs,
                status = idleMsg,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY
            )
        }
        return SmartcarDcSyncResult.NotCurrentlyFastCharging(idleMsg)
    }

    suspend fun syncSmartcarDcCharging(
        cars: List<Car>,
        forceCapabilityRecheck: Boolean = false,
        allowLiveProbe: Boolean = false
    ): SmartcarDcSyncResult = withContext(Dispatchers.IO) {
        val dao = chargingSessionDao ?: return@withContext SmartcarDcSyncResult.Error("Charging database unavailable")
        val (clientId, _) = getEffectiveSmartcarCredentials()
        val hasLinkedAccount = !preferencesManager.getSmartcarUserId().isNullOrBlank() ||
            !preferencesManager.getSmartcarVehicleId().isNullOrBlank()
        if (!hasLinkedAccount && clientId.isNullOrBlank()) {
            return@withContext SmartcarDcSyncResult.NotConfigured
        }

        val cachedCapability = preferencesManager.getSmartcarDcCapability()
        if (!forceCapabilityRecheck && !allowLiveProbe &&
            cachedCapability == PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED
        ) {
            return@withContext SmartcarDcSyncResult.UnsupportedByVehicle(
                "Not supported by GM/OnStar — Superchargers sync via Tesla; use Import file for EA/EVgo"
            )
        }

        var token = getValidSmartcarAccessToken() ?: return@withContext SmartcarDcSyncResult.NotConfigured
        val isV3 = clientId?.startsWith("client_") == true ||
            !preferencesManager.getSmartcarUserId().isNullOrBlank()
        if (!isV3) {
            return@withContext SmartcarDcSyncResult.UnsupportedByVehicle(
                "Smartcar v3 connection required for DC charging signals"
            )
        }

        val targetCar = cars.firstOrNull { !it.isArchived && resolveProviderForCar(it) == PROVIDER_SMARTCAR }
        _isSyncing.value = true
        try {
            var vehicleId = preferencesManager.getSmartcarVehicleId()
            var userId = preferencesManager.getSmartcarUserId()
            if (vehicleId.isNullOrBlank()) {
                fun fetchConns(bearer: String): Pair<Int, String> {
                    val req = Request.Builder()
                        .url("$SMARTCAR_V3_API_BASE/connections")
                        .addHeader("Authorization", "Bearer $bearer")
                        .build()
                    return client.newCall(req).execute().use { Pair(it.code, it.body?.string().orEmpty()) }
                }
                var (cCode, cBody) = fetchConns(token)
                if (cCode == 401) {
                    val refreshed = getValidSmartcarAccessToken(forceRefresh = true)
                    if (!refreshed.isNullOrBlank() && refreshed != token) {
                        token = refreshed
                        val retry = fetchConns(token)
                        cCode = retry.first
                        cBody = retry.second
                    }
                }
                if (cCode !in 200..299) {
                    val detail = parseSmartcarErrorDetail(cBody) ?: "Smartcar connections ($cCode)"
                    return@withContext SmartcarDcSyncResult.Error(detail)
                }
                val parsed = parseSmartcarV3Connection(cBody, userId)
                    ?: return@withContext SmartcarDcSyncResult.NotConfigured
                vehicleId = parsed.first
                userId = parsed.second
                preferencesManager.setSmartcarVehicleId(vehicleId, userId)
            }

            val nowMs = System.currentTimeMillis()
            val (recCode, recBody, updatedToken) = executeSmartcarV3SignalGet(
                vehicleId = vehicleId,
                signalCode = "charge-chargerecords",
                userId = userId,
                initialToken = token
            )
            token = updatedToken

            when {
                recCode in 200..299 -> {
                    val records = parseSmartcarChargeRecords(recBody)
                    val existingSessions = dao.getAllSessionsList()
                    val newEntities = buildSmartcarDcSessionEntities(
                        records = records,
                        targetCar = targetCar,
                        existingSessions = existingSessions,
                        uploadedAtMs = nowMs
                    )
                    if (newEntities.isNotEmpty()) {
                        dao.insertSessions(newEntities)
                    }
                    val result = SmartcarDcSyncResult.Synced(
                        insertedCount = newEntities.size,
                        totalKwhAdded = newEntities.sumOf { it.kwh },
                        totalCostAdded = newEntities.sumOf { it.directCost ?: 0.0 },
                        source = "charge_records"
                    )
                    val msg = formatSmartcarDcSyncMessage(result)
                    preferencesManager.recordSmartcarDcSyncResult(
                        timestampMs = nowMs,
                        status = msg,
                        capability = PreferencesManager.SMARTCAR_CAPABILITY_CHARGE_RECORDS
                    )
                    _lastSyncMessage.value = msg
                    result
                }
                isSmartcarPermissionError(recCode, recBody) -> {
                    val msg = "Missing read_charge_records — enable Charge Records in Smartcar Dashboard → Vehicle Access & reconnect"
                    preferencesManager.recordSmartcarDcSyncResult(
                        timestampMs = nowMs,
                        status = msg,
                        capability = PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION
                    )
                    _lastSyncMessage.value = msg
                    SmartcarDcSyncResult.MissingPermission(msg)
                }
                isSmartcarNotCapableError(recCode, recBody) -> {
                    if (allowLiveProbe) {
                        val liveResult = probeSmartcarLiveDcSnapshot(
                            vehicleId = vehicleId,
                            userId = userId,
                            initialToken = token,
                            targetCar = targetCar,
                            dao = dao,
                            nowMs = nowMs,
                            recordStatusOnIdle = true
                        )
                        _lastSyncMessage.value = formatSmartcarDcSyncMessage(liveResult)
                        liveResult
                    } else {
                        val msg = "Not supported by GM/OnStar — Superchargers sync via Tesla; use Import file for EA/EVgo"
                        preferencesManager.recordSmartcarDcSyncResult(
                            timestampMs = nowMs,
                            status = msg,
                            capability = PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED
                        )
                        SmartcarDcSyncResult.UnsupportedByVehicle(msg)
                    }
                }
                else -> {
                    val detail = parseSmartcarErrorDetail(recBody) ?: "Smartcar ($recCode)"
                    val err = SmartcarDcSyncResult.Error(detail)
                    val msg = formatSmartcarDcSyncMessage(err)
                    preferencesManager.recordSmartcarDcSyncResult(nowMs, msg)
                    _lastSyncMessage.value = msg
                    err
                }
            }
        } catch (e: Exception) {
            val err = SmartcarDcSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
            val msg = formatSmartcarDcSyncMessage(err)
            preferencesManager.recordSmartcarDcSyncResult(System.currentTimeMillis(), msg)
            _lastSyncMessage.value = msg
            err
        } finally {
            _isSyncing.value = false
        }
    }
}
