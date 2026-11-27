package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.WorkLocationCaptureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

internal class TeslaWorkLocationCapturer(
    private val preferencesManager: PreferencesManager,
    private val teslaAuthClient: TeslaAuthClient,
    private val stateSink: TelemetrySyncStateSink
) {
    suspend fun captureCurrentTeslaWorkLocation(
        cars: List<Car>,
        forceWake: Boolean = false
    ): WorkLocationCaptureResult = withContext(Dispatchers.IO) {
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return@withContext WorkLocationCaptureResult.NotConfigured
        }
        var token = teslaAuthClient.getValidTeslaAccessToken()
            ?: return@withContext WorkLocationCaptureResult.Error("Tesla token expired — reconnect in Settings")

        val jwtScopes = TelemetryJsonUtils.extractJwtScopes(token)
        if (jwtScopes != null && !jwtScopes.contains(OdometerSyncService.TESLA_SCOPE_VEHICLE_LOCATION)) {
            return@withContext WorkLocationCaptureResult.MissingScope(
                "Tesla token is missing vehicle_location scope — enable Vehicle Location in Tesla Developer Portal and reconnect"
            )
        }

        val targetCar = cars.firstOrNull {
            !it.isArchived && TelemetryJsonUtils.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_TESLA
        }
        stateSink.setSyncing(true)
        try {
            val (vin, state) = try {
                val (listCode, listBody, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(
                    "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles",
                    token
                )
                token = updatedToken
                if (listCode !in 200..299) {
                    val detail = TeslaAuthClient.parseTeslaErrorDetail(listBody)
                    return@withContext WorkLocationCaptureResult.Error(detail ?: "Tesla API ($listCode)")
                }
                TeslaOdometerSyncer.parseTeslaVehicleListState(listBody, targetCar?.vin)
                    ?: return@withContext WorkLocationCaptureResult.Error("No Tesla vehicle found")
            } catch (_: Exception) {
                return@withContext WorkLocationCaptureResult.Error("Network error contacting Tesla")
            }

            if (state != "online") {
                if (!forceWake) {
                    return@withContext WorkLocationCaptureResult.SkippedAsleep()
                }
                val (_, afterWakeToken) = teslaAuthClient.wakeTeslaVehicleOnce(vin, token)
                token = afterWakeToken
            }

            val (locCode, locBody, _) = teslaAuthClient.executeTeslaGetWithRefresh(
                "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin/vehicle_data?endpoints=location_data",
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
                val detail = TeslaAuthClient.parseTeslaErrorDetail(locBody)
                return@withContext WorkLocationCaptureResult.Error(detail ?: "Tesla location API ($locCode)")
            }

            val root = runCatching { telemetryJson.parseToJsonElement(locBody) as? JsonObject }.getOrNull()
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
            stateSink.setSyncing(false)
        }
    }
}
