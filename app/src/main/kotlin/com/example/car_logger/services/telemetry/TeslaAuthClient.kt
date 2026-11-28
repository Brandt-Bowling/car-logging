package com.example.car_logger.services.telemetry

import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.services.OdometerSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale

internal class TeslaAuthClient(
    private val preferencesManager: PreferencesManager,
    private val client: OkHttpClient,
    private val stateSink: TelemetrySyncStateSink,
    private val wakePollDelayMsProvider: () -> Long = { 5_000L }
) {

    suspend fun getEffectiveTeslaCredentials(): Pair<String?, String?> {
        val (savedId, savedSecret) = preferencesManager.getTeslaCredentials()
        val id = savedId?.takeIf { it.isNotBlank() } ?: OdometerSyncService.DEFAULT_TESLA_CLIENT_ID.takeIf { it.isNotBlank() }
        val secret = savedSecret?.takeIf { it.isNotBlank() } ?: OdometerSyncService.DEFAULT_TESLA_CLIENT_SECRET.takeIf { it.isNotBlank() }
        return Pair(id, secret)
    }

    suspend fun exchangeTeslaCode(code: String): Boolean {
        val (clientId, clientSecret) = getEffectiveTeslaCredentials()
        if (clientId.isNullOrBlank()) {
            stateSink.setMessage("Tesla Client ID not configured")
            return false
        }

        val formBuilder = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code)
            .add("redirect_uri", OdometerSyncService.TESLA_REDIRECT_URI)
            .add("audience", OdometerSyncService.TESLA_API_BASE)
        if (!clientSecret.isNullOrBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(OdometerSyncService.TESLA_AUTH_TOKEN_URL)
            .post(formBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = parseTeslaErrorDetail(rawBody)
                    val msg = detail?.let { "Tesla connect failed (${resp.code}: $it)" }
                        ?: "Tesla connect failed (${resp.code})"
                    stateSink.setMessage(msg)
                    return false
                }
                val obj = telemetryJson.parseToJsonElement(rawBody) as? JsonObject
                if (obj == null) {
                    stateSink.setMessage("Tesla connect failed: invalid token response")
                    return false
                }
                val access = obj["access_token"].asStringOrNull()
                if (access == null) {
                    stateSink.setMessage("Tesla connect failed: missing access_token")
                    return false
                }
                val refresh = obj["refresh_token"].asStringOrNull()
                val expires = obj["expires_in"].asLongOrNull() ?: 28800L
                preferencesManager.saveTeslaTokens(access, refresh, expires)
                stateSink.setMessage("Tesla connected")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tesla code exchange failed", e)
            stateSink.setMessage("Tesla connect failed: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    suspend fun getValidTeslaAccessToken(forceRefresh: Boolean = false): String? {
        val (access, refresh, expiry) = preferencesManager.getTeslaTokens()
        if (access.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        val effectiveExpiryMs = if (expiry > 0L) expiry else (TelemetryJsonUtils.extractJwtExpiryMs(access) ?: 0L)
        if (!forceRefresh && (effectiveExpiryMs == 0L || now < effectiveExpiryMs - 60_000L)) {
            return access
        }
        val fallbackIfUnexpired = access.takeIf { !forceRefresh && (effectiveExpiryMs == 0L || now < effectiveExpiryMs) }
        val (clientId, clientSecret) = getEffectiveTeslaCredentials()
        if (clientId.isNullOrBlank() || refresh.isNullOrBlank()) {
            return fallbackIfUnexpired
        }

        val formBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refresh)
        if (!clientSecret.isNullOrBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(OdometerSyncService.TESLA_AUTH_TOKEN_URL)
            .post(formBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return fallbackIfUnexpired
                val obj = telemetryJson.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
                    ?: return fallbackIfUnexpired
                val newAccess = obj["access_token"].asStringOrNull() ?: return fallbackIfUnexpired
                val newRefresh = obj["refresh_token"].asStringOrNull() ?: refresh
                val expires = obj["expires_in"].asLongOrNull() ?: 28800L
                preferencesManager.saveTeslaTokens(newAccess, newRefresh, expires)
                newAccess
            }
        } catch (_: Exception) {
            fallbackIfUnexpired
        }
    }

    suspend fun executeTeslaGetWithRefresh(url: String, initialToken: String): Triple<Int, String, String> =
        executeWithRefresh(initialToken) { bearer ->
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $bearer")
                .build()
            client.newCall(req).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

    suspend fun executeTeslaPostWithRefresh(
        url: String,
        jsonPayload: String,
        initialToken: String
    ): Triple<Int, String, String> = executeWithRefresh(initialToken) { bearer ->
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearer")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            Pair(resp.code, resp.body?.string().orEmpty())
        }
    }

    private suspend inline fun executeWithRefresh(
        initialToken: String,
        crossinline block: (bearer: String) -> Pair<Int, String>
    ): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        var activeToken = initialToken
        var (code, body) = block(activeToken)
        if (code == 401) {
            val refreshed = getValidTeslaAccessToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank() && refreshed != activeToken) {
                activeToken = refreshed
                val retry = block(activeToken)
                code = retry.first
                body = retry.second
            }
        }
        Triple(code, body, activeToken)
    }

    suspend fun wakeTeslaVehicleOnce(
        vin: String,
        initialToken: String,
        maxStatePolls: Int = 6
    ): Pair<Boolean, String> {
        var activeToken = initialToken
        val online = wakeAndPollUntilOnline(
            maxStatePolls = maxStatePolls,
            delayMs = wakePollDelayMsProvider(),
            sendWake = {
                val (_, wakeBody, updated) = executeTeslaPostWithRefresh(
                    url = "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin/wake_up",
                    jsonPayload = "{}",
                    initialToken = activeToken
                )
                activeToken = updated
                parseTeslaWakeState(wakeBody)
            },
            pollState = {
                val (stateCode, stateBody, updated) = executeTeslaGetWithRefresh(
                    url = "${OdometerSyncService.TESLA_API_BASE}/api/1/vehicles/$vin",
                    initialToken = activeToken
                )
                activeToken = updated
                if (stateCode in 200..299) parseTeslaWakeState(stateBody) else null
            }
        )
        return Pair(online, activeToken)
    }

    companion object {
        private const val TAG = "TeslaAuthClient"

        fun parseTeslaErrorDetail(rawBody: String): String? {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return null
            return try {
                val root = telemetryJson.parseToJsonElement(trimmed) as? JsonObject ?: return trimmed.take(160)
                val err = root["error"].asStringOrNull()
                val desc = root["error_description"].asStringOrNull()
                val msg = root["message"].asStringOrNull()
                when {
                    desc != null && err != null && !desc.equals(err, ignoreCase = true) -> "$err: $desc"
                    desc != null -> desc
                    err != null -> err
                    msg != null -> msg
                    else -> null
                }
            } catch (_: Exception) {
                trimmed.take(160)
            }
        }

        fun isTeslaScopeError(httpCode: Int, errorDetail: String?): Boolean {
            if (httpCode != 403) return false
            if (errorDetail.isNullOrBlank()) return true
            val lower = errorDetail.lowercase(Locale.US)
            return lower.contains("scope") ||
                lower.contains("consent") ||
                lower.contains("permission") ||
                lower.contains("forbidden") ||
                lower.contains("energy_device_data") ||
                lower.contains("vehicle_location") ||
                lower.contains("vehicle_charging_cmds") ||
                lower.contains("unauthorized") ||
                lower.contains("insufficient")
        }

        fun buildTeslaAuthUrl(
            clientId: String,
            includeLocationScope: Boolean = false,
            includeChargingHistoryScope: Boolean = false
        ): String {
            val encodedClient = URLEncoder.encode(clientId.trim(), "UTF-8")
            val encodedRedirect = URLEncoder.encode(OdometerSyncService.TESLA_REDIRECT_URI, "UTF-8")
            val scopes = mutableListOf(
                "openid",
                "offline_access",
                "vehicle_device_data",
                OdometerSyncService.TESLA_SCOPE_ENERGY_DEVICE_DATA
            )
            if (includeLocationScope) scopes.add(OdometerSyncService.TESLA_SCOPE_VEHICLE_LOCATION)
            if (includeChargingHistoryScope) scopes.add(OdometerSyncService.TESLA_SCOPE_VEHICLE_CHARGING_CMDS)
            val scopeParam = scopes.joinToString("%20")
            return "https://auth.tesla.com/oauth2/v3/authorize" +
                "?response_type=code" +
                "&client_id=$encodedClient" +
                "&redirect_uri=$encodedRedirect" +
                "&scope=$scopeParam" +
                "&prompt_missing_scopes=true" +
                "&require_requested_scopes=true" +
                "&state=carlogger_tesla"
        }

        fun parseTeslaWakeState(jsonBody: String): String? {
            return try {
                val root = telemetryJson.parseToJsonElement(jsonBody) as? JsonObject ?: return null
                val response = (root["response"] as? JsonObject) ?: root
                response["state"].asStringOrNull()?.lowercase(Locale.US)
            } catch (_: Exception) {
                null
            }
        }

        suspend fun waitForTeslaWakeUp(
            maxAttempts: Int = 6,
            delayMs: Long = 5_000L,
            pollWakeState: suspend () -> String?
        ): Boolean {
            for (attempt in 1..maxAttempts) {
                val currentState = try {
                    pollWakeState()
                } catch (_: Exception) {
                    null
                }
                if (currentState == "online") return true
                if (delayMs > 0L) delay(delayMs)
            }
            return false
        }

        /**
         * Sends at most ONE paid POST /wake_up request ($0.02), then polls vehicle connectivity
         * state (GET /api/1/vehicles/{vin}) until online instead of repeating POST /wake_up.
         */
        suspend fun wakeAndPollUntilOnline(
            maxStatePolls: Int = 6,
            delayMs: Long = 5_000L,
            sendWake: suspend () -> String?,
            pollState: suspend () -> String?
        ): Boolean {
            val initialState = try {
                sendWake()
            } catch (_: Exception) {
                null
            }
            if (initialState == "online") return true
            for (attempt in 1 until maxStatePolls.coerceAtLeast(1)) {
                if (delayMs > 0L) delay(delayMs)
                val currentState = try {
                    pollState()
                } catch (_: Exception) {
                    null
                }
                if (currentState == "online") return true
            }
            return false
        }
    }
}
