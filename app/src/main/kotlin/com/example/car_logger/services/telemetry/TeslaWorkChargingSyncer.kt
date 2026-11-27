package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.TeslaChargeSnapshot
import com.example.car_logger.services.WorkChargeDecision
import com.example.car_logger.services.WorkChargingSyncResult
import com.example.car_logger.services.WorkLocationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

internal class TeslaWorkChargingSyncer(
    private val preferencesManager: PreferencesManager,
    private val carDao: CarDao,
    private val odometerLogDao: OdometerLogDao?,
    private val chargingSessionDao: ChargingSessionDao?,
    private val teslaAuthClient: TeslaAuthClient,
    private val wallConnectorSyncer: WallConnectorSyncer,
    private val stateSink: TelemetrySyncStateSink
) {
    suspend fun processWorkChargeSnapshot(
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

        val decision = TeslaWorkChargeEvaluator.evaluateWorkChargeCandidate(
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

        val result = applyDecision(dao, decision, snapshot, targetCar, todayIso, nowMs, zoneId)
        if (recordSyncStatus) {
            recordResult(nowMs, result)
        }
        return result
    }

    private suspend fun applyDecision(
        dao: ChargingSessionDao,
        decision: WorkChargeDecision,
        snapshot: TeslaChargeSnapshot,
        targetCar: Car?,
        todayIso: String,
        nowMs: Long,
        zoneId: ZoneId
    ): WorkChargingSyncResult = when (decision) {
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
            WorkChargingSyncResult.UpToDate(
                "Work charging up to date (${String.format(Locale.US, "%.1f", decision.energyKwh)} kWh logged)"
            )
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
        val initialToken = teslaAuthClient.getValidTeslaAccessToken()
        if (initialToken.isNullOrBlank()) {
            val expiredMsg = "Tesla access token expired — reconnect in Settings"
            preferencesManager.recordWorkChargingSyncResult(System.currentTimeMillis(), expiredMsg)
            stateSink.setMessage(expiredMsg)
            return@withContext WorkChargingSyncResult.Error(expiredMsg)
        }
        var token: String = initialToken

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
                    val err = WorkChargingSyncResult.Error(
                        detail?.let { "Tesla API ($listCode: $it)" } ?: "Tesla API ($listCode)"
                    )
                    return@withContext recordResult(System.currentTimeMillis(), err)
                }
                TeslaOdometerSyncer.parseTeslaVehicleListState(listBody, targetCar?.vin)
                    ?: return@withContext recordResult(
                        System.currentTimeMillis(),
                        WorkChargingSyncResult.Error("No Tesla vehicle found")
                    )
            } catch (e: Exception) {
                return@withContext recordResult(
                    System.currentTimeMillis(),
                    WorkChargingSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
                )
            }

            val (workLat, workLng, workRadius) = preferencesManager.getWorkLocation()
            val canRequestLocation = workLat != null && workLng != null &&
                TelemetryJsonUtils.hasTeslaLocationScope(token)
            val baseEndpoints = "vehicle_state%3Bcharge_state"
            val endpointsWithLocation = if (canRequestLocation) "$baseEndpoints%3Blocation_data" else baseEndpoints

            val snapshot: TeslaChargeSnapshot? = if (state == "online") {
                val (fetched, err) = fetchLiveSnapshot(vin, endpointsWithLocation, baseEndpoints, canRequestLocation, token)
                if (err != null) return@withContext recordResult(System.currentTimeMillis(), err)
                fetched
            } else {
                // Zero-wake first: try Tesla's cached vehicle data endpoint while asleep/offline
                val (cachedCode, cachedBody, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(
                    "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin/vehicle_data_cached",
                    token
                )
                token = updatedToken
                if (cachedCode in 200..299) {
                    TeslaWorkChargeEvaluator.parseTeslaChargeSnapshot(cachedBody, vin, isCachedResponse = true)
                } else if (!forceWake) {
                    return@withContext recordResult(System.currentTimeMillis(), WorkChargingSyncResult.SkippedAsleep())
                } else {
                    val (_, afterWakeToken) = teslaAuthClient.wakeTeslaVehicleOnce(vin, token)
                    val (fetched, err) = fetchLiveSnapshot(vin, endpointsWithLocation, baseEndpoints, canRequestLocation, afterWakeToken)
                    if (err != null) return@withContext recordResult(System.currentTimeMillis(), err)
                    fetched
                }
            }

            if (snapshot == null) {
                return@withContext recordResult(
                    System.currentTimeMillis(),
                    WorkChargingSyncResult.UpToDate("No charge state data returned")
                )
            }

            val nowMs = System.currentTimeMillis()
            if (targetCar != null && snapshot.odometerMiles != null && snapshot.odometerMiles > 0) {
                val finalOdo = maxOf(snapshot.odometerMiles, targetCar.odometer ?: 0)
                runCatching {
                    TelemetryJsonUtils.recordOdometerReading(
                        carDao = carDao,
                        odometerLogDao = odometerLogDao,
                        carId = targetCar.id,
                        odometer = finalOdo,
                        provider = OdometerSyncService.PROVIDER_TESLA,
                        syncTime = nowMs
                    )
                }
            }

            // Lazily refresh Home Wall Connectors (free Energy API) only when there's an AC charge
            // candidate that is not already GPS-confirmed inside the Work radius
            val isGpsConfirmedAtWork = workLat != null && workLng != null &&
                snapshot.latitude != null && snapshot.longitude != null &&
                snapshot.chargingState?.trim()?.lowercase(Locale.US) in listOf("charging", "complete", "stopped") &&
                TelemetryJsonUtils.haversineMeters(snapshot.latitude, snapshot.longitude, workLat, workLng) <= workRadius
            if (syncHomeChargersFirst &&
                !snapshot.fastChargerPresent &&
                snapshot.chargeEnergyAddedKwh >= 0.5 &&
                !isGpsConfirmedAtWork
            ) {
                runCatching { wallConnectorSyncer.syncWallConnectorCharging(cars) }
            }

            processWorkChargeSnapshot(
                snapshot = snapshot,
                targetCar = targetCar,
                nowMs = nowMs,
                recordSyncStatus = true
            )
        } finally {
            stateSink.setSyncing(false)
        }
    }

    private suspend fun fetchLiveSnapshot(
        vin: String,
        endpointsWithLocation: String,
        baseEndpoints: String,
        canRequestLocation: Boolean,
        initialToken: String
    ): Pair<TeslaChargeSnapshot?, WorkChargingSyncResult?> {
        var token = initialToken
        var (dataCode, dataBody, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(
            "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin/vehicle_data?endpoints=$endpointsWithLocation",
            token
        )
        token = updatedToken
        // Tesla returns 403 for the entire request if location_data is requested without vehicle_location scope
        if (dataCode == 403 && canRequestLocation) {
            val retry = teslaAuthClient.executeTeslaGetWithRefresh(
                "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin/vehicle_data?endpoints=$baseEndpoints",
                token
            )
            dataCode = retry.first
            dataBody = retry.second
        }
        if (dataCode !in 200..299) {
            val err = if (dataCode == 408) {
                WorkChargingSyncResult.SkippedAsleep()
            } else {
                val detail = TeslaAuthClient.parseTeslaErrorDetail(dataBody)
                WorkChargingSyncResult.Error(detail?.let { "Tesla data ($dataCode: $it)" } ?: "Tesla data ($dataCode)")
            }
            return Pair(null, err)
        }
        return Pair(TeslaWorkChargeEvaluator.parseTeslaChargeSnapshot(dataBody, vin, isCachedResponse = false), null)
    }

    private suspend fun recordResult(nowMs: Long, result: WorkChargingSyncResult): WorkChargingSyncResult {
        val msg = TelemetryStatusFormatters.formatWorkChargingSyncMessage(result)
        preferencesManager.recordWorkChargingSyncResult(nowMs, msg)
        stateSink.setMessage(msg)
        return result
    }
}
