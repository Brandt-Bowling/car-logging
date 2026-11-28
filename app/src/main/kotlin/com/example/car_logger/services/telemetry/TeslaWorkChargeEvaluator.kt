package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.TeslaChargeSnapshot
import com.example.car_logger.services.WorkChargeDecision
import com.example.car_logger.services.WorkLocationConfig
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

internal object TeslaWorkChargeEvaluator {

    fun parseTeslaChargeSnapshot(
        jsonBody: String,
        vin: String,
        isCachedResponse: Boolean = false
    ): TeslaChargeSnapshot? {
        return try {
            val root = telemetryJson.parseToJsonElement(jsonBody) as? JsonObject ?: return null
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
                timestampMs = TelemetryJsonUtils.parseFlexibleTimestampMs(chargeState["timestamp"])
                    ?: TelemetryJsonUtils.parseFlexibleTimestampMs(vehicleState?.get("timestamp")),
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
            val distMeters = TelemetryJsonUtils.haversineMeters(
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
}
