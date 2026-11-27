package com.example.car_logger.services

import android.net.Uri
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
import com.example.car_logger.di.AppContainer
import com.example.car_logger.services.telemetry.SmartcarAuthClient
import com.example.car_logger.services.telemetry.SmartcarOdometerSyncer
import com.example.car_logger.services.telemetry.TelemetryJsonUtils
import com.example.car_logger.services.telemetry.TelemetryStatusFormatters
import com.example.car_logger.services.telemetry.TelemetrySyncStateSink
import com.example.car_logger.services.telemetry.TeslaAuthClient
import com.example.car_logger.services.telemetry.TeslaOdometerSyncer
import com.example.car_logger.services.telemetry.asBooleanOrNull
import com.example.car_logger.services.telemetry.asDoubleOrNull
import com.example.car_logger.services.telemetry.asIntOrNull
import com.example.car_logger.services.telemetry.asLongOrNull
import com.example.car_logger.services.telemetry.asStringOrNull
import com.example.car_logger.services.telemetry.telemetryJson
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

class OdometerSyncService(
    private val preferencesManager: PreferencesManager,
    private val carDao: CarDao,
    private val odometerLogDao: OdometerLogDao? = null,
    private val chargingSessionDao: ChargingSessionDao? = null,
    internal val client: OkHttpClient = AppContainer.defaultHttpClient
) {
    internal suspend fun recordOdometerReading(carId: String, odometer: Int, provider: String, syncTime: Long) {
        TelemetryJsonUtils.recordOdometerReading(carDao, odometerLogDao, carId, odometer, provider, syncTime)
    }
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    internal val stateSink = TelemetrySyncStateSink(_isSyncing, _lastSyncMessage)

    internal var wakePollDelayMs: Long = 5_000L

    private val teslaAuthClient = TeslaAuthClient(
        preferencesManager = preferencesManager,
        client = client,
        stateSink = stateSink,
        wakePollDelayMsProvider = { wakePollDelayMs }
    )

    private val smartcarAuthClient = SmartcarAuthClient(
        preferencesManager = preferencesManager,
        client = client,
        stateSink = stateSink
    )

    private val teslaOdometerSyncer = TeslaOdometerSyncer(
        preferencesManager = preferencesManager,
        carDao = carDao,
        odometerLogDao = odometerLogDao,
        teslaAuthClient = teslaAuthClient,
        stateSink = stateSink,
        onWorkChargePayload = { jsonBody, vin, car, nowMs, isCached ->
            if (chargingSessionDao != null && preferencesManager.getWorkChargingSyncEnabled()) {
                val snapshot = parseTeslaChargeSnapshot(
                    jsonBody = jsonBody,
                    vin = vin,
                    isCachedResponse = isCached
                )
                if (snapshot != null) {
                    processWorkChargeSnapshot(
                        snapshot = snapshot,
                        targetCar = car,
                        nowMs = nowMs,
                        recordSyncStatus = false
                    )
                }
            }
        }
    )

    private val smartcarOdometerSyncer = SmartcarOdometerSyncer(
        preferencesManager = preferencesManager,
        carDao = carDao,
        odometerLogDao = odometerLogDao,
        client = client,
        smartcarAuthClient = smartcarAuthClient,
        stateSink = stateSink,
        onLiveDcProbe = { vehicleId, userId, token, car, nowMs ->
            val dao = chargingSessionDao ?: return@SmartcarOdometerSyncer
            if (preferencesManager.getSmartcarDcSyncEnabled() &&
                preferencesManager.getSmartcarDcCapability() == PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY
            ) {
                probeSmartcarLiveDcSnapshot(
                    vehicleId = vehicleId,
                    userId = userId,
                    initialToken = token,
                    targetCar = car,
                    dao = dao,
                    nowMs = nowMs,
                    recordStatusOnIdle = false
                )
            }
        }
    )

    companion object {
        private const val TAG = "OdometerSyncService"

        internal suspend fun recordOdometerReading(
            carDao: CarDao,
            odometerLogDao: OdometerLogDao?,
            carId: String,
            odometer: Int,
            provider: String,
            syncTime: Long
        ) = TelemetryJsonUtils.recordOdometerReading(carDao, odometerLogDao, carId, odometer, provider, syncTime)

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

        // Tesla /api/1/dx/charging/history rejects any single request whose span exceeds 1 year
        // ("Date range cannot exceed 1 year"). Cap each window at 364 full days (8,736 hours) so
        // leap years, DST transitions, and timezone boundaries can never exceed Tesla's 1-year cap.
        internal const val TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS = 364
        internal const val DEFAULT_SUPERCHARGER_INITIAL_LOOKBACK_DAYS = 364
        internal const val SUPERCHARGER_INCREMENTAL_OVERLAP_DAYS = 1

        private val json get() = telemetryJson

        internal fun parseJwtClaims(token: String): JsonObject? =
            TelemetryJsonUtils.parseJwtClaims(token)

        internal fun extractJwtExpiryMs(token: String): Long? =
            TelemetryJsonUtils.extractJwtExpiryMs(token)

        internal fun extractJwtScopes(token: String): Set<String>? =
            TelemetryJsonUtils.extractJwtScopes(token)

        fun hasTeslaLocationScope(token: String?): Boolean =
            TelemetryJsonUtils.hasTeslaLocationScope(token)

        fun hasTeslaChargingHistoryScope(token: String?): Boolean =
            TelemetryJsonUtils.hasTeslaChargingHistoryScope(token)

        internal fun parseTeslaErrorDetail(rawBody: String): String? =
            TeslaAuthClient.parseTeslaErrorDetail(rawBody)

        internal fun isTeslaScopeError(httpCode: Int, errorDetail: String?): Boolean =
            TeslaAuthClient.isTeslaScopeError(httpCode, errorDetail)

        fun isCooldownActive(
            lastSyncMs: Long?,
            cooldownHours: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean = TelemetryJsonUtils.isCooldownActive(lastSyncMs, cooldownHours, nowMs)

        fun remainingCooldownHours(
            lastSyncMs: Long?,
            cooldownHours: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Int = TelemetryJsonUtils.remainingCooldownHours(lastSyncMs, cooldownHours, nowMs)

        fun formatRelativeTime(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String? =
            TelemetryJsonUtils.formatRelativeTime(timestampMs, nowMs)

        fun parseSmartcarOdometerMiles(jsonBody: String): Int? =
            SmartcarOdometerSyncer.parseSmartcarOdometerMiles(jsonBody)

        fun parseSmartcarV3Connection(jsonBody: String, preferredUserId: String? = null): Pair<String, String?>? =
            SmartcarAuthClient.parseSmartcarV3Connection(jsonBody, preferredUserId)

        fun parseSmartcarV3ApplicationId(jsonBody: String): String? =
            SmartcarAuthClient.parseSmartcarV3ApplicationId(jsonBody)

        fun parseSmartcarErrorDetail(jsonBody: String): String? =
            SmartcarAuthClient.parseSmartcarErrorDetail(jsonBody)

        fun formatSyncResultMessage(car: Car, result: OdometerSyncResult): String =
            TelemetryStatusFormatters.formatSyncResultMessage(car, result)

        fun formatDailyPollSummary(
            cars: List<Car>,
            results: List<OdometerSyncResult>,
            chargerResult: WallConnectorSyncResult? = null
        ): String = TelemetryStatusFormatters.formatDailyPollSummary(cars, results, chargerResult)

        fun formatWallConnectorSyncMessage(result: WallConnectorSyncResult): String =
            TelemetryStatusFormatters.formatWallConnectorSyncMessage(result)

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

        private fun parseFlexibleTimestampMs(raw: JsonElement?): Long? =
            TelemetryJsonUtils.parseFlexibleTimestampMs(raw)

        private fun parseFlexibleDurationSeconds(raw: JsonElement?): Long? =
            TelemetryJsonUtils.parseFlexibleDurationSeconds(raw)

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

        fun parseTeslaVehicleListState(jsonBody: String, targetVin: String?): Pair<String, String>? =
            TeslaOdometerSyncer.parseTeslaVehicleListState(jsonBody, targetVin)

        fun parseTeslaOdometerMiles(jsonBody: String): Int? =
            TeslaOdometerSyncer.parseTeslaOdometerMiles(jsonBody)

        fun parseTeslaWakeState(jsonBody: String): String? =
            TeslaAuthClient.parseTeslaWakeState(jsonBody)

        suspend fun waitForTeslaWakeUp(
            maxAttempts: Int = 6,
            delayMs: Long = 5_000L,
            pollWakeState: suspend () -> String?
        ): Boolean = TeslaAuthClient.waitForTeslaWakeUp(maxAttempts, delayMs, pollWakeState)

        /**
         * Sends at most ONE paid POST /wake_up request ($0.02), then polls vehicle connectivity
         * state (GET /api/1/vehicles/{vin}) until online instead of repeating POST /wake_up.
         */
        suspend fun wakeAndPollUntilOnline(
            maxStatePolls: Int = 6,
            delayMs: Long = 5_000L,
            sendWake: suspend () -> String?,
            pollState: suspend () -> String?
        ): Boolean = TeslaAuthClient.wakeAndPollUntilOnline(maxStatePolls, delayMs, sendWake, pollState)

        fun shouldRecordOdometerLog(
            latestLog: OdometerLogEntity?,
            newOdometer: Int,
            syncTimeMs: Long,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): Boolean = TelemetryJsonUtils.shouldRecordOdometerLog(latestLog, newOdometer, syncTimeMs, zoneId)

        fun resolveProviderForCar(car: Car): String? =
            TelemetryJsonUtils.resolveProviderForCar(car)

        fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
            TelemetryJsonUtils.haversineMeters(lat1, lng1, lat2, lng2)

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

        fun formatWorkChargingSyncMessage(result: WorkChargingSyncResult): String =
            TelemetryStatusFormatters.formatWorkChargingSyncMessage(result)

        internal fun buildSuperchargerDateWindows(
            endInstant: Instant,
            lookbackDays: Int,
            zoneId: ZoneId = ZoneId.systemDefault(),
            maxWindowDays: Int = TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS
        ): List<Pair<Instant, Instant>> {
            val safeMaxDays = maxWindowDays.coerceAtLeast(1)
            val clampedLookbackDays = lookbackDays.coerceIn(1, 730)
            val endTruncated = endInstant.truncatedTo(ChronoUnit.SECONDS)
            val maxWindowSeconds = safeMaxDays.toLong() * 86_400L

            if (clampedLookbackDays <= safeMaxDays) {
                val startCandidate = endTruncated.atZone(zoneId)
                    .minusDays(clampedLookbackDays.toLong())
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
                    .toInstant()
                val earliestSingleWindowStart = endTruncated.minusSeconds(maxWindowSeconds)
                val windowStart = if (startCandidate.isBefore(earliestSingleWindowStart)) {
                    earliestSingleWindowStart
                } else {
                    startCandidate
                }
                return listOf(windowStart to endTruncated)
            }

            val overallStart = endTruncated.minusSeconds(clampedLookbackDays.toLong() * 86_400L)
            return buildSuperchargerDateWindowsForRange(
                startInstant = overallStart,
                endInstant = endTruncated,
                nowInstant = endTruncated,
                maxWindowDays = safeMaxDays
            )
        }

        internal fun buildSuperchargerDateWindowsForRange(
            startInstant: Instant,
            endInstant: Instant,
            nowInstant: Instant = Instant.now(),
            maxWindowDays: Int = TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS
        ): List<Pair<Instant, Instant>> {
            val safeMaxDays = maxWindowDays.coerceAtLeast(1)
            val maxWindowSeconds = safeMaxDays.toLong() * 86_400L
            val nowTruncated = nowInstant.truncatedTo(ChronoUnit.SECONDS)
            val requestedEnd = endInstant.truncatedTo(ChronoUnit.SECONDS)
            val clampedEnd = if (requestedEnd.isAfter(nowTruncated)) nowTruncated else requestedEnd
            val requestedStart = startInstant.truncatedTo(ChronoUnit.SECONDS)
            val clampedStart = if (!requestedStart.isBefore(clampedEnd)) {
                clampedEnd.minusSeconds(3_600L)
            } else {
                requestedStart
            }

            val windows = mutableListOf<Pair<Instant, Instant>>()
            var cursorEnd = clampedEnd
            while (cursorEnd.isAfter(clampedStart)) {
                val candidateStart = cursorEnd.minusSeconds(maxWindowSeconds)
                val windowStart = if (candidateStart.isBefore(clampedStart)) clampedStart else candidateStart
                windows.add(windowStart to cursorEnd)
                cursorEnd = windowStart
            }
            return windows
        }

        internal fun resolveIncrementalSuperchargerWindows(
            existingSessions: List<ChargingSessionEntity>,
            nowInstant: Instant = Instant.now(),
            zoneId: ZoneId = ZoneId.systemDefault(),
            maxWindowDays: Int = TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS,
            overlapDays: Int = SUPERCHARGER_INCREMENTAL_OVERLAP_DAYS
        ): List<Pair<Instant, Instant>> {
            val safeMaxDays = maxWindowDays.coerceAtLeast(1)
            val maxWindowSeconds = safeMaxDays.toLong() * 86_400L
            val nowTruncated = nowInstant.truncatedTo(ChronoUnit.SECONDS)
            val latestSyncedMs = existingSessions
                .asSequence()
                .filter { it.uploadBatchId == ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID }
                .maxOfOrNull { maxOf(it.startDateMs, it.endDateMs) }

            if (latestSyncedMs == null || latestSyncedMs <= 0L) {
                return buildSuperchargerDateWindows(
                    endInstant = nowTruncated,
                    lookbackDays = DEFAULT_SUPERCHARGER_INITIAL_LOOKBACK_DAYS,
                    zoneId = zoneId,
                    maxWindowDays = safeMaxDays
                )
            }

            val bufferSeconds = overlapDays.coerceAtLeast(0).toLong() * 86_400L
            val latestInstant = Instant.ofEpochMilli(latestSyncedMs).truncatedTo(ChronoUnit.SECONDS)
            val earliestAllowedStart = nowTruncated.minusSeconds(maxWindowSeconds)
            val rawStart = latestInstant.minusSeconds(bufferSeconds)
            val clampedStart = when {
                rawStart.isBefore(earliestAllowedStart) -> earliestAllowedStart
                !rawStart.isBefore(nowTruncated) -> nowTruncated.minusSeconds(maxOf(bufferSeconds, 3_600L))
                else -> rawStart
            }
            return buildSuperchargerDateWindowsForRange(
                startInstant = clampedStart,
                endInstant = nowTruncated,
                nowInstant = nowTruncated,
                maxWindowDays = safeMaxDays
            )
        }

        fun parseTeslaSuperchargerHistory(rawJson: String): Pair<List<TeslaSuperchargerSession>, Boolean> {
            return try {
                val rootElem = json.parseToJsonElement(rawJson)
                val root = rootElem as? JsonObject
                val responseElem = root?.get("response")
                val containerObj = (responseElem as? JsonObject) ?: root
                val dataArr = (rootElem as? JsonArray)
                    ?: (responseElem as? JsonArray)
                    ?: (containerObj?.get("data") as? JsonArray)
                    ?: return Pair(emptyList(), false)

                val explicitHasMore = containerObj?.get("hasMore").asBooleanOrNull()
                    ?: containerObj?.get("has_more").asBooleanOrNull()
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

        fun formatSuperchargerSyncMessage(result: SuperchargerSyncResult): String =
            TelemetryStatusFormatters.formatSuperchargerSyncMessage(result)

        fun parseSmartcarV3ErrorCode(rawBody: String): Pair<String?, String?> =
            SmartcarAuthClient.parseSmartcarV3ErrorCode(rawBody)

        fun isSmartcarNotCapableError(httpCode: Int, rawBody: String): Boolean =
            SmartcarAuthClient.isSmartcarNotCapableError(httpCode, rawBody)

        fun isSmartcarPermissionError(httpCode: Int, rawBody: String): Boolean =
            SmartcarAuthClient.isSmartcarPermissionError(httpCode, rawBody)

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

        fun formatSmartcarDcSyncMessage(result: SmartcarDcSyncResult): String =
            TelemetryStatusFormatters.formatSmartcarDcSyncMessage(result)

        fun buildSmartcarAuthUrl(clientOrAppId: String): String =
            SmartcarAuthClient.buildSmartcarAuthUrl(clientOrAppId)

        fun buildTeslaAuthUrl(
            clientId: String,
            includeLocationScope: Boolean = false,
            includeChargingHistoryScope: Boolean = false
        ): String = TeslaAuthClient.buildTeslaAuthUrl(
            clientId = clientId,
            includeLocationScope = includeLocationScope,
            includeChargingHistoryScope = includeChargingHistoryScope
        )
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

    suspend fun resolveSmartcarAuthUrl(clientOrAppId: String): String =
        smartcarAuthClient.resolveSmartcarAuthUrl(clientOrAppId)

    suspend fun refreshSmartcarConnectionStatus(): Boolean =
        smartcarAuthClient.refreshSmartcarConnectionStatus()

    suspend fun handleOAuthCallback(uri: Uri): Boolean =
        smartcarAuthClient.handleSmartcarCallback(uri) ?: run {
            val code = uri.getQueryParameter("code") ?: return@run false
            val path = uri.path.orEmpty()
            if (path.contains("tesla", ignoreCase = true)) {
                teslaAuthClient.exchangeTeslaCode(code)
            } else {
                false
            }
        }

    suspend fun getEffectiveSmartcarCredentials(): Pair<String?, String?> =
        smartcarAuthClient.getEffectiveSmartcarCredentials()

    suspend fun getEffectiveTeslaCredentials(): Pair<String?, String?> =
        teslaAuthClient.getEffectiveTeslaCredentials()

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

    private suspend fun getValidTeslaAccessToken(forceRefresh: Boolean = false): String? =
        teslaAuthClient.getValidTeslaAccessToken(forceRefresh)

    private suspend fun executeTeslaGetWithRefresh(url: String, initialToken: String): Triple<Int, String, String> =
        teslaAuthClient.executeTeslaGetWithRefresh(url, initialToken)

    private suspend fun wakeTeslaVehicleOnce(
        vin: String,
        initialToken: String,
        maxStatePolls: Int = 6
    ): Pair<Boolean, String> = teslaAuthClient.wakeTeslaVehicleOnce(vin, initialToken, maxStatePolls)

    private suspend fun syncTeslaVehicle(
        car: Car,
        isManual: Boolean = false,
        forceWake: Boolean
    ): OdometerSyncResult = teslaOdometerSyncer.syncTeslaVehicle(car, isManual, forceWake)

    private suspend fun getValidSmartcarAccessToken(forceRefresh: Boolean = false): String? =
        smartcarAuthClient.getValidSmartcarAccessToken(forceRefresh)

    private suspend fun syncSmartcarVehicle(car: Car): OdometerSyncResult =
        smartcarOdometerSyncer.syncSmartcarVehicle(car)

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
        lookbackDays: Int? = null,
        startInstant: Instant? = null,
        endInstant: Instant? = null
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
            val zoneId = ZoneId.systemDefault()
            val nowInstant = Instant.now()
            val windows = when {
                startInstant != null -> buildSuperchargerDateWindowsForRange(
                    startInstant = startInstant,
                    endInstant = endInstant ?: nowInstant,
                    nowInstant = nowInstant
                )
                lookbackDays != null -> buildSuperchargerDateWindows(
                    endInstant = nowInstant,
                    lookbackDays = lookbackDays,
                    zoneId = zoneId
                )
                else -> resolveIncrementalSuperchargerWindows(
                    existingSessions = existingSessions,
                    nowInstant = nowInstant,
                    zoneId = zoneId
                )
            }

            Log.i(
                TAG,
                "Supercharger sync: ${windows.size} window(s) (${windows.last().first} -> ${windows.first().second})"
            )

            val pageSize = 50
            val maxPagesPerWindow = 5
            val allRaw = mutableListOf<TeslaSuperchargerSession>()

            for ((windowStart, windowEnd) in windows) {
                val encodedStart = URLEncoder.encode(DateTimeFormatter.ISO_INSTANT.format(windowStart), "UTF-8")
                val encodedEnd = URLEncoder.encode(DateTimeFormatter.ISO_INSTANT.format(windowEnd), "UTF-8")

                for (pageNo in 1..maxPagesPerWindow) {
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
                            val err = SuperchargerSyncResult.Error(detail?.let { "Tesla (403: $it)" } ?: "Tesla (403)")
                            val msg = formatSuperchargerSyncMessage(err)
                            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                            _lastSyncMessage.value = msg
                            return@withContext err
                        }
                        code !in 200..299 -> {
                            val detail = parseTeslaErrorDetail(bodyStr)
                            val err = SuperchargerSyncResult.Error(detail?.let { "Tesla ($code: $it)" } ?: "Tesla ($code)")
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
            }

            val dedupedRaw = allRaw.distinctBy { it.sessionId }.sortedBy { it.startDateMs }
            val nowMs = System.currentTimeMillis()
            val newEntities = buildSuperchargerSessionEntities(
                rawSessions = dedupedRaw,
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
                totalSessionsScanned = dedupedRaw.size
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
    ): Triple<Int, String, String> = smartcarAuthClient.executeSmartcarV3SignalGet(
        vehicleId = vehicleId,
        signalCode = signalCode,
        userId = userId,
        initialToken = initialToken
    )

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
