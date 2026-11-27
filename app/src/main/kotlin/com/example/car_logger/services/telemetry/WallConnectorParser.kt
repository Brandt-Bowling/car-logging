package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.TeslaWallConnectorChargeSession
import com.example.car_logger.services.TeslaWallConnectorDevice
import com.example.car_logger.services.WallConnectorType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.ZoneId
import kotlin.math.round

internal object WallConnectorParser {

    fun parseTeslaWallConnectorChargeHistory(
        jsonBody: String,
        energySiteId: String
    ): List<TeslaWallConnectorChargeSession> {
        return try {
            val rootElement = telemetryJson.parseToJsonElement(jsonBody)
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

                val startMs = TelemetryJsonUtils.parseFlexibleTimestampMs(
                    obj["charge_start_time"]
                        ?: obj["charging_start_time"]
                        ?: obj["start_date"]
                        ?: obj["start_time"]
                        ?: obj["timestamp"]
                        ?: obj["datetime"]
                        ?: obj["time"]
                ) ?: continue

                val explicitEndMs = TelemetryJsonUtils.parseFlexibleTimestampMs(
                    obj["charge_stop_time"]
                        ?: obj["charging_stop_time"]
                        ?: obj["end_date"]
                        ?: obj["stop_time"]
                        ?: obj["end_time"]
                )
                val durationSec = TelemetryJsonUtils.parseFlexibleDurationSeconds(
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
            val kwh = round((raw.energyWh / 1000.0) * 1000.0) / 1000.0
            if (kwh <= 0.0) continue

            val matchedDevice = devices.firstOrNull { dev ->
                !raw.din.isNullOrBlank() && dev.din.equals(raw.din, ignoreCase = true)
            } ?: devices.firstOrNull { dev ->
                dev.energySiteId == raw.energySiteId
            }

            val connectorType = if (!raw.din.isNullOrBlank() || !raw.vin.isNullOrBlank()) {
                WallConnectorDeviceParser.classifyWallConnectorType(
                    din = raw.din ?: matchedDevice?.din,
                    partNumber = matchedDevice?.partNumber,
                    siteName = matchedDevice?.siteName,
                    sessionVin = raw.vin
                )
            } else {
                matchedDevice?.connectorType ?: WallConnectorType.GEN_3
            }

            val targetCar = WallConnectorDeviceParser.resolveCarForWallConnector(
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
}
