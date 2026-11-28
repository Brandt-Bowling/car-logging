package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

internal val telemetryJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonElement?.asDoubleOrNull(): Double? =
    (this as? JsonPrimitive)?.doubleOrNull

internal fun JsonElement?.asLongOrNull(): Long? =
    (this as? JsonPrimitive)?.longOrNull

internal fun JsonElement?.asIntOrNull(): Int? =
    (this as? JsonPrimitive)?.intOrNull

internal fun JsonElement?.asBooleanOrNull(): Boolean? =
    (this as? JsonPrimitive)?.booleanOrNull

internal class TelemetrySyncStateSink(
    private val isSyncing: MutableStateFlow<Boolean>,
    private val lastSyncMessage: MutableStateFlow<String?>
) {
    fun setSyncing(value: Boolean) {
        isSyncing.value = value
    }

    fun setMessage(message: String) {
        lastSyncMessage.value = message
    }
}

internal object TelemetryJsonUtils {

    fun parseJwtClaims(token: String): JsonObject? {
        val parts = token.trim().split(".")
        if (parts.size != 3 || parts[1].isBlank()) return null
        return try {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            telemetryJson.parseToJsonElement(String(decoded, Charsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    fun extractJwtExpiryMs(token: String): Long? {
        val expSec = parseJwtClaims(token)?.get("exp").asLongOrNull() ?: return null
        return if (expSec > 0L) expSec * 1000L else null
    }

    fun extractJwtScopes(token: String): Set<String>? {
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
        return extractJwtScopes(token)?.contains(OdometerSyncService.TESLA_SCOPE_VEHICLE_LOCATION) == true
    }

    fun hasTeslaChargingHistoryScope(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return extractJwtScopes(token)?.contains(OdometerSyncService.TESLA_SCOPE_VEHICLE_CHARGING_CMDS) == true
    }

    fun parseFlexibleTimestampMs(raw: JsonElement?): Long? {
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

    fun parseFlexibleDurationSeconds(raw: JsonElement?): Long? {
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
        return runCatching { Duration.parse(text).seconds }.getOrNull()?.takeIf { it >= 0L }
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
            makeLower.contains("tesla") -> OdometerSyncService.PROVIDER_TESLA
            makeLower.contains("chevrolet") || makeLower.contains("chevy") ||
                makeLower.contains("gmc") || makeLower.contains("cadillac") -> OdometerSyncService.PROVIDER_SMARTCAR
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

    suspend fun recordOdometerReading(
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
                    val baselineTime = if (prevSyncTime != null && prevSyncTime < syncTime) {
                        prevSyncTime
                    } else {
                        syncTime - 86_400_000L
                    }
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
}
