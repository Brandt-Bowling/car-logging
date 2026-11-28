package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

internal class SmartcarOdometerSyncer(
    private val preferencesManager: PreferencesManager,
    private val carDao: CarDao,
    private val odometerLogDao: OdometerLogDao?,
    private val client: OkHttpClient,
    private val smartcarAuthClient: SmartcarAuthClient,
    private val stateSink: TelemetrySyncStateSink,
    private val onLiveDcProbe: suspend (vehicleId: String, userId: String?, token: String, car: Car, nowMs: Long) -> Unit
) {

    suspend fun syncSmartcarVehicle(car: Car): OdometerSyncResult {
        val (clientId, clientSecret) = smartcarAuthClient.getEffectiveSmartcarCredentials()
        val hasLinkedAccount = !preferencesManager.getSmartcarUserId().isNullOrBlank() ||
            !preferencesManager.getSmartcarVehicleId().isNullOrBlank()
        val token = smartcarAuthClient.getValidSmartcarAccessToken()
        if (token.isNullOrBlank()) {
            if (hasLinkedAccount && (clientId.isNullOrBlank() || clientSecret.isNullOrBlank())) {
                return OdometerSyncResult.Error(car.id, "Missing Smartcar Client ID/Secret in build or Settings")
            }
            return OdometerSyncResult.NotConfigured(car.id)
        }
        val isV3 = clientId?.startsWith("client_") == true ||
            !preferencesManager.getSmartcarUserId().isNullOrBlank()

        if (isV3) {
            return syncSmartcarV3Vehicle(car, token)
        }

        var vehicleId = preferencesManager.getSmartcarVehicleId()
        if (vehicleId.isNullOrBlank()) {
            val listReq = Request.Builder()
                .url("${OdometerSyncService.SMARTCAR_API_BASE}/vehicles")
                .addHeader("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(listReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return OdometerSyncResult.Error(car.id, "Smartcar (${resp.code})")
                    }
                    val root = telemetryJson.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val firstId = root["vehicles"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    if (firstId.isNullOrBlank()) {
                        return OdometerSyncResult.Error(car.id, "No Smartcar vehicle linked")
                    }
                    vehicleId = firstId
                    preferencesManager.setSmartcarVehicleId(firstId)
                }
            } catch (_: Exception) {
                return OdometerSyncResult.Error(car.id, "Network error")
            }
        }

        val odoReq = Request.Builder()
            .url("${OdometerSyncService.SMARTCAR_API_BASE}/vehicles/$vehicleId/odometer")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("sc-unit-system", "imperial")
            .build()

        return try {
            client.newCall(odoReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return OdometerSyncResult.Error(car.id, "Smartcar (${resp.code})")
                }
                val newOdo = parseSmartcarOdometerMiles(resp.body?.string().orEmpty())
                    ?: return OdometerSyncResult.Error(car.id, "Invalid odometer response")

                val finalOdo = maxOf(newOdo, car.odometer ?: 0)
                val now = System.currentTimeMillis()
                TelemetryJsonUtils.recordOdometerReading(
                    carDao = carDao,
                    odometerLogDao = odometerLogDao,
                    carId = car.id,
                    odometer = finalOdo,
                    provider = OdometerSyncService.PROVIDER_SMARTCAR,
                    syncTime = now
                )
                stateSink.setMessage("${car.model}: $finalOdo mi")
                OdometerSyncResult.Updated(car.id, finalOdo, OdometerSyncService.PROVIDER_SMARTCAR)
            }
        } catch (_: Exception) {
            OdometerSyncResult.Error(car.id, "Failed to read Smartcar odometer")
        }
    }

    private suspend fun syncSmartcarV3Vehicle(car: Car, initialToken: String): OdometerSyncResult {
        var token = initialToken

        fun executeConnectionsCall(bearerToken: String): Pair<Int, String> {
            val connReq = Request.Builder()
                .url("${OdometerSyncService.SMARTCAR_V3_API_BASE}/connections")
                .addHeader("Authorization", "Bearer $bearerToken")
                .build()
            return client.newCall(connReq).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        val (vehicleId, userId) = try {
            var (code, bodyStr) = executeConnectionsCall(token)
            if (code == 401) {
                val refreshed = smartcarAuthClient.getValidSmartcarAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank() && refreshed != token) {
                    token = refreshed
                    val retry = executeConnectionsCall(token)
                    code = retry.first
                    bodyStr = retry.second
                }
            }
            if (code !in 200..299) {
                val detail = SmartcarAuthClient.parseSmartcarErrorDetail(bodyStr)
                val msg = if (detail != null) "Smartcar v3 ($code: $detail)" else "Smartcar v3 ($code)"
                stateSink.setMessage(msg)
                return OdometerSyncResult.Error(car.id, msg)
            }
            val preferredUserId = preferencesManager.getSmartcarUserId()
            val parsed = SmartcarAuthClient.parseSmartcarV3Connection(bodyStr, preferredUserId)
            if (parsed == null) {
                preferencesManager.clearSmartcarVehicleConnection()
                val msg = "No vehicle linked in Smartcar — tap Connect in Settings"
                stateSink.setMessage(msg)
                return OdometerSyncResult.Error(car.id, msg)
            }
            parsed
        } catch (_: Exception) {
            val msg = "Network error contacting Smartcar"
            stateSink.setMessage(msg)
            return OdometerSyncResult.Error(car.id, msg)
        }

        preferencesManager.setSmartcarVehicleId(vehicleId, userId)

        fun executeOdometerSignalCall(bearerToken: String): Pair<Int, String> {
            val odoReqBuilder = Request.Builder()
                .url("${OdometerSyncService.SMARTCAR_V3_API_BASE}/vehicles/$vehicleId/signals/odometer-traveleddistance")
                .addHeader("Authorization", "Bearer $bearerToken")
                .addHeader("sc-unit-system", "imperial")
                .addHeader("Smartcar-Unit-System", "imperial")
            if (!userId.isNullOrBlank()) {
                odoReqBuilder.addHeader("sc-user-id", userId)
            }
            return client.newCall(odoReqBuilder.build()).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        return try {
            var (code, bodyStr) = executeOdometerSignalCall(token)
            if (code == 401) {
                val refreshed = smartcarAuthClient.getValidSmartcarAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank() && refreshed != token) {
                    token = refreshed
                    val retry = executeOdometerSignalCall(token)
                    code = retry.first
                    bodyStr = retry.second
                }
            }
            if (code !in 200..299) {
                val detail = SmartcarAuthClient.parseSmartcarErrorDetail(bodyStr)
                val msg = if (detail != null) "Smartcar ($code: $detail)" else "Smartcar signal ($code)"
                stateSink.setMessage(msg)
                return OdometerSyncResult.Error(car.id, msg)
            }

            val newOdo = parseSmartcarOdometerMiles(bodyStr)
            if (newOdo == null) {
                val detail = SmartcarAuthClient.parseSmartcarErrorDetail(bodyStr) ?: "Invalid v3 odometer response"
                stateSink.setMessage(detail)
                return OdometerSyncResult.Error(car.id, detail)
            }

            val finalOdo = maxOf(newOdo, car.odometer ?: 0)
            val now = System.currentTimeMillis()
            TelemetryJsonUtils.recordOdometerReading(
                carDao = carDao,
                odometerLogDao = odometerLogDao,
                carId = car.id,
                odometer = finalOdo,
                provider = OdometerSyncService.PROVIDER_SMARTCAR,
                syncTime = now
            )
            runCatching { onLiveDcProbe(vehicleId, userId, token, car, now) }
            stateSink.setMessage("${car.model}: $finalOdo mi")
            OdometerSyncResult.Updated(car.id, finalOdo, OdometerSyncService.PROVIDER_SMARTCAR)
        } catch (e: Exception) {
            val msg = "Failed to read Smartcar v3 odometer (${e.javaClass.simpleName})"
            stateSink.setMessage(msg)
            OdometerSyncResult.Error(car.id, msg)
        }
    }

    companion object {
        fun parseSmartcarOdometerMiles(jsonBody: String): Int? {
            return try {
                val root = telemetryJson.parseToJsonElement(jsonBody).jsonObject
                val dataObj = root["data"]?.jsonObject
                val attrsObj = dataObj?.get("attributes")?.jsonObject
                val bodyObj = attrsObj?.get("body")?.jsonObject
                    ?: dataObj?.get("body")?.jsonObject
                    ?: root["body"]?.jsonObject

                if (bodyObj != null) {
                    val rawVal = bodyObj["value"]?.jsonPrimitive?.doubleOrNull
                        ?: bodyObj["distance"]?.jsonPrimitive?.doubleOrNull
                    val unit = (bodyObj["unit"]?.jsonPrimitive?.content
                        ?: attrsObj?.get("unit")?.jsonPrimitive?.content
                        ?: dataObj?.get("unit")?.jsonPrimitive?.content
                        ?: root["unit"]?.jsonPrimitive?.content
                        ?: "").lowercase()
                    if (rawVal != null && rawVal >= 0) {
                        val miles = if (unit.startsWith("km") || unit.contains("kilometer") || unit == "metric") {
                            rawVal * 0.621371192
                        } else {
                            rawVal
                        }
                        return miles.roundToInt()
                    }
                }

                val distance = root["distance"]?.jsonPrimitive?.doubleOrNull
                    ?: root["value"]?.jsonPrimitive?.doubleOrNull
                    ?: dataObj?.get("distance")?.jsonPrimitive?.doubleOrNull
                    ?: attrsObj?.get("distance")?.jsonPrimitive?.doubleOrNull
                distance?.roundToInt()?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }
    }
}
