package com.example.car_logger.services.telemetry

import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.TeslaWallConnectorChargeSession
import com.example.car_logger.services.TeslaWallConnectorDevice
import com.example.car_logger.services.WallConnectorSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal class WallConnectorSyncer(
    private val preferencesManager: PreferencesManager,
    private val chargingSessionDao: ChargingSessionDao?,
    private val teslaAuthClient: TeslaAuthClient,
    private val stateSink: TelemetrySyncStateSink
) {
    companion object {
        private const val TAG = "OdometerSyncService"
    }

    suspend fun syncWallConnectorCharging(
        cars: List<Car>,
        lookbackDays: Int? = null
    ): WallConnectorSyncResult = withContext(Dispatchers.IO) {
        val dao = chargingSessionDao ?: return@withContext WallConnectorSyncResult.Error("Charging database unavailable")
        val (storedAccess, _, _) = preferencesManager.getTeslaTokens()
        if (storedAccess.isNullOrBlank()) {
            return@withContext WallConnectorSyncResult.NotConfigured
        }
        val initialToken = teslaAuthClient.getValidTeslaAccessToken()
        if (initialToken.isNullOrBlank()) {
            val expiredMsg = "Tesla access token expired — reconnect or paste a new token in Settings"
            preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), expiredMsg)
            stateSink.setMessage(expiredMsg)
            return@withContext WallConnectorSyncResult.Error(expiredMsg)
        }
        var token: String = initialToken

        stateSink.setSyncing(true)
        try {
            val (productsCode, productsBody, refreshedAfterProducts) = try {
                teslaAuthClient.executeTeslaGetWithRefresh(
                    "${OdometerSyncService.TESLA_API_BASE}/api/1/products",
                    token
                )
            } catch (e: Exception) {
                val res = WallConnectorSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
                return@withContext recordErrorStatus(res)
            }
            token = refreshedAfterProducts

            when (productsCode) {
                401 -> {
                    val detail = TeslaAuthClient.parseTeslaErrorDetail(productsBody)
                    val msg = detail?.let { "Tesla authorization failed (401: $it) — reconnect or update token in Settings" }
                        ?: "Tesla token expired or unauthorized (401) — reconnect or update token in Settings"
                    preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), msg)
                    stateSink.setMessage(msg)
                    return@withContext WallConnectorSyncResult.Error(msg)
                }
                403 -> {
                    val detail = TeslaAuthClient.parseTeslaErrorDetail(productsBody)
                    if (TeslaAuthClient.isTeslaScopeError(403, detail)) {
                        val missingScopeRes = WallConnectorSyncResult.MissingScope(
                            "Tesla token is missing energy_device_data scope — tap Reconnect in Settings"
                        )
                        preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), missingScopeRes.message)
                        stateSink.setMessage(missingScopeRes.message)
                        return@withContext missingScopeRes
                    }
                    val errMsg = detail?.let { "Tesla API error (403: $it)" } ?: "Tesla products API (403)"
                    return@withContext recordErrorStatus(WallConnectorSyncResult.Error(errMsg))
                }
                !in 200..299 -> {
                    val detail = TeslaAuthClient.parseTeslaErrorDetail(productsBody)
                    val errMsg = detail?.let { "Tesla products API ($productsCode: $it)" }
                        ?: "Tesla products API ($productsCode)"
                    return@withContext recordErrorStatus(WallConnectorSyncResult.Error(errMsg))
                }
            }

            val discoveredDevices = WallConnectorDeviceParser.parseTeslaEnergySites(productsBody).toMutableList()
            val uniqueSiteIds = discoveredDevices.map { it.energySiteId }.distinct()

            if (uniqueSiteIds.isEmpty()) {
                val jwtScopes = TelemetryJsonUtils.extractJwtScopes(token)
                val nowMs = System.currentTimeMillis()
                if (jwtScopes != null && !jwtScopes.contains(OdometerSyncService.TESLA_SCOPE_ENERGY_DEVICE_DATA)) {
                    val missingScopeRes = WallConnectorSyncResult.MissingScope(
                        "Tesla token is missing energy_device_data scope — tap Reconnect in Settings"
                    )
                    preferencesManager.recordChargerSyncResult(nowMs, missingScopeRes.message)
                    stateSink.setMessage(missingScopeRes.message)
                    return@withContext missingScopeRes
                }
                val noDevicesRes = WallConnectorSyncResult.NoDevices()
                preferencesManager.recordChargerSyncResult(nowMs, noDevicesRes.message)
                stateSink.setMessage(noDevicesRes.message)
                return@withContext noDevicesRes
            }

            val (enrichedDevices, afterEnrichToken) = enrichWallConnectorDevices(discoveredDevices, uniqueSiteIds, token)
            token = afterEnrichToken

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
                .withHour(0).withMinute(0).withSecond(0).withNano(0)
            val earliestAllowedStart = Instant.ofEpochMilli(
                endZdt.toInstant().toEpochMilli() - 30L * 86_400_000L
            ).atZone(zoneId).truncatedTo(ChronoUnit.SECONDS)
            val startZdt = if (startCandidate.isBefore(earliestAllowedStart)) earliestAllowedStart else startCandidate

            val startFormatted = startZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val endFormatted = endZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val encodedStart = URLEncoder.encode(startFormatted, "UTF-8")
            val encodedEnd = URLEncoder.encode(endFormatted, "UTF-8")
            val encodedStartDate = URLEncoder.encode(startZdt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE), "UTF-8")
            val encodedEndDate = URLEncoder.encode(endZdt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE), "UTF-8")
            val encodedTz = URLEncoder.encode(zoneId.id, "UTF-8")

            Log.i(
                TAG,
                "Wall Connector sync: sites=${uniqueSiteIds.size}, devices=${enrichedDevices.size}, " +
                    "lookback=${effectiveLookbackDays}d ($startFormatted -> $endFormatted)"
            )

            val allRawSessions = mutableListOf<TeslaWallConnectorChargeSession>()
            val siteErrors = mutableListOf<String>()
            for (siteId in uniqueSiteIds) {
                val primaryUrl = "${OdometerSyncService.TESLA_API_BASE}/api/1/energy_sites/$siteId/telemetry_history" +
                    "?kind=charge&start_date=$encodedStart&end_date=$encodedEnd&time_zone=$encodedTz"
                val fallbackUrl = "${OdometerSyncService.TESLA_API_BASE}/api/1/energy_sites/$siteId/telemetry_history" +
                    "?kind=charge&start_date=$encodedStartDate&end_date=$encodedEndDate&time_zone=$encodedTz"

                try {
                    var (code, bodyStr, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(primaryUrl, token)
                    token = updatedToken
                    if (code == 400) {
                        val retry = teslaAuthClient.executeTeslaGetWithRefresh(fallbackUrl, token)
                        code = retry.first
                        bodyStr = retry.second
                        token = retry.third
                    }

                    when {
                        code == 401 -> {
                            val detail = TeslaAuthClient.parseTeslaErrorDetail(bodyStr)
                            val msg = detail?.let { "Tesla authorization failed (401: $it) — reconnect or update token in Settings" }
                                ?: "Tesla token expired or unauthorized (401) — reconnect or update token in Settings"
                            preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), msg)
                            stateSink.setMessage(msg)
                            return@withContext WallConnectorSyncResult.Error(msg)
                        }
                        code == 403 -> {
                            val detail = TeslaAuthClient.parseTeslaErrorDetail(bodyStr)
                            if (TeslaAuthClient.isTeslaScopeError(403, detail)) {
                                val missingScopeRes = WallConnectorSyncResult.MissingScope(
                                    "Tesla token is missing energy_device_data scope — tap Reconnect in Settings"
                                )
                                preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), missingScopeRes.message)
                                stateSink.setMessage(missingScopeRes.message)
                                return@withContext missingScopeRes
                            }
                            siteErrors.add(detail?.let { "Site $siteId (403: $it)" } ?: "Site $siteId (403)")
                        }
                        code in 200..299 -> {
                            val siteSessions = WallConnectorParser.parseTeslaWallConnectorChargeHistory(bodyStr, siteId)
                            if (siteSessions.isEmpty()) {
                                val keysSummary = runCatching {
                                    val rootObj = telemetryJson.parseToJsonElement(bodyStr) as? JsonObject
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
                            val detail = TeslaAuthClient.parseTeslaErrorDetail(bodyStr)
                            siteErrors.add(detail?.let { "Site $siteId ($code: $it)" } ?: "Site $siteId ($code)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Wall Connector telemetry_history fetch failed for $siteId: ${e.message}")
                    siteErrors.add("Site $siteId (${e.message ?: e.javaClass.simpleName})")
                }
            }

            if (allRawSessions.isEmpty() && siteErrors.isNotEmpty()) {
                return@withContext recordErrorStatus(WallConnectorSyncResult.Error(siteErrors.first()))
            }

            val existingIds = existingSessions.map { it.id }.toSet()
            val existingFingerprints = existingSessions.map { it.fingerprint }.toSet()
            val (gen3OverrideCarId, universalOverrideCarId) = preferencesManager.getWallConnectorCarMapping()
            val nowMs = System.currentTimeMillis()

            val newEntities = WallConnectorParser.buildWallConnectorSessionEntities(
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

            val result = WallConnectorSyncResult.Synced(
                insertedCount = newEntities.size,
                totalKwhAdded = newEntities.sumOf { it.kwh },
                totalSessionsScanned = allRawSessions.size
            )
            val statusMsg = TelemetryStatusFormatters.formatWallConnectorSyncMessage(result)
            preferencesManager.recordChargerSyncResult(nowMs, statusMsg)
            stateSink.setMessage(statusMsg)
            result
        } finally {
            stateSink.setSyncing(false)
        }
    }

    private suspend fun enrichWallConnectorDevices(
        discoveredDevices: List<TeslaWallConnectorDevice>,
        uniqueSiteIds: List<String>,
        initialToken: String
    ): Pair<List<TeslaWallConnectorDevice>, String> {
        var token = initialToken
        val enrichedDevices = mutableListOf<TeslaWallConnectorDevice>()
        for (siteId in uniqueSiteIds) {
            val siteDevices = discoveredDevices.filter { it.energySiteId == siteId }
            val needsSiteInfo = siteDevices.all { it.din.isNullOrBlank() && it.partNumber.isNullOrBlank() }
            if (needsSiteInfo) {
                val parsedInfo = try {
                    val (infoCode, infoBody, updatedToken) = teslaAuthClient.executeTeslaGetWithRefresh(
                        "${OdometerSyncService.TESLA_API_BASE}/api/1/energy_sites/$siteId/site_info",
                        token
                    )
                    token = updatedToken
                    if (infoCode in 200..299) {
                        WallConnectorDeviceParser.parseTeslaSiteInfoWallConnectors(
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
        return Pair(enrichedDevices, token)
    }

    private suspend fun recordErrorStatus(res: WallConnectorSyncResult): WallConnectorSyncResult {
        val statusMsg = TelemetryStatusFormatters.formatWallConnectorSyncMessage(res)
        preferencesManager.recordChargerSyncResult(System.currentTimeMillis(), statusMsg)
        stateSink.setMessage(statusMsg)
        return res
    }
}
