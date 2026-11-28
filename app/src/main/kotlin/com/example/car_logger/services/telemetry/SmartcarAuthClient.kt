package com.example.car_logger.services.telemetry

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.services.OdometerSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale

internal class SmartcarAuthClient(
    private val preferencesManager: PreferencesManager,
    private val client: OkHttpClient,
    private val stateSink: TelemetrySyncStateSink
) {

    suspend fun getEffectiveSmartcarCredentials(): Pair<String?, String?> {
        val (savedId, savedSecret) = preferencesManager.getSmartcarCredentials()
        val id = savedId?.takeIf { it.isNotBlank() } ?: OdometerSyncService.DEFAULT_SMARTCAR_CLIENT_ID.takeIf { it.isNotBlank() }
        val secret = savedSecret?.takeIf { it.isNotBlank() } ?: OdometerSyncService.DEFAULT_SMARTCAR_CLIENT_SECRET.takeIf { it.isNotBlank() }
        return Pair(id, secret)
    }

    suspend fun resolveSmartcarAuthUrl(clientOrAppId: String): String = withContext(Dispatchers.IO) {
        val trimmed = clientOrAppId.trim()
        if (trimmed.isNotBlank() && !trimmed.startsWith("client_")) {
            return@withContext buildSmartcarAuthUrl(trimmed)
        }
        val cachedAppId = preferencesManager.getSmartcarApplicationId()
        if (!cachedAppId.isNullOrBlank() && !cachedAppId.startsWith("client_")) {
            return@withContext buildSmartcarAuthUrl(cachedAppId)
        }
        val token = getValidSmartcarAccessToken()
        if (!token.isNullOrBlank()) {
            val appReq = Request.Builder()
                .url("${OdometerSyncService.SMARTCAR_V3_MANAGEMENT_API_BASE}/applications")
                .addHeader("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(appReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val resolvedId = parseSmartcarV3ApplicationId(resp.body?.string().orEmpty())
                        if (!resolvedId.isNullOrBlank()) {
                            preferencesManager.setSmartcarApplicationId(resolvedId)
                            return@withContext buildSmartcarAuthUrl(resolvedId)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        buildSmartcarAuthUrl(OdometerSyncService.DEFAULT_SMARTCAR_APPLICATION_ID)
    }

    suspend fun refreshSmartcarConnectionStatus(): Boolean = withContext(Dispatchers.IO) {
        val (clientId, _) = getEffectiveSmartcarCredentials()
        if (clientId?.startsWith("client_") != true && preferencesManager.getSmartcarUserId().isNullOrBlank()) {
            return@withContext !preferencesManager.getSmartcarVehicleId().isNullOrBlank()
        }
        var token = getValidSmartcarAccessToken() ?: return@withContext false

        fun fetchConnections(bearerToken: String): Pair<Int, String> {
            val connReq = Request.Builder()
                .url("${OdometerSyncService.SMARTCAR_V3_API_BASE}/connections")
                .addHeader("Authorization", "Bearer $bearerToken")
                .build()
            return client.newCall(connReq).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        try {
            var (code, bodyStr) = fetchConnections(token)
            if (code == 401) {
                val refreshed = getValidSmartcarAccessToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank() && refreshed != token) {
                    token = refreshed
                    val retry = fetchConnections(token)
                    code = retry.first
                    bodyStr = retry.second
                }
            }
            if (code !in 200..299) return@withContext false
            val preferredUserId = preferencesManager.getSmartcarUserId()
            val parsed = parseSmartcarV3Connection(bodyStr, preferredUserId)
            if (parsed != null) {
                preferencesManager.setSmartcarVehicleId(parsed.first, parsed.second)
                true
            } else {
                preferencesManager.clearSmartcarVehicleConnection()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun handleSmartcarCallback(uri: Uri): Boolean {
        preferencesManager.setSmartcarDcCapability(PreferencesManager.SMARTCAR_CAPABILITY_UNKNOWN)
        val userId = uri.getQueryParameter("user_id")
        if (!userId.isNullOrBlank()) {
            preferencesManager.setSmartcarUserId(userId)
        }
        val vehicleId = uri.getQueryParameter("vehicle_id")
        if (!vehicleId.isNullOrBlank()) {
            preferencesManager.setSmartcarVehicleId(vehicleId, userId)
        }
        val code = uri.getQueryParameter("code")
        return if (!code.isNullOrBlank()) {
            exchangeSmartcarCode(code)
        } else {
            getValidSmartcarAccessToken(forceRefresh = true)
            val connected = refreshSmartcarConnectionStatus() || !userId.isNullOrBlank() || !vehicleId.isNullOrBlank()
            if (connected) {
                stateSink.setMessage("Smartcar connected")
            }
            connected
        }
    }

    suspend fun exchangeSmartcarCode(code: String): Boolean {
        val (clientId, clientSecret) = getEffectiveSmartcarCredentials()
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) return false

        val basicAuth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", OdometerSyncService.SMARTCAR_REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(OdometerSyncService.SMARTCAR_AUTH_TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val obj = telemetryJson.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val access = obj["access_token"]?.jsonPrimitive?.content ?: return false
                val refresh = obj["refresh_token"]?.jsonPrimitive?.content
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L
                preferencesManager.saveSmartcarTokens(access, refresh, expires)
                stateSink.setMessage("Smartcar connected")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Smartcar code exchange failed", e)
            false
        }
    }

    suspend fun getValidSmartcarAccessToken(forceRefresh: Boolean = false): String? {
        val (access, refresh, expiry) = preferencesManager.getSmartcarTokens()
        val (clientId, clientSecret) = getEffectiveSmartcarCredentials()
        val isV3Client = clientId?.startsWith("client_") == true
        val now = System.currentTimeMillis()
        val isUnexpired = !access.isNullOrBlank() && (
            (!isV3Client && expiry == 0L) || (expiry > 0L && now < expiry - 60_000L)
        )
        if (!forceRefresh && isUnexpired) return access
        val fallbackIfUnexpired = access?.takeIf { expiry == 0L || now < expiry }
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) return fallbackIfUnexpired

        if (isV3Client || refresh.isNullOrBlank()) {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()
            val request = Request.Builder().url(OdometerSyncService.SMARTCAR_V3_IAM_TOKEN_URL).post(body).build()
            return try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return fallbackIfUnexpired
                    val obj = telemetryJson.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return fallbackIfUnexpired
                    val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                    preferencesManager.saveSmartcarTokens(newAccess, null, expires)
                    newAccess
                }
            } catch (_: Exception) {
                fallbackIfUnexpired
            }
        }

        val basicAuth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        val request = Request.Builder()
            .url(OdometerSyncService.SMARTCAR_AUTH_TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return fallbackIfUnexpired
                val obj = telemetryJson.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return fallbackIfUnexpired
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content ?: refresh
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L
                preferencesManager.saveSmartcarTokens(newAccess, newRefresh, expires)
                newAccess
            }
        } catch (_: Exception) {
            fallbackIfUnexpired
        }
    }

    suspend fun executeSmartcarV3SignalGet(
        vehicleId: String,
        signalCode: String,
        userId: String?,
        initialToken: String
    ): Triple<Int, String, String> {
        var activeToken = initialToken
        fun doGet(bearer: String): Pair<Int, String> {
            val reqBuilder = Request.Builder()
                .url("${OdometerSyncService.SMARTCAR_V3_API_BASE}/vehicles/$vehicleId/signals/$signalCode")
                .addHeader("Authorization", "Bearer $bearer")
            if (!userId.isNullOrBlank()) {
                reqBuilder.addHeader("sc-user-id", userId)
            }
            return client.newCall(reqBuilder.build()).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        }

        var (code, body) = doGet(activeToken)
        if (code == 401) {
            val refreshed = getValidSmartcarAccessToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank() && refreshed != activeToken) {
                activeToken = refreshed
                val retry = doGet(activeToken)
                code = retry.first
                body = retry.second
            }
        }
        return Triple(code, body, activeToken)
    }

    companion object {
        private const val TAG = "SmartcarAuthClient"

        fun buildSmartcarAuthUrl(clientOrAppId: String): String {
            val trimmed = clientOrAppId.trim()
            val defaultAppId = OdometerSyncService.DEFAULT_SMARTCAR_APPLICATION_ID
            val appId = if ((trimmed.isBlank() || trimmed.startsWith("client_")) && defaultAppId.isNotBlank()) {
                defaultAppId.trim()
            } else {
                trimmed
            }
            val encodedAppId = URLEncoder.encode(appId, "UTF-8")
            val encodedRedirect = URLEncoder.encode(OdometerSyncService.SMARTCAR_REDIRECT_URI, "UTF-8")
            return "https://connect.smartcar.com/oauth/authorize" +
                "?application_id=$encodedAppId" +
                "&response_type=none" +
                "&redirect_uri=$encodedRedirect" +
                "&mode=live"
        }

        fun parseSmartcarV3Connection(jsonBody: String, preferredUserId: String? = null): Pair<String, String?>? {
            return try {
                val root = telemetryJson.parseToJsonElement(jsonBody).jsonObject
                val dataArr = root["data"]?.jsonArray ?: return null
                if (dataArr.isEmpty()) return null

                fun extractPair(item: JsonObject): Pair<String, String?>? {
                    val attrs = item["attributes"]?.jsonObject
                    val rels = item["relationships"]?.jsonObject
                    val vehicleId = attrs?.get("vehicleId")?.jsonPrimitive?.content
                        ?: rels?.get("vehicle")?.jsonObject?.get("data")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: item["vehicleId"]?.jsonPrimitive?.content
                        ?: item["id"]?.jsonPrimitive?.content
                        ?: return null
                    val userId = attrs?.get("userId")?.jsonPrimitive?.content
                        ?: attrs?.get("user")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: rels?.get("user")?.jsonObject?.get("data")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: item["userId"]?.jsonPrimitive?.content
                    return Pair(vehicleId, userId)
                }

                if (!preferredUserId.isNullOrBlank()) {
                    for (elem in dataArr) {
                        val pair = extractPair(elem.jsonObject) ?: continue
                        if (pair.second == preferredUserId) return pair
                    }
                }
                val first = dataArr.firstOrNull()?.jsonObject ?: return null
                extractPair(first)
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarV3ApplicationId(jsonBody: String): String? {
            return try {
                val root = telemetryJson.parseToJsonElement(jsonBody).jsonObject
                val dataArr = root["data"]?.jsonArray ?: return null
                val first = dataArr.firstOrNull()?.jsonObject ?: return null
                first["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && !it.startsWith("client_") }
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarErrorDetail(jsonBody: String): String? {
            return try {
                val root = telemetryJson.parseToJsonElement(jsonBody).jsonObject
                val firstErr = root["errors"]?.jsonArray?.firstOrNull()?.jsonObject
                if (firstErr != null) {
                    val title = firstErr["title"]?.jsonPrimitive?.content
                    val code = firstErr["code"]?.jsonPrimitive?.content
                    val detail = firstErr["detail"]?.jsonPrimitive?.content
                    return title ?: code ?: detail
                }
                val dataObj = root["data"]?.jsonObject
                val attrsObj = dataObj?.get("attributes")?.jsonObject
                val statusObj = attrsObj?.get("status")?.jsonObject ?: root["status"]?.jsonObject
                val statusVal = statusObj?.get("value")?.jsonPrimitive?.content
                if (!statusVal.isNullOrBlank() && !statusVal.equals("SUCCESS", ignoreCase = true)) {
                    val errObj = statusObj["error"]?.jsonObject
                    if (errObj != null) {
                        val errType = errObj["type"]?.jsonPrimitive?.content
                        val resType = errObj["resolution"]?.jsonObject?.get("type")?.jsonPrimitive?.content
                        if (errType.equals("PERMISSION", ignoreCase = true) ||
                            resType.equals("REAUTHENTICATE", ignoreCase = true)
                        ) {
                            return "Missing odometer permission — tap Reconnect in Settings"
                        }
                        val resolved = errObj["detail"]?.jsonPrimitive?.content
                            ?: errObj["title"]?.jsonPrimitive?.content
                            ?: errObj["code"]?.jsonPrimitive?.content
                        if (!resolved.isNullOrBlank()) return resolved
                    }
                    val reason = statusObj["messages"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    return if (!reason.isNullOrBlank()) "$statusVal ($reason)" else statusVal
                }
                root["message"]?.jsonPrimitive?.content ?: root["error"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarV3ErrorCode(rawBody: String): Pair<String?, String?> {
            return try {
                val root = telemetryJson.parseToJsonElement(rawBody) as? JsonObject ?: return Pair(null, null)
                val firstErr = (root["errors"] as? JsonArray)?.firstOrNull() as? JsonObject
                if (firstErr != null) {
                    return Pair(
                        firstErr["type"].asStringOrNull()?.uppercase(Locale.US),
                        firstErr["code"].asStringOrNull()?.uppercase(Locale.US)
                    )
                }
                val dataObj = root["data"] as? JsonObject
                val attrsObj = dataObj?.get("attributes") as? JsonObject
                val statusObj = (attrsObj?.get("status") as? JsonObject) ?: (root["status"] as? JsonObject)
                val errObj = statusObj?.get("error") as? JsonObject
                if (errObj != null) {
                    return Pair(
                        errObj["type"].asStringOrNull()?.uppercase(Locale.US),
                        errObj["code"].asStringOrNull()?.uppercase(Locale.US)
                    )
                }
                Pair(null, null)
            } catch (_: Exception) {
                Pair(null, null)
            }
        }

        fun isSmartcarNotCapableError(httpCode: Int, rawBody: String): Boolean {
            if (httpCode == 501) return true
            val (type, code) = parseSmartcarV3ErrorCode(rawBody)
            return type == "COMPATIBILITY" ||
                code == "VEHICLE_NOT_CAPABLE" ||
                rawBody.contains("VEHICLE_NOT_CAPABLE", ignoreCase = true)
        }

        fun isSmartcarPermissionError(httpCode: Int, rawBody: String): Boolean {
            val (type, code) = parseSmartcarV3ErrorCode(rawBody)
            if (type == "PERMISSION" || code == "FORBIDDEN" || code == "PERMISSION") return true
            return httpCode == 403 && !isSmartcarNotCapableError(httpCode, rawBody)
        }
    }
}
