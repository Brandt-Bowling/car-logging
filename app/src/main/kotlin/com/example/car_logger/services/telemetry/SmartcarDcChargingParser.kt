package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.SmartcarChargeRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.ZoneId
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToLong

internal object SmartcarDcChargingParser {

    fun parseSmartcarChargeRecords(rawJson: String): List<SmartcarChargeRecord> {
        return try {
            val root = telemetryJson.parseToJsonElement(rawJson) as? JsonObject ?: return emptyList()
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
                val startMs = TelemetryJsonUtils.parseFlexibleTimestampMs(obj["startTime"]) ?: continue
                val rawEndMs = TelemetryJsonUtils.parseFlexibleTimestampMs(obj["endTime"])
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
            val bodyObj = extractSignalBody(rawJson) ?: return null
            bodyObj["value"].asBooleanOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun parseSmartcarSignalDouble(rawJson: String): Double? {
        return try {
            val bodyObj = extractSignalBody(rawJson) ?: return null
            bodyObj["value"].asDoubleOrNull()?.takeIf { it.isFinite() }
        } catch (_: Exception) {
            null
        }
    }

    fun parseSmartcarSignalString(rawJson: String): String? {
        return try {
            val bodyObj = extractSignalBody(rawJson) ?: return null
            bodyObj["value"].asStringOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractSignalBody(rawJson: String): JsonObject? {
        val root = telemetryJson.parseToJsonElement(rawJson) as? JsonObject ?: return null
        val dataObj = root["data"] as? JsonObject
        val attrsObj = dataObj?.get("attributes") as? JsonObject
        return (attrsObj?.get("body") as? JsonObject) ?: (root["body"] as? JsonObject) ?: root
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
            if (TeslaSuperchargerParser.hasOverlappingPublicSession(
                    combinedPublicForOverlap,
                    targetCar?.id,
                    record.startDateMs,
                    record.endDateMs,
                    record.energyKwh
                )
            ) {
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
}
