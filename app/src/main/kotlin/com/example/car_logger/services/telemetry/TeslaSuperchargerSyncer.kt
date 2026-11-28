package com.example.car_logger.services.telemetry

import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SuperchargerSyncResult
import com.example.car_logger.services.TeslaSuperchargerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class TeslaSuperchargerSyncer(
    private val preferencesManager: PreferencesManager,
    private val chargingSessionDao: ChargingSessionDao?,
    private val teslaAuthClient: TeslaAuthClient,
    private val stateSink: TelemetrySyncStateSink
) {
    companion object {
        private const val TAG = "OdometerSyncService"
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
        val initialToken = teslaAuthClient.getValidTeslaAccessToken()
        if (initialToken.isNullOrBlank()) {
            val expiredMsg = "Tesla access token expired — reconnect in Settings"
            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), expiredMsg)
            stateSink.setMessage(expiredMsg)
            return@withContext SuperchargerSyncResult.Error(expiredMsg)
        }
        var token: String = initialToken

        val jwtScopes = TelemetryJsonUtils.extractJwtScopes(token)
        if (jwtScopes != null && !jwtScopes.contains(OdometerSyncService.TESLA_SCOPE_VEHICLE_CHARGING_CMDS)) {
            val missing = SuperchargerSyncResult.MissingScope(
                "Requires Vehicle Charging Management (vehicle_charging_cmds) scope — enable in Tesla Developer Portal & reconnect"
            )
            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), missing.message)
            return@withContext missing
        }

        stateSink.setSyncing(true)
        try {
            val existingSessions = dao.getAllSessionsList()
            val zoneId = ZoneId.systemDefault()
            val nowInstant = Instant.now()
            val windows = when {
                startInstant != null -> TeslaSuperchargerParser.buildSuperchargerDateWindowsForRange(
                    startInstant = startInstant,
                    endInstant = endInstant ?: nowInstant,
                    nowInstant = nowInstant
                )
                lookbackDays != null -> TeslaSuperchargerParser.buildSuperchargerDateWindows(
                    endInstant = nowInstant,
                    lookbackDays = lookbackDays,
                    zoneId = zoneId
                )
                else -> TeslaSuperchargerParser.resolveIncrementalSuperchargerWindows(
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
                    val url = "${OdometerSyncService.TESLA_API_BASE}/api/1/dx/charging/history" +
                        "?startTime=$encodedStart&endTime=$encodedEnd&pageNo=$pageNo&pageSize=$pageSize"
                    val (code, bodyStr, updatedToken) = try {
                        teslaAuthClient.executeTeslaGetWithRefresh(url, token)
                    } catch (e: Exception) {
                        val err = SuperchargerSyncResult.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
                        val msg = TelemetryStatusFormatters.formatSuperchargerSyncMessage(err)
                        preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                        stateSink.setMessage(msg)
                        return@withContext err
                    }
                    token = updatedToken

                    when {
                        code == 401 -> {
                            val detail = TeslaAuthClient.parseTeslaErrorDetail(bodyStr)
                            val msg = detail?.let { "Tesla authorization failed (401: $it) — reconnect in Settings" }
                                ?: "Tesla token expired (401) — reconnect in Settings"
                            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                            stateSink.setMessage(msg)
                            return@withContext SuperchargerSyncResult.Error(msg)
                        }
                        code == 403 -> {
                            val detail = TeslaAuthClient.parseTeslaErrorDetail(bodyStr)
                            if (TeslaAuthClient.isTeslaScopeError(403, detail)) {
                                val missing = SuperchargerSyncResult.MissingScope(
                                    "Requires Vehicle Charging Management (vehicle_charging_cmds) scope — enable in Tesla Developer Portal & reconnect"
                                )
                                preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), missing.message)
                                stateSink.setMessage(missing.message)
                                return@withContext missing
                            }
                            val err = SuperchargerSyncResult.Error(detail?.let { "Tesla (403: $it)" } ?: "Tesla (403)")
                            val msg = TelemetryStatusFormatters.formatSuperchargerSyncMessage(err)
                            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                            stateSink.setMessage(msg)
                            return@withContext err
                        }
                        code !in 200..299 -> {
                            val detail = TeslaAuthClient.parseTeslaErrorDetail(bodyStr)
                            val err = SuperchargerSyncResult.Error(detail?.let { "Tesla ($code: $it)" } ?: "Tesla ($code)")
                            val msg = TelemetryStatusFormatters.formatSuperchargerSyncMessage(err)
                            preferencesManager.recordSuperchargerSyncResult(System.currentTimeMillis(), msg)
                            stateSink.setMessage(msg)
                            return@withContext err
                        }
                        else -> {
                            val (pageSessions, hasMore) = TeslaSuperchargerParser.parseTeslaSuperchargerHistory(bodyStr)
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
            val newEntities = TeslaSuperchargerParser.buildSuperchargerSessionEntities(
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
            val statusMsg = TelemetryStatusFormatters.formatSuperchargerSyncMessage(result)
            preferencesManager.recordSuperchargerSyncResult(nowMs, statusMsg)
            stateSink.setMessage(statusMsg)
            result
        } finally {
            stateSink.setSyncing(false)
        }
    }
}
