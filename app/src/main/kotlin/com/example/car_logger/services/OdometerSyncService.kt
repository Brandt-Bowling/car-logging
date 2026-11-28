package com.example.car_logger.services

import android.net.Uri
import com.example.car_logger.BuildConfig
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.di.AppContainer
import com.example.car_logger.services.telemetry.SmartcarAuthClient
import com.example.car_logger.services.telemetry.SmartcarDcChargingParser
import com.example.car_logger.services.telemetry.SmartcarDcChargingSyncer
import com.example.car_logger.services.telemetry.SmartcarOdometerSyncer
import com.example.car_logger.services.telemetry.TelemetryJsonUtils
import com.example.car_logger.services.telemetry.TelemetryStatusFormatters
import com.example.car_logger.services.telemetry.TelemetrySyncStateSink
import com.example.car_logger.services.telemetry.TeslaAuthClient
import com.example.car_logger.services.telemetry.TeslaOdometerSyncer
import com.example.car_logger.services.telemetry.TeslaSuperchargerParser
import com.example.car_logger.services.telemetry.TeslaSuperchargerSyncer
import com.example.car_logger.services.telemetry.TeslaWorkChargeEvaluator
import com.example.car_logger.services.telemetry.TeslaWorkChargingSyncer
import com.example.car_logger.services.telemetry.TeslaWorkLocationCapturer
import com.example.car_logger.services.telemetry.WallConnectorDeviceParser
import com.example.car_logger.services.telemetry.WallConnectorParser
import com.example.car_logger.services.telemetry.WallConnectorSyncer
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

