package com.example.car_logger.services

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.car_logger.BuildConfig
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.model.Car
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

sealed class OdometerSyncResult {
    data class Updated(val carId: String, val odometerMiles: Int, val provider: String) : OdometerSyncResult()
    data class SkippedAsleep(val carId: String) : OdometerSyncResult()
    data class SkippedCooldown(val carId: String, val hoursRemaining: Int) : OdometerSyncResult()
    data class SkippedPolicy(val carId: String, val policy: WakeProtectionPolicy) : OdometerSyncResult()
    data class NotConfigured(val carId: String) : OdometerSyncResult()
    data class Error(val carId: String, val message: String) : OdometerSyncResult()
}

class OdometerSyncService(
    private val preferencesManager: PreferencesManager,
    private val carDao: CarDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    companion object {
        private const val TAG = "OdometerSyncService"

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
        const val TESLA_AUTH_TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun isCooldownActive(
            lastSyncMs: Long?,
            cooldownHours: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (lastSyncMs == null || lastSyncMs <= 0L || cooldownHours <= 0) return false
            val elapsedMs = nowMs - lastSyncMs
            return elapsedMs in 0 until (cooldownHours * 3_600_000L)
        }

        fun remainingCooldownHours(
            lastSyncMs: Long?,
            cooldownHours: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Int {
            if (!isCooldownActive(lastSyncMs, cooldownHours, nowMs)) return 0
            val remainingMs = (cooldownHours * 3_600_000L) - (nowMs - (lastSyncMs ?: 0L))
            return ceil(remainingMs / 3_600_000.0).toInt().coerceAtLeast(1)
        }

        fun formatRelativeTime(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
            if (timestampMs == null || timestampMs <= 0L) return null
            val diffMinutes = ((nowMs - timestampMs) / 60_000L).coerceAtLeast(0L)
            return when {
                diffMinutes < 1 -> "just now"
                diffMinutes < 60 -> "${diffMinutes}m ago"
                diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
                else -> "${diffMinutes / 1440}d ago"
            }
        }

        fun parseSmartcarOdometerMiles(jsonBody: String): Int? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                // Check v3 JSON:API structure: { "data": { "attributes": { "body": { "value": 24899, "unit": "km" } } } }
                // or root["body"]["value"]
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

                // Fallback v2.0 response: { "distance": 14820.4 }
                val distance = root["distance"]?.jsonPrimitive?.doubleOrNull
                    ?: root["value"]?.jsonPrimitive?.doubleOrNull
                    ?: dataObj?.get("distance")?.jsonPrimitive?.doubleOrNull
                    ?: attrsObj?.get("distance")?.jsonPrimitive?.doubleOrNull
                distance?.roundToInt()?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarV3Connection(jsonBody: String, preferredUserId: String? = null): Pair<String, String?>? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val dataArr = root["data"]?.jsonArray ?: return null
                if (dataArr.isEmpty()) return null

                fun extractPair(item: kotlinx.serialization.json.JsonObject): Pair<String, String?>? {
                    val attrs = item["attributes"]?.jsonObject
                    val rels = item["relationships"]?.jsonObject

                    val vehicleId = attrs?.get("vehicleId")?.jsonPrimitive?.content
                        ?: rels?.get("vehicle")?.jsonObject?.get("data")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: item["vehicleId"]?.jsonPrimitive?.content
                        ?: item["id"]?.jsonPrimitive?.content
                        ?: return null

                    val userId = attrs?.get("userId")?.jsonPrimitive?.content
                        ?: rels?.get("user")?.jsonObject?.get("data")?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?: item["userId"]?.jsonPrimitive?.content

                    return Pair(vehicleId, userId)
                }

                if (!preferredUserId.isNullOrBlank()) {
                    for (elem in dataArr) {
                        val pair = extractPair(elem.jsonObject) ?: continue
                        if (pair.second == preferredUserId) {
                            return pair
                        }
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
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val dataArr = root["data"]?.jsonArray ?: return null
                val first = dataArr.firstOrNull()?.jsonObject ?: return null
                first["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && !it.startsWith("client_") }
            } catch (_: Exception) {
                null
            }
        }

        fun parseSmartcarErrorDetail(jsonBody: String): String? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
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
                    val reason = statusObj["messages"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    return if (!reason.isNullOrBlank()) "$statusVal ($reason)" else statusVal
                }
                root["message"]?.jsonPrimitive?.content ?: root["error"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                null
            }
        }

        fun formatSyncResultMessage(car: Car, result: OdometerSyncResult): String {
            val name = car.displayName
            return when (result) {
                is OdometerSyncResult.Updated -> {
                    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(result.odometerMiles)
                    "Synced $name: $formatted mi"
                }
                is OdometerSyncResult.SkippedAsleep ->
                    "$name is asleep (wake skipped)"
                is OdometerSyncResult.SkippedCooldown ->
                    "$name is in cooldown (${result.hoursRemaining}h remaining)"
                is OdometerSyncResult.SkippedPolicy ->
                    "$name sync skipped (${result.policy.label} mode)"
                is OdometerSyncResult.NotConfigured ->
                    "$name telemetry not linked — connect in Settings"
                is OdometerSyncResult.Error ->
                    "Sync failed for $name: ${result.message}"
            }
        }

        fun parseTeslaVehicleListState(jsonBody: String, targetVin: String?): Pair<String, String>? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val responseArr = root["response"]?.jsonArray ?: return null
                for (element in responseArr) {
                    val obj = element.jsonObject
                    val vin = obj["vin"]?.jsonPrimitive?.content?.uppercase() ?: continue
                    val state = obj["state"]?.jsonPrimitive?.content?.lowercase() ?: "unknown"
                    if (targetVin.isNullOrBlank() || vin == targetVin.trim().uppercase()) {
                        return Pair(vin, state)
                    }
                }
                val first = responseArr.firstOrNull()?.jsonObject ?: return null
                val vin = first["vin"]?.jsonPrimitive?.content ?: return null
                val state = first["state"]?.jsonPrimitive?.content?.lowercase() ?: "unknown"
                Pair(vin, state)
            } catch (_: Exception) {
                null
            }
        }

        fun parseTeslaOdometerMiles(jsonBody: String): Int? {
            return try {
                val root = json.parseToJsonElement(jsonBody).jsonObject
                val response = root["response"]?.jsonObject ?: root
                val vehicleState = response["vehicle_state"]?.jsonObject ?: response
                val odo = vehicleState["odometer"]?.jsonPrimitive?.doubleOrNull
                odo?.roundToInt()?.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }

        fun resolveProviderForCar(car: Car): String? {
            if (!car.telemetryProvider.isNullOrBlank()) return car.telemetryProvider
            val makeLower = car.make.lowercase()
            return when {
                makeLower.contains("tesla") -> PROVIDER_TESLA
                makeLower.contains("chevrolet") || makeLower.contains("chevy") ||
                    makeLower.contains("gmc") || makeLower.contains("cadillac") -> PROVIDER_SMARTCAR
                else -> null
            }
        }
    }

    fun buildSmartcarAuthUrl(clientOrAppId: String): String {
        val trimmed = clientOrAppId.trim()
        val appId = if ((trimmed.isBlank() || trimmed.startsWith("client_")) && DEFAULT_SMARTCAR_APPLICATION_ID.isNotBlank()) {
            DEFAULT_SMARTCAR_APPLICATION_ID.trim()
        } else {
            trimmed
        }
        val encodedAppId = URLEncoder.encode(appId, "UTF-8")
        val encodedRedirect = URLEncoder.encode(SMARTCAR_REDIRECT_URI, "UTF-8")
        return "https://connect.smartcar.com/oauth/authorize" +
            "?application_id=$encodedAppId" +
            "&response_type=none" +
            "&redirect_uri=$encodedRedirect" +
            "&mode=live"
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
                .url("$SMARTCAR_V3_MANAGEMENT_API_BASE/applications")
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
        buildSmartcarAuthUrl(DEFAULT_SMARTCAR_APPLICATION_ID)
    }

    suspend fun refreshSmartcarConnectionStatus(): Boolean = withContext(Dispatchers.IO) {
        val (clientId, _) = getEffectiveSmartcarCredentials()
        if (clientId?.startsWith("client_") != true) {
            return@withContext !preferencesManager.getSmartcarVehicleId().isNullOrBlank()
        }
        val token = getValidSmartcarAccessToken() ?: return@withContext false
        val connReq = Request.Builder()
            .url("$SMARTCAR_V3_API_BASE/connections")
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(connReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val preferredUserId = preferencesManager.getSmartcarUserId()
                val parsed = parseSmartcarV3Connection(resp.body?.string().orEmpty(), preferredUserId)
                if (parsed != null) {
                    preferencesManager.setSmartcarVehicleId(parsed.first, parsed.second)
                    true
                } else {
                    preferencesManager.clearSmartcarVehicleConnection()
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun buildTeslaAuthUrl(clientId: String): String {
        val encodedClient = URLEncoder.encode(clientId.trim(), "UTF-8")
        val encodedRedirect = URLEncoder.encode(TESLA_REDIRECT_URI, "UTF-8")
        return "https://auth.tesla.com/oauth2/v3/authorize" +
            "?response_type=code" +
            "&client_id=$encodedClient" +
            "&redirect_uri=$encodedRedirect" +
            "&scope=openid%20offline_access%20vehicle_device_data" +
            "&state=carlogger_tesla"
    }

    suspend fun handleOAuthCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val errorDesc = uri.getQueryParameter("error_description") ?: error
            Log.w(TAG, "OAuth callback error: $errorDesc")
            _lastSyncMessage.value = "OAuth failed: $errorDesc"
            return@withContext false
        }
        val path = uri.path.orEmpty()
        if (path.contains("smartcar", ignoreCase = true)) {
            val userId = uri.getQueryParameter("user_id")
            if (!userId.isNullOrBlank()) {
                preferencesManager.setSmartcarUserId(userId)
            }
            val vehicleId = uri.getQueryParameter("vehicle_id")
            if (!vehicleId.isNullOrBlank()) {
                preferencesManager.setSmartcarVehicleId(vehicleId, userId)
            }
            val code = uri.getQueryParameter("code")
            return@withContext if (!code.isNullOrBlank()) {
                exchangeSmartcarCode(code)
            } else {
                val connected = refreshSmartcarConnectionStatus() || !userId.isNullOrBlank() || !vehicleId.isNullOrBlank()
                if (connected) {
                    _lastSyncMessage.value = "Smartcar connected"
                }
                connected
            }
        }
        val code = uri.getQueryParameter("code") ?: return@withContext false
        return@withContext when {
            path.contains("tesla", ignoreCase = true) -> exchangeTeslaCode(code)
            else -> false
        }
    }

    suspend fun getEffectiveSmartcarCredentials(): Pair<String?, String?> {
        val (savedId, savedSecret) = preferencesManager.getSmartcarCredentials()
        val id = savedId?.takeIf { it.isNotBlank() } ?: DEFAULT_SMARTCAR_CLIENT_ID.takeIf { it.isNotBlank() }
        val secret = savedSecret?.takeIf { it.isNotBlank() } ?: DEFAULT_SMARTCAR_CLIENT_SECRET.takeIf { it.isNotBlank() }
        return Pair(id, secret)
    }

    suspend fun getEffectiveTeslaCredentials(): Pair<String?, String?> {
        val (savedId, savedSecret) = preferencesManager.getTeslaCredentials()
        val id = savedId?.takeIf { it.isNotBlank() } ?: DEFAULT_TESLA_CLIENT_ID.takeIf { it.isNotBlank() }
        val secret = savedSecret?.takeIf { it.isNotBlank() } ?: DEFAULT_TESLA_CLIENT_SECRET.takeIf { it.isNotBlank() }
        return Pair(id, secret)
    }

    private suspend fun exchangeSmartcarCode(code: String): Boolean {
        val (clientId, clientSecret) = getEffectiveSmartcarCredentials()
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) return false

        val basicAuth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", SMARTCAR_REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(SMARTCAR_AUTH_TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val access = obj["access_token"]?.jsonPrimitive?.content ?: return false
                val refresh = obj["refresh_token"]?.jsonPrimitive?.content
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L
                preferencesManager.saveSmartcarTokens(access, refresh, expires)
                _lastSyncMessage.value = "Smartcar connected"
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Smartcar code exchange failed", e)
            false
        }
    }

    private suspend fun exchangeTeslaCode(code: String): Boolean {
        val (clientId, clientSecret) = getEffectiveTeslaCredentials()
        if (clientId.isNullOrBlank()) return false

        val formBuilder = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code)
            .add("redirect_uri", TESLA_REDIRECT_URI)
            .add("audience", TESLA_API_BASE)
        if (!clientSecret.isNullOrBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(TESLA_AUTH_TOKEN_URL)
            .post(formBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val access = obj["access_token"]?.jsonPrimitive?.content ?: return false
                val refresh = obj["refresh_token"]?.jsonPrimitive?.content
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 28800L
                preferencesManager.saveTeslaTokens(access, refresh, expires)
                _lastSyncMessage.value = "Tesla connected"
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tesla code exchange failed", e)
            false
        }
    }

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

    suspend fun syncVehicle(
        car: Car,
        isManual: Boolean = false,
        forceWake: Boolean = false
    ): OdometerSyncResult = withContext(Dispatchers.IO) {
        val provider = resolveProviderForCar(car)
            ?: return@withContext OdometerSyncResult.NotConfigured(car.id)

        val cooldownHours = preferencesManager.getSyncCooldownHours()
        val nowMs = System.currentTimeMillis()

        // Enforce cooldown unless the user explicitly forced wake/poll
        if (!forceWake && isCooldownActive(car.lastOdometerSyncTime, cooldownHours, nowMs)) {
            val remaining = remainingCooldownHours(car.lastOdometerSyncTime, cooldownHours, nowMs)
            return@withContext OdometerSyncResult.SkippedCooldown(car.id, remaining)
        }

        _isSyncing.value = true
        try {
            return@withContext when (provider) {
                PROVIDER_TESLA -> syncTeslaVehicle(car, forceWake)
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

    private suspend fun getValidTeslaAccessToken(): String? {
        val (access, refresh, expiry) = preferencesManager.getTeslaTokens()
        if (access.isNullOrBlank()) return null
        if (expiry == 0L || System.currentTimeMillis() < expiry - 60_000L) {
            return access
        }
        val (clientId, clientSecret) = getEffectiveTeslaCredentials()
        if (clientId.isNullOrBlank() || refresh.isNullOrBlank()) return access

        val formBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refresh)
        if (!clientSecret.isNullOrBlank()) {
            formBuilder.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(TESLA_AUTH_TOKEN_URL)
            .post(formBuilder.build())
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return access
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return access
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content ?: refresh
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 28800L
                preferencesManager.saveTeslaTokens(newAccess, newRefresh, expires)
                newAccess
            }
        } catch (_: Exception) {
            access
        }
    }

    private suspend fun syncTeslaVehicle(car: Car, forceWake: Boolean): OdometerSyncResult {
        val token = getValidTeslaAccessToken()
            ?: return OdometerSyncResult.NotConfigured(car.id)

        // Step 1: Zero-Wake Cloud State Check
        val listReq = Request.Builder()
            .url("$TESLA_API_BASE/api/1/vehicles")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val (vin, state) = try {
            client.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return OdometerSyncResult.Error(car.id, "Tesla API (${resp.code})")
                }
                parseTeslaVehicleListState(resp.body?.string().orEmpty(), car.vin)
                    ?: return OdometerSyncResult.Error(car.id, "No Tesla found")
            }
        } catch (e: Exception) {
            return OdometerSyncResult.Error(car.id, "Network error")
        }

        // Step 2: If asleep and forceWake is false, NEVER wake the vehicle
        if (state != "online") {
            if (!forceWake) {
                carDao.updateTelemetryStatus(car.id, PROVIDER_TESLA, "Asleep")
                _lastSyncMessage.value = "${car.model} asleep (wake skipped)"
                return OdometerSyncResult.SkippedAsleep(car.id)
            }

            // Explicit user Force Wake
            val wakeReq = Request.Builder()
                .url("$TESLA_API_BASE/api/1/vehicles/$vin/wake_up")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(wakeReq).execute().close()
                delay(4000)
            } catch (_: Exception) {}
        }

        // Step 3: Fetch vehicle_state.odometer
        val dataReq = Request.Builder()
            .url("$TESLA_API_BASE/api/1/vehicles/$vin/vehicle_data?endpoints=vehicle_state")
            .addHeader("Authorization", "Bearer $token")
            .build()

        return try {
            client.newCall(dataReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 408) {
                        carDao.updateTelemetryStatus(car.id, PROVIDER_TESLA, "Asleep")
                        return OdometerSyncResult.SkippedAsleep(car.id)
                    }
                    return OdometerSyncResult.Error(car.id, "Tesla data (${resp.code})")
                }
                val newOdo = parseTeslaOdometerMiles(resp.body?.string().orEmpty())
                    ?: return OdometerSyncResult.Error(car.id, "Invalid odometer payload")

                val finalOdo = maxOf(newOdo, car.odometer ?: 0)
                val now = System.currentTimeMillis()
                carDao.updateOdometerFromTelemetry(
                    carId = car.id,
                    odometer = finalOdo,
                    provider = PROVIDER_TESLA,
                    status = "Synced",
                    syncTime = now
                )
                _lastSyncMessage.value = "${car.model}: $finalOdo mi"
                OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_TESLA)
            }
        } catch (e: Exception) {
            OdometerSyncResult.Error(car.id, "Failed to read Tesla odometer")
        }
    }

    private suspend fun getValidSmartcarAccessToken(): String? {
        val (access, refresh, expiry) = preferencesManager.getSmartcarTokens()
        if (!access.isNullOrBlank() && (expiry == 0L || System.currentTimeMillis() < expiry - 60_000L)) {
            return access
        }
        val (clientId, clientSecret) = getEffectiveSmartcarCredentials()
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            return access
        }

        // Smartcar API v3 IAM Client Credentials Flow (client_...)
        if (clientId.startsWith("client_") || refresh.isNullOrBlank()) {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build()

            val request = Request.Builder()
                .url(SMARTCAR_V3_IAM_TOKEN_URL)
                .post(body)
                .build()

            return try {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return access
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return access
                    val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                    preferencesManager.saveSmartcarTokens(newAccess, null, expires)
                    newAccess
                }
            } catch (_: Exception) {
                access
            }
        }

        val basicAuth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()

        val request = Request.Builder()
            .url(SMARTCAR_AUTH_TOKEN_URL)
            .addHeader("Authorization", "Basic $basicAuth")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return access
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return access
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content ?: refresh
                val expires = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L
                preferencesManager.saveSmartcarTokens(newAccess, newRefresh, expires)
                newAccess
            }
        } catch (_: Exception) {
            access
        }
    }

    private suspend fun syncSmartcarVehicle(car: Car): OdometerSyncResult {
        val token = getValidSmartcarAccessToken()
            ?: return OdometerSyncResult.NotConfigured(car.id)

        val (clientId, _) = getEffectiveSmartcarCredentials()
        val isV3 = clientId?.startsWith("client_") == true

        if (isV3) {
            return syncSmartcarV3Vehicle(car, token)
        }

        var vehicleId = preferencesManager.getSmartcarVehicleId()
        if (vehicleId.isNullOrBlank()) {
            val listReq = Request.Builder()
                .url("$SMARTCAR_API_BASE/vehicles")
                .addHeader("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(listReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return OdometerSyncResult.Error(car.id, "Smartcar (${resp.code})")
                    }
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val firstId = root["vehicles"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                    if (firstId.isNullOrBlank()) {
                        return OdometerSyncResult.Error(car.id, "No Smartcar vehicle linked")
                    }
                    vehicleId = firstId
                    preferencesManager.setSmartcarVehicleId(firstId)
                }
            } catch (e: Exception) {
                return OdometerSyncResult.Error(car.id, "Network error")
            }
        }

        val odoReq = Request.Builder()
            .url("$SMARTCAR_API_BASE/vehicles/$vehicleId/odometer")
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
                carDao.updateOdometerFromTelemetry(
                    carId = car.id,
                    odometer = finalOdo,
                    provider = PROVIDER_SMARTCAR,
                    status = "Synced",
                    syncTime = now
                )
                _lastSyncMessage.value = "${car.model}: $finalOdo mi"
                OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_SMARTCAR)
            }
        } catch (e: Exception) {
            OdometerSyncResult.Error(car.id, "Failed to read Smartcar odometer")
        }
    }

    private suspend fun syncSmartcarV3Vehicle(car: Car, token: String): OdometerSyncResult {
        val connReq = Request.Builder()
            .url("$SMARTCAR_V3_API_BASE/connections")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val (vehicleId, userId) = try {
            client.newCall(connReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = parseSmartcarErrorDetail(bodyStr)
                    val msg = if (detail != null) "Smartcar v3 (${resp.code}: $detail)" else "Smartcar v3 (${resp.code})"
                    _lastSyncMessage.value = msg
                    return OdometerSyncResult.Error(car.id, msg)
                }
                val preferredUserId = preferencesManager.getSmartcarUserId()
                val parsed = parseSmartcarV3Connection(bodyStr, preferredUserId)
                if (parsed == null) {
                    preferencesManager.clearSmartcarVehicleConnection()
                    val msg = "No vehicle linked in Smartcar — tap Connect in Settings"
                    _lastSyncMessage.value = msg
                    return OdometerSyncResult.Error(car.id, msg)
                }
                parsed
            }
        } catch (e: Exception) {
            val msg = "Network error contacting Smartcar"
            _lastSyncMessage.value = msg
            return OdometerSyncResult.Error(car.id, msg)
        }

        preferencesManager.setSmartcarVehicleId(vehicleId, userId)

        val odoReqBuilder = Request.Builder()
            .url("$SMARTCAR_V3_API_BASE/vehicles/$vehicleId/signals/odometer-traveleddistance")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("sc-unit-system", "imperial")
            .addHeader("Smartcar-Unit-System", "imperial")
        if (!userId.isNullOrBlank()) {
            odoReqBuilder.addHeader("sc-user-id", userId)
        }

        return try {
            client.newCall(odoReqBuilder.build()).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = parseSmartcarErrorDetail(bodyStr)
                    val msg = if (detail != null) "Smartcar (${resp.code}: $detail)" else "Smartcar signal (${resp.code})"
                    _lastSyncMessage.value = msg
                    return OdometerSyncResult.Error(car.id, msg)
                }
                val newOdo = parseSmartcarOdometerMiles(bodyStr)
                if (newOdo == null) {
                    val detail = parseSmartcarErrorDetail(bodyStr) ?: "Invalid v3 odometer response"
                    _lastSyncMessage.value = detail
                    return OdometerSyncResult.Error(car.id, detail)
                }

                val finalOdo = maxOf(newOdo, car.odometer ?: 0)
                val now = System.currentTimeMillis()
                carDao.updateOdometerFromTelemetry(
                    carId = car.id,
                    odometer = finalOdo,
                    provider = PROVIDER_SMARTCAR,
                    status = "Synced",
                    syncTime = now
                )
                _lastSyncMessage.value = "${car.model}: $finalOdo mi"
                OdometerSyncResult.Updated(car.id, finalOdo, PROVIDER_SMARTCAR)
            }
        } catch (e: Exception) {
            val msg = "Failed to read Smartcar v3 odometer (${e.javaClass.simpleName})"
            _lastSyncMessage.value = msg
            OdometerSyncResult.Error(car.id, msg)
        }
    }
}
