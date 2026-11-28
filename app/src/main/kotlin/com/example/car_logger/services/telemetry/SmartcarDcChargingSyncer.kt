package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SmartcarDcSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import kotlin.math.round

internal class SmartcarDcChargingSyncer(
    private val preferencesManager: PreferencesManager,
    private val chargingSessionDao: ChargingSessionDao?,
    private val client: OkHttpClient,
    private val smartcarAuthClient: SmartcarAuthClient,
    private val stateSink: TelemetrySyncStateSink
) {
    suspend fun probeSmartcarLiveDcSnapshot(
        vehicleId: String,
        userId: String?,
        initialToken: String,
        targetCar: Car?,
        dao: ChargingSessionDao,
        nowMs: Long,
        recordStatusOnIdle: Boolean
    ): SmartcarDcSyncResult {
        var token = initialToken
        val (fastCode, fastBody, tokenAfterFast) = smartcarAuthClient.executeSmartcarV3SignalGet(
            vehicleId = vehicleId,
            signalCode = "charge-isfastchargerpresent",
            userId = userId,
            initialToken = token
        )
        token = tokenAfterFast

        val (energyCode, energyBody, tokenAfterEnergy) = smartcarAuthClient.executeSmartcarV3SignalGet(
            vehicleId = vehicleId,
            signalCode = "charge-energyadded",
            userId = userId,
            initialToken = token
        )
        token = tokenAfterEnergy

        if (SmartcarAuthClient.isSmartcarPermissionError(fastCode, fastBody) ||
            SmartcarAuthClient.isSmartcarPermissionError(energyCode, energyBody)
        ) {
            val msg = "Missing Smartcar charge permissions — enable Charge signals in Smartcar Dashboard → Vehicle Access & reconnect"
            preferencesManager.recordSmartcarDcSyncResult(
                timestampMs = nowMs,
                status = msg,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION
            )
            return SmartcarDcSyncResult.MissingPermission(msg)
        }

        val fastNotCapable = fastCode !in 200..299 && SmartcarAuthClient.isSmartcarNotCapableError(fastCode, fastBody)
        val energyNotCapable = energyCode !in 200..299 && SmartcarAuthClient.isSmartcarNotCapableError(energyCode, energyBody)

        if (energyNotCapable || (fastNotCapable && energyCode !in 200..299)) {
            val msg = "Not supported by GM/OnStar — Superchargers sync via Tesla; use Import file for EA/EVgo"
            preferencesManager.recordSmartcarDcSyncResult(
                timestampMs = nowMs,
                status = msg,
                capability = PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED
            )
            return SmartcarDcSyncResult.UnsupportedByVehicle(msg)
        }

        var isFastCharger = if (fastCode in 200..299) SmartcarDcChargingParser.parseSmartcarSignalBoolean(fastBody) else null
        val energyAddedKwh = if (energyCode in 200..299) SmartcarDcChargingParser.parseSmartcarSignalDouble(energyBody) else null

        var fastChargerType: String? = null
        if (isFastCharger != true) {
            val (wattCode, wattBody, tokenAfterWatt) = smartcarAuthClient.executeSmartcarV3SignalGet(
                vehicleId = vehicleId,
                signalCode = "charge-wattage",
                userId = userId,
                initialToken = token
            )
            token = tokenAfterWatt
            val watts = if (wattCode in 200..299) SmartcarDcChargingParser.parseSmartcarSignalDouble(wattBody) else null
            if (watts != null && watts >= 20_000.0) {
                isFastCharger = true
            }
        }

        if (isFastCharger == true && energyAddedKwh != null && energyAddedKwh >= 0.5) {
            val (typeCode, typeBody, _) = smartcarAuthClient.executeSmartcarV3SignalGet(
                vehicleId = vehicleId,
                signalCode = "charge-fastchargertype",
                userId = userId,
                initialToken = token
            )
            if (typeCode in 200..299) {
                fastChargerType = SmartcarDcChargingParser.parseSmartcarSignalString(typeBody)
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
                status = TelemetryStatusFormatters.formatSmartcarDcSyncMessage(result),
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
        val (clientId, _) = smartcarAuthClient.getEffectiveSmartcarCredentials()
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

        var token = smartcarAuthClient.getValidSmartcarAccessToken() ?: return@withContext SmartcarDcSyncResult.NotConfigured
        val isV3 = clientId?.startsWith("client_") == true ||
            !preferencesManager.getSmartcarUserId().isNullOrBlank()
        if (!isV3) {
            return@withContext SmartcarDcSyncResult.UnsupportedByVehicle(
                "Smartcar v3 connection required for DC charging signals"
            )
        }

        val targetCar = cars.firstOrNull {
            !it.isArchived && TelemetryJsonUtils.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR
        }
        stateSink.setSyncing(true)
        try {
            var vehicleId = preferencesManager.getSmartcarVehicleId()
            var userId = preferencesManager.getSmartcarUserId()
            if (vehicleId.isNullOrBlank()) {
                val resolved = resolveConnectionId(token, userId)
                if (resolved.error != null) return@withContext resolved.error
                vehicleId = resolved.vehicleId!!
                userId = resolved.userId
                token = resolved.token
            }

            val nowMs = System.currentTimeMillis()
            val (recCode, recBody, updatedToken) = smartcarAuthClient.executeSmartcarV3SignalGet(
                vehicleId = vehicleId,
                signalCode = "charge-chargerecords",
                userId = userId,
                initialToken = token
            )
            token = updatedToken

            when {
                recCode in 200..299 -> {
                    val records = SmartcarDcChargingParser.parseSmartcarChargeRecords(recBody)
                    val existingSessions = dao.getAllSessionsList()
                    val newEntities = SmartcarDcChargingParser.buildSmartcarDcSessionEntities(
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
                    val msg = TelemetryStatusFormatters.formatSmartcarDcSyncMessage(result)
                    preferencesManager.recordSmartcarDcSyncResult(
                        timestampMs = nowMs,
                        status = msg,
                        capability = PreferencesManager.SMARTCAR_CAPABILITY_CHARGE_RECORDS
                    )
                    stateSink.setMessage(msg)
                    result
                }
                SmartcarAuthClient.isSmartcarPermissionError(recCode, recBody) -> {
                    val msg = "Missing read_charge_records — enable Charge Records in Smartcar Dashboard → Vehicle Access & reconnect"
                    preferencesManager.recordSmartcarDcSyncResult(
                        timestampMs = nowMs,
                        status = msg,
                        capability = PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION
                    )
                    stateSink.setMessage(msg)
                    SmartcarDcSyncResult.MissingPermission(msg)
                }
                SmartcarAuthClient.isSmartcarNotCapableError(recCode, recBody) -> {
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
                        stateSink.setMessage(TelemetryStatusFormatters.formatSmartcarDcSyncMessage(liveResult))
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
                    val detail = SmartcarAuthClient.parseSmartcarErrorDetail(recBody) ?: "Smartcar ($recCode)"
                    val err = SmartcarDcSyncResult.Error(detail)
                    val msg = TelemetryStatusFormatters.formatSmartcarDcSyncMessage(err)
                    preferencesManager.recordSmartcarDcSyncResult(nowMs, msg)
                    stateSink.setMessage(msg)
                    err
                }
            }
        } catch (e: Exception) {
            val err = SmartcarDcSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
            val msg = TelemetryStatusFormatters.formatSmartcarDcSyncMessage(err)
            preferencesManager.recordSmartcarDcSyncResult(System.currentTimeMillis(), msg)
            stateSink.setMessage(msg)
            err
        } finally {
            stateSink.setSyncing(false)
        }
    }

    private data class ResolvedConnection(
        val vehicleId: String?,
        val userId: String?,
        val token: String,
        val error: SmartcarDcSyncResult?
    )

    private suspend fun resolveConnectionId(initialToken: String, preferredUserId: String?): ResolvedConnection {
        var token = initialToken
        fun fetchConns(bearer: String): Pair<Int, String> {
            val req = Request.Builder()
                .url("${OdometerSyncService.SMARTCAR_V3_API_BASE}/connections")
                .addHeader("Authorization", "Bearer $bearer")
                .build()
            return client.newCall(req).execute().use { Pair(it.code, it.body?.string().orEmpty()) }
        }
        var (cCode, cBody) = fetchConns(token)
        if (cCode == 401) {
            val refreshed = smartcarAuthClient.getValidSmartcarAccessToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank() && refreshed != token) {
                token = refreshed
                val retry = fetchConns(token)
                cCode = retry.first
                cBody = retry.second
            }
        }
        if (cCode !in 200..299) {
            val detail = SmartcarAuthClient.parseSmartcarErrorDetail(cBody) ?: "Smartcar connections ($cCode)"
            return ResolvedConnection(null, preferredUserId, token, SmartcarDcSyncResult.Error(detail))
        }
        val parsed = SmartcarAuthClient.parseSmartcarV3Connection(cBody, preferredUserId)
            ?: return ResolvedConnection(null, preferredUserId, token, SmartcarDcSyncResult.NotConfigured)
        preferencesManager.setSmartcarVehicleId(parsed.first, parsed.second)
        return ResolvedConnection(parsed.first, parsed.second, token, null)
    }
}