class OdometerSyncService(
    private val preferencesManager: PreferencesManager,
    private val carDao: CarDao,
    private val odometerLogDao: OdometerLogDao? = null,
    private val chargingSessionDao: ChargingSessionDao? = null,
    internal val client: OkHttpClient = AppContainer.defaultHttpClient
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    internal val stateSink = TelemetrySyncStateSink(_isSyncing, _lastSyncMessage)
    internal var wakePollDelayMs: Long = 5_000L

    private val teslaAuthClient = TeslaAuthClient(preferencesManager, client, stateSink) { wakePollDelayMs }
    private val smartcarAuthClient = SmartcarAuthClient(preferencesManager, client, stateSink)
    private val wallConnectorSyncer = WallConnectorSyncer(preferencesManager, chargingSessionDao, teslaAuthClient, stateSink)
    private val teslaWorkChargingSyncer = TeslaWorkChargingSyncer(
        preferencesManager, carDao, odometerLogDao, chargingSessionDao, teslaAuthClient, wallConnectorSyncer, stateSink
    )
    private val teslaWorkLocationCapturer = TeslaWorkLocationCapturer(preferencesManager, teslaAuthClient, stateSink)
    private val teslaSuperchargerSyncer = TeslaSuperchargerSyncer(preferencesManager, chargingSessionDao, teslaAuthClient, stateSink)
    private val smartcarDcChargingSyncer = SmartcarDcChargingSyncer(
        preferencesManager, chargingSessionDao, client, smartcarAuthClient, stateSink
    )
    private val teslaOdometerSyncer = TeslaOdometerSyncer(
        preferencesManager, carDao, odometerLogDao, teslaAuthClient, stateSink
    ) { jsonBody, vin, car, nowMs, isCached ->
        if (chargingSessionDao != null && preferencesManager.getWorkChargingSyncEnabled()) {
            parseTeslaChargeSnapshot(jsonBody, vin, isCached)?.let {
                teslaWorkChargingSyncer.processWorkChargeSnapshot(it, car, nowMs, recordSyncStatus = false)
            }
        }
    }
    private val smartcarOdometerSyncer = SmartcarOdometerSyncer(
        preferencesManager, carDao, odometerLogDao, client, smartcarAuthClient, stateSink
    ) { vehicleId, userId, token, car, nowMs ->
        val dao = chargingSessionDao ?: return@SmartcarOdometerSyncer
        if (preferencesManager.getSmartcarDcSyncEnabled() &&
            preferencesManager.getSmartcarDcCapability() == PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY
        ) {
            smartcarDcChargingSyncer.probeSmartcarLiveDcSnapshot(vehicleId, userId, token, car, dao, nowMs, false)
        }
    }

    internal suspend fun recordOdometerReading(carId: String, odometer: Int, provider: String, syncTime: Long) =
        TelemetryJsonUtils.recordOdometerReading(carDao, odometerLogDao, carId, odometer, provider, syncTime)

    fun buildSmartcarAuthUrl(clientOrAppId: String): String = Companion.buildSmartcarAuthUrl(clientOrAppId)

    fun buildTeslaAuthUrl(
        clientId: String,
        includeLocationScope: Boolean = false,
        includeChargingHistoryScope: Boolean = false
    ): String = Companion.buildTeslaAuthUrl(clientId, includeLocationScope, includeChargingHistoryScope)

    suspend fun resolveSmartcarAuthUrl(clientOrAppId: String): String = smartcarAuthClient.resolveSmartcarAuthUrl(clientOrAppId)
    suspend fun refreshSmartcarConnectionStatus(): Boolean = smartcarAuthClient.refreshSmartcarConnectionStatus()
    suspend fun getEffectiveSmartcarCredentials(): Pair<String?, String?> = smartcarAuthClient.getEffectiveSmartcarCredentials()
    suspend fun getEffectiveTeslaCredentials(): Pair<String?, String?> = teslaAuthClient.getEffectiveTeslaCredentials()

    suspend fun handleOAuthCallback(uri: Uri): Boolean =
        smartcarAuthClient.handleSmartcarCallback(uri) ?: run {
            val code = uri.getQueryParameter("code") ?: return@run false
            val path = uri.path.orEmpty()
            if (path.contains("tesla", ignoreCase = true)) teslaAuthClient.exchangeTeslaCode(code) else false
        }

    suspend fun syncAllEligibleVehicles(cars: List<Car>, isPostDrive: Boolean = false): List<OdometerSyncResult> =
        withContext(Dispatchers.IO) {
            val policy = preferencesManager.getWakePolicy()
            if (policy == WakeProtectionPolicy.MANUAL_ONLY) {
                return@withContext cars.map { OdometerSyncResult.SkippedPolicy(it.id, policy) }
            }
            if (policy == WakeProtectionPolicy.POST_DRIVE && !isPostDrive) {
                return@withContext cars.map { OdometerSyncResult.SkippedPolicy(it.id, policy) }
            }
            val results = mutableListOf<OdometerSyncResult>()
            for (car in cars) {
                if (car.isArchived || resolveProviderForCar(car) == null) continue
                results.add(syncVehicle(car = car, isManual = false, forceWake = false))
            }
            results
        }

    suspend fun syncDailyEndOfDay(cars: List<Car>): List<OdometerSyncResult> = withContext(Dispatchers.IO) {
        val eligibleCars = cars.filter { !it.isArchived && resolveProviderForCar(it) != null }
        val results = eligibleCars.map { car ->
            syncVehicle(car = car, isManual = false, forceWake = true, bypassCooldown = true)
        }
        preferencesManager.recordDailyPollResult(System.currentTimeMillis(), formatDailyPollSummary(eligibleCars, results))
        results
    }

    suspend fun syncVehicle(
        car: Car,
        isManual: Boolean = false,
        forceWake: Boolean = false,
        bypassCooldown: Boolean = false
    ): OdometerSyncResult = withContext(Dispatchers.IO) {
        val provider = resolveProviderForCar(car) ?: return@withContext OdometerSyncResult.NotConfigured(car.id)
        val cooldownHours = preferencesManager.getSyncCooldownHours()
        val nowMs = System.currentTimeMillis()
        if (!forceWake && !bypassCooldown && isCooldownActive(car.lastOdometerSyncTime, cooldownHours, nowMs)) {
            return@withContext OdometerSyncResult.SkippedCooldown(
                car.id,
                remainingCooldownHours(car.lastOdometerSyncTime, cooldownHours, nowMs)
            )
        }
        _isSyncing.value = true
        try {
            when (provider) {
                PROVIDER_TESLA -> teslaOdometerSyncer.syncTeslaVehicle(car, isManual = isManual, forceWake = forceWake)
                PROVIDER_SMARTCAR -> smartcarOdometerSyncer.syncSmartcarVehicle(car)
                else -> OdometerSyncResult.NotConfigured(car.id)
            }
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun syncWallConnectorCharging(cars: List<Car>, lookbackDays: Int? = null): WallConnectorSyncResult =
        wallConnectorSyncer.syncWallConnectorCharging(cars, lookbackDays)

    suspend fun syncTeslaWorkCharging(
        cars: List<Car>,
        forceWake: Boolean = false,
        syncHomeChargersFirst: Boolean = true
    ): WorkChargingSyncResult = teslaWorkChargingSyncer.syncTeslaWorkCharging(cars, forceWake, syncHomeChargersFirst)

    suspend fun captureCurrentTeslaWorkLocation(cars: List<Car>, forceWake: Boolean = false): WorkLocationCaptureResult =
        teslaWorkLocationCapturer.captureCurrentTeslaWorkLocation(cars, forceWake)

    suspend fun syncTeslaSuperchargerCharging(
        cars: List<Car>,
        lookbackDays: Int? = null,
        startInstant: Instant? = null,
        endInstant: Instant? = null
    ): SuperchargerSyncResult = teslaSuperchargerSyncer.syncTeslaSuperchargerCharging(cars, lookbackDays, startInstant, endInstant)

    suspend fun syncSmartcarDcCharging(
        cars: List<Car>,
        forceCapabilityRecheck: Boolean = false,
        allowLiveProbe: Boolean = false
    ): SmartcarDcSyncResult = smartcarDcChargingSyncer.syncSmartcarDcCharging(cars, forceCapabilityRecheck, allowLiveProbe)

    companion object {
        const val PROVIDER_SMARTCAR = "SMARTCAR"
        const val PROVIDER_TESLA = "TESLA_FLEET"
        const val FALLBACK_SMARTCAR_APPLICATION_ID = "c6e28e0b-2199-47e6-b367-51cb96965b30"

        val DEFAULT_SMARTCAR_APPLICATION_ID: String = BuildConfig.SMARTCAR_APPLICATION_ID.ifBlank { FALLBACK_SMARTCAR_APPLICATION_ID }
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
        internal const val TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS = 364
        internal const val DEFAULT_SUPERCHARGER_INITIAL_LOOKBACK_DAYS = 364
        internal const val SUPERCHARGER_INCREMENTAL_OVERLAP_DAYS = 1

        internal suspend fun recordOdometerReading(
            carDao: CarDao, odometerLogDao: OdometerLogDao?, carId: String, odometer: Int, provider: String, syncTime: Long
        ) = TelemetryJsonUtils.recordOdometerReading(carDao, odometerLogDao, carId, odometer, provider, syncTime)

        internal fun parseJwtClaims(token: String): JsonObject? = TelemetryJsonUtils.parseJwtClaims(token)
        internal fun extractJwtExpiryMs(token: String): Long? = TelemetryJsonUtils.extractJwtExpiryMs(token)
        internal fun extractJwtScopes(token: String): Set<String>? = TelemetryJsonUtils.extractJwtScopes(token)
        fun hasTeslaLocationScope(token: String?): Boolean = TelemetryJsonUtils.hasTeslaLocationScope(token)
        fun hasTeslaChargingHistoryScope(token: String?): Boolean = TelemetryJsonUtils.hasTeslaChargingHistoryScope(token)
        internal fun parseTeslaErrorDetail(rawBody: String): String? = TeslaAuthClient.parseTeslaErrorDetail(rawBody)
        internal fun isTeslaScopeError(httpCode: Int, errorDetail: String?): Boolean = TeslaAuthClient.isTeslaScopeError(httpCode, errorDetail)

        fun isCooldownActive(lastSyncMs: Long?, cooldownHours: Int, nowMs: Long = System.currentTimeMillis()): Boolean =
            TelemetryJsonUtils.isCooldownActive(lastSyncMs, cooldownHours, nowMs)

        fun remainingCooldownHours(lastSyncMs: Long?, cooldownHours: Int, nowMs: Long = System.currentTimeMillis()): Int =
            TelemetryJsonUtils.remainingCooldownHours(lastSyncMs, cooldownHours, nowMs)

        fun formatRelativeTime(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String? =
            TelemetryJsonUtils.formatRelativeTime(timestampMs, nowMs)

        fun parseSmartcarOdometerMiles(jsonBody: String): Int? = SmartcarOdometerSyncer.parseSmartcarOdometerMiles(jsonBody)
        fun parseSmartcarV3Connection(jsonBody: String, preferredUserId: String? = null): Pair<String, String?>? =
            SmartcarAuthClient.parseSmartcarV3Connection(jsonBody, preferredUserId)
        fun parseSmartcarV3ApplicationId(jsonBody: String): String? = SmartcarAuthClient.parseSmartcarV3ApplicationId(jsonBody)
        fun parseSmartcarErrorDetail(jsonBody: String): String? = SmartcarAuthClient.parseSmartcarErrorDetail(jsonBody)

        fun formatSyncResultMessage(car: Car, result: OdometerSyncResult): String =
            TelemetryStatusFormatters.formatSyncResultMessage(car, result)

        fun formatDailyPollSummary(cars: List<Car>, results: List<OdometerSyncResult>, chargerResult: WallConnectorSyncResult? = null): String =
            TelemetryStatusFormatters.formatDailyPollSummary(cars, results, chargerResult)

        fun formatWallConnectorSyncMessage(result: WallConnectorSyncResult): String =
            TelemetryStatusFormatters.formatWallConnectorSyncMessage(result)

        fun classifyWallConnectorType(
            din: String? = null, partNumber: String? = null, siteName: String? = null, sessionVin: String? = null, index: Int = 0
        ): WallConnectorType = WallConnectorDeviceParser.classifyWallConnectorType(din, partNumber, siteName, sessionVin, index)

        fun parseTeslaEnergySites(productsJson: String): List<TeslaWallConnectorDevice> =
            WallConnectorDeviceParser.parseTeslaEnergySites(productsJson)

        fun parseTeslaSiteInfoWallConnectors(
            siteInfoJson: String, energySiteId: String, fallbackSiteName: String? = null, startingIndex: Int = 0
        ): List<TeslaWallConnectorDevice> =
            WallConnectorDeviceParser.parseTeslaSiteInfoWallConnectors(siteInfoJson, energySiteId, fallbackSiteName, startingIndex)

        fun resolveCarForWallConnector(
            connectorType: WallConnectorType, sessionVin: String? = null, cars: List<Car>,
            gen3OverrideCarId: String? = null, universalOverrideCarId: String? = null
        ): Car? = WallConnectorDeviceParser.resolveCarForWallConnector(
            connectorType, sessionVin, cars, gen3OverrideCarId, universalOverrideCarId
        )

        fun parseTeslaWallConnectorChargeHistory(jsonBody: String, energySiteId: String): List<TeslaWallConnectorChargeSession> =
            WallConnectorParser.parseTeslaWallConnectorChargeHistory(jsonBody, energySiteId)

        fun buildWallConnectorSessionEntities(
            rawSessions: List<TeslaWallConnectorChargeSession>, devices: List<TeslaWallConnectorDevice>, cars: List<Car>,
            gen3OverrideCarId: String? = null, universalOverrideCarId: String? = null,
            existingIds: Set<String> = emptySet(), existingFingerprints: Set<String> = emptySet(),
            uploadedAtMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()
        ): List<ChargingSessionEntity> = WallConnectorParser.buildWallConnectorSessionEntities(
            rawSessions, devices, cars, gen3OverrideCarId, universalOverrideCarId,
            existingIds, existingFingerprints, uploadedAtMs, zoneId
        )

        fun parseTeslaVehicleListState(jsonBody: String, targetVin: String?): Pair<String, String>? =
            TeslaOdometerSyncer.parseTeslaVehicleListState(jsonBody, targetVin)

        fun parseTeslaOdometerMiles(jsonBody: String): Int? = TeslaOdometerSyncer.parseTeslaOdometerMiles(jsonBody)
        fun parseTeslaWakeState(jsonBody: String): String? = TeslaAuthClient.parseTeslaWakeState(jsonBody)

        suspend fun waitForTeslaWakeUp(maxAttempts: Int = 6, delayMs: Long = 5_000L, pollWakeState: suspend () -> String?): Boolean =
            TeslaAuthClient.waitForTeslaWakeUp(maxAttempts, delayMs, pollWakeState)

        suspend fun wakeAndPollUntilOnline(
            maxStatePolls: Int = 6, delayMs: Long = 5_000L, sendWake: suspend () -> String?, pollState: suspend () -> String?
        ): Boolean = TeslaAuthClient.wakeAndPollUntilOnline(maxStatePolls, delayMs, sendWake, pollState)

        fun shouldRecordOdometerLog(
            latestLog: OdometerLogEntity?, newOdometer: Int, syncTimeMs: Long, zoneId: ZoneId = ZoneId.systemDefault()
        ): Boolean = TelemetryJsonUtils.shouldRecordOdometerLog(latestLog, newOdometer, syncTimeMs, zoneId)

        fun resolveProviderForCar(car: Car): String? = TelemetryJsonUtils.resolveProviderForCar(car)
        fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
            TelemetryJsonUtils.haversineMeters(lat1, lng1, lat2, lng2)

        fun parseTeslaChargeSnapshot(jsonBody: String, vin: String, isCachedResponse: Boolean = false): TeslaChargeSnapshot? =
            TeslaWorkChargeEvaluator.parseTeslaChargeSnapshot(jsonBody, vin, isCachedResponse)

        fun evaluateWorkChargeCandidate(
            snapshot: TeslaChargeSnapshot, targetCarId: String?, existingSessions: List<ChargingSessionEntity>,
            workLocation: WorkLocationConfig? = null, lastWatermarkEnergy: Double = -1.0,
            lastWatermarkDateIso: String? = null, lastWatermarkVenue: String? = null,
            nowMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()
        ): WorkChargeDecision = TeslaWorkChargeEvaluator.evaluateWorkChargeCandidate(
            snapshot, targetCarId, existingSessions, workLocation,
            lastWatermarkEnergy, lastWatermarkDateIso, lastWatermarkVenue, nowMs, zoneId
        )

        fun formatWorkChargingSyncMessage(result: WorkChargingSyncResult): String =
            TelemetryStatusFormatters.formatWorkChargingSyncMessage(result)

        internal fun buildSuperchargerDateWindows(
            endInstant: Instant, lookbackDays: Int, zoneId: ZoneId = ZoneId.systemDefault(),
            maxWindowDays: Int = TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS
        ): List<Pair<Instant, Instant>> =
            TeslaSuperchargerParser.buildSuperchargerDateWindows(endInstant, lookbackDays, zoneId, maxWindowDays)

        internal fun buildSuperchargerDateWindowsForRange(
            startInstant: Instant, endInstant: Instant, nowInstant: Instant = Instant.now(),
            maxWindowDays: Int = TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS
        ): List<Pair<Instant, Instant>> =
            TeslaSuperchargerParser.buildSuperchargerDateWindowsForRange(startInstant, endInstant, nowInstant, maxWindowDays)

        internal fun resolveIncrementalSuperchargerWindows(
            existingSessions: List<ChargingSessionEntity>, nowInstant: Instant = Instant.now(),
            zoneId: ZoneId = ZoneId.systemDefault(), maxWindowDays: Int = TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS,
            overlapDays: Int = SUPERCHARGER_INCREMENTAL_OVERLAP_DAYS
        ): List<Pair<Instant, Instant>> = TeslaSuperchargerParser.resolveIncrementalSuperchargerWindows(
            existingSessions, nowInstant, zoneId, maxWindowDays, overlapDays
        )

        fun parseTeslaSuperchargerHistory(rawJson: String): Pair<List<TeslaSuperchargerSession>, Boolean> =
            TeslaSuperchargerParser.parseTeslaSuperchargerHistory(rawJson)

        fun buildSuperchargerSessionEntities(
            rawSessions: List<TeslaSuperchargerSession>, cars: List<Car>,
            existingSessions: List<ChargingSessionEntity> = emptyList(),
            uploadedAtMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()
        ): List<ChargingSessionEntity> =
            TeslaSuperchargerParser.buildSuperchargerSessionEntities(rawSessions, cars, existingSessions, uploadedAtMs, zoneId)

        fun formatSuperchargerSyncMessage(result: SuperchargerSyncResult): String =
            TelemetryStatusFormatters.formatSuperchargerSyncMessage(result)

        fun parseSmartcarV3ErrorCode(rawBody: String): Pair<String?, String?> = SmartcarAuthClient.parseSmartcarV3ErrorCode(rawBody)
        fun isSmartcarNotCapableError(httpCode: Int, rawBody: String): Boolean =
            SmartcarAuthClient.isSmartcarNotCapableError(httpCode, rawBody)
        fun isSmartcarPermissionError(httpCode: Int, rawBody: String): Boolean =
            SmartcarAuthClient.isSmartcarPermissionError(httpCode, rawBody)
        fun parseSmartcarChargeRecords(rawJson: String): List<SmartcarChargeRecord> =
            SmartcarDcChargingParser.parseSmartcarChargeRecords(rawJson)
        fun parseSmartcarSignalBoolean(rawJson: String): Boolean? = SmartcarDcChargingParser.parseSmartcarSignalBoolean(rawJson)
        fun parseSmartcarSignalDouble(rawJson: String): Double? = SmartcarDcChargingParser.parseSmartcarSignalDouble(rawJson)
        fun parseSmartcarSignalString(rawJson: String): String? = SmartcarDcChargingParser.parseSmartcarSignalString(rawJson)

        fun buildSmartcarDcSessionEntities(
            records: List<SmartcarChargeRecord>, targetCar: Car?,
            existingSessions: List<ChargingSessionEntity> = emptyList(),
            uploadedAtMs: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()
        ): List<ChargingSessionEntity> =
            SmartcarDcChargingParser.buildSmartcarDcSessionEntities(records, targetCar, existingSessions, uploadedAtMs, zoneId)

        fun formatSmartcarDcSyncMessage(result: SmartcarDcSyncResult): String =
            TelemetryStatusFormatters.formatSmartcarDcSyncMessage(result)
        fun buildSmartcarAuthUrl(clientOrAppId: String): String = SmartcarAuthClient.buildSmartcarAuthUrl(clientOrAppId)

        fun buildTeslaAuthUrl(
            clientId: String, includeLocationScope: Boolean = false, includeChargingHistoryScope: Boolean = false
        ): String = TeslaAuthClient.buildTeslaAuthUrl(clientId, includeLocationScope, includeChargingHistoryScope)
    }
}
