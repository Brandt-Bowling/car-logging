package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.Locale
import kotlin.math.roundToInt

internal class TeslaOdometerSyncer(
    private val preferencesManager: PreferencesManager,
    private val carDao: CarDao,
    private val odometerLogDao: OdometerLogDao?,
    private val teslaAuthClient: TeslaAuthClient,
    private val stateSink: TelemetrySyncStateSink,
    private val onWorkChargePayload: suspend (jsonBody: String, vin: String, car: Car, nowMs: Long, isCached: Boolean) -> Unit
) {

    suspend fun syncTeslaVehicle(
        car: Car,
        isManual: Boolean = false,
        forceWake: Boolean
    ): OdometerSyncResult {
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return OdometerSyncResult.NotConfigured(car.id)
        }
        var token = teslaAuthClient.getValidTeslaAccessToken()
            ?: return OdometerSyncResult.Error(car.id, "Tesla token expired — reconnect or paste a new token in Settings")

        val (vin, state) = try {
            val (listCode, listBody, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(
                "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles",
                token
            )
            token = updatedToken
            if (listCode !in 200..299) {
                val detail = TeslaAuthClient.parseTeslaErrorDetail(listBody)
                val msg = detail?.let { "Tesla API ($listCode: $it)" } ?: "Tesla API ($listCode)"
                return OdometerSyncResult.Error(car.id, msg)
            }
            parseTeslaVehicleListState(listBody, car.vin)
                ?: return OdometerSyncResult.Error(car.id, "No Tesla found")
        } catch (_: Exception) {
            return OdometerSyncResult.Error(car.id, "Network error")
        }

        if (state != "online") {
            if (!isManual) {
                val cachedResult = tryCachedVehicleData(car, vin, token)
                token = cachedResult.second
                if (cachedResult.first != null) return cachedResult.first!!
            }

            if (!forceWake) {
                carDao.updateTelemetryStatus(car.id, OdometerSyncService.PROVIDER_TESLA, "Asleep")
                stateSink.setMessage("${car.model} asleep (wake skipped)")
                return OdometerSyncResult.SkippedAsleep(car.id)
            }

            val (_, afterWakeToken) = teslaAuthClient.wakeTeslaVehicleOnce(vin, token)
            token = afterWakeToken
        }

        val maxDataAttempts = if (forceWake) 3 else 1
        for (attempt in 1..maxDataAttempts) {
            try {
                val (dataCode, dataBody, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(
                    "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin/vehicle_data?endpoints=vehicle_state%3Bcharge_state",
                    token
                )
                token = updatedToken
                val result = if (dataCode !in 200..299) {
                    if (dataCode == 408) {
                        if (attempt < maxDataAttempts) {
                            null
                        } else {
                            carDao.updateTelemetryStatus(car.id, OdometerSyncService.PROVIDER_TESLA, "Asleep")
                            OdometerSyncResult.SkippedAsleep(car.id)
                        }
                    } else {
                        val detail = TeslaAuthClient.parseTeslaErrorDetail(dataBody)
                        val msg = detail?.let { "Tesla data ($dataCode: $it)" } ?: "Tesla data ($dataCode)"
                        OdometerSyncResult.Error(car.id, msg)
                    }
                } else {
                    val newOdo = parseTeslaOdometerMiles(dataBody)
                        ?: return OdometerSyncResult.Error(car.id, "Invalid odometer payload")
                    val finalOdo = maxOf(newOdo, car.odometer ?: 0)
                    val now = System.currentTimeMillis()
                    TelemetryJsonUtils.recordOdometerReading(
                        carDao = carDao,
                        odometerLogDao = odometerLogDao,
                        carId = car.id,
                        odometer = finalOdo,
                        provider = OdometerSyncService.PROVIDER_TESLA,
                        syncTime = now
                    )
                    runCatching { onWorkChargePayload(dataBody, vin, car, now, false) }
                    stateSink.setMessage("${car.model}: $finalOdo mi")
                    OdometerSyncResult.Updated(car.id, finalOdo, OdometerSyncService.PROVIDER_TESLA)
                }
                if (result != null) return result
                delay(5_000L)
            } catch (_: Exception) {
                return OdometerSyncResult.Error(car.id, "Failed to read Tesla odometer")
            }
        }
        carDao.updateTelemetryStatus(car.id, OdometerSyncService.PROVIDER_TESLA, "Asleep")
        return OdometerSyncResult.SkippedAsleep(car.id)
    }

    private suspend fun tryCachedVehicleData(
        car: Car,
        vin: String,
        initialToken: String
    ): Pair<OdometerSyncResult?, String> {
        var token = initialToken
        val res = try {
            val (cachedCode, cachedBody, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(
                "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin/vehicle_data_cached",
                token
            )
            token = updatedToken
            if (cachedCode in 200..299) {
                val cachedOdo = parseTeslaOdometerMiles(cachedBody)
                if (cachedOdo != null) {
                    val finalOdo = maxOf(cachedOdo, car.odometer ?: 0)
                    val now = System.currentTimeMillis()
                    TelemetryJsonUtils.recordOdometerReading(
                        carDao = carDao,
                        odometerLogDao = odometerLogDao,
                        carId = car.id,
                        odometer = finalOdo,
                        provider = OdometerSyncService.PROVIDER_TESLA,
                        syncTime = now
                    )
                    runCatching { onWorkChargePayload(cachedBody, vin, car, now, true) }
                    stateSink.setMessage("${car.model}: $finalOdo mi")
                    OdometerSyncResult.Updated(car.id, finalOdo, OdometerSyncService.PROVIDER_TESLA)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        return Pair(res, token)
    }

    companion object {
        fun parseTeslaVehicleListState(jsonBody: String, targetVin: String?): Pair<String, String>? {
            return try {
                val root = telemetryJson.parseToJsonElement(jsonBody) as? JsonObject ?: return null
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
                val root = telemetryJson.parseToJsonElement(jsonBody) as? JsonObject ?: return null
                val response = (root["response"] as? JsonObject) ?: root
                val dataObj = (response["data"] as? JsonObject) ?: response
                val vehicleState = (dataObj["vehicle_state"] as? JsonObject) ?: dataObj
                val odo = vehicleState["odometer"].asDoubleOrNull()
                odo?.roundToInt()?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }
    }
}
