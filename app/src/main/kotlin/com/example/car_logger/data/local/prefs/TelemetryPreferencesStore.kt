package com.example.car_logger.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class TelemetryPreferencesStore(private val dataStore: DataStore<Preferences>) {

    val wakePolicyFlow: Flow<WakeProtectionPolicy> = dataStore.data.map { prefs ->
        WakeProtectionPolicy.fromKey(prefs[PreferencesManager.KEY_WAKE_POLICY])
    }

    val syncCooldownHoursFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_SYNC_COOLDOWN_HOURS] ?: PreferencesManager.DEFAULT_COOLDOWN_HOURS
    }

    val dailyPollEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_DAILY_POLL_ENABLED] ?: true
    }

    val lastDailyPollTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_DAILY_POLL_TIME] ?: 0L
    }

    val lastDailyPollStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_DAILY_POLL_STATUS]
    }

    val smartcarClientIdFlow: Flow<String?> = dataStore.data.map { it[PreferencesManager.KEY_SMARTCAR_CLIENT_ID] }

    val smartcarConnectedFlow: Flow<Boolean> = dataStore.data.map {
        !it[PreferencesManager.KEY_SMARTCAR_VEHICLE_ID].isNullOrBlank() ||
            !it[PreferencesManager.KEY_SMARTCAR_USER_ID].isNullOrBlank() ||
            !it[PreferencesManager.KEY_SMARTCAR_REFRESH_TOKEN].isNullOrBlank()
    }

    val teslaClientIdFlow: Flow<String?> = dataStore.data.map { it[PreferencesManager.KEY_TESLA_CLIENT_ID] }

    val teslaConnectedFlow: Flow<Boolean> = dataStore.data.map {
        !it[PreferencesManager.KEY_TESLA_ACCESS_TOKEN].isNullOrBlank()
    }

    val teslaAccessTokenFlow: Flow<String?> = dataStore.data.map { it[PreferencesManager.KEY_TESLA_ACCESS_TOKEN] }

    suspend fun getWakePolicy(): WakeProtectionPolicy = wakePolicyFlow.first()
    suspend fun getSyncCooldownHours(): Int = syncCooldownHoursFlow.first()

    suspend fun setWakePolicy(policy: WakeProtectionPolicy) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_WAKE_POLICY] = policy.key
        }
    }

    suspend fun setSyncCooldownHours(hours: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SYNC_COOLDOWN_HOURS] = hours
        }
    }

    suspend fun getDailyPollEnabled(): Boolean = dailyPollEnabledFlow.first()

    suspend fun setDailyPollEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_DAILY_POLL_ENABLED] = enabled
        }
    }

    suspend fun recordDailyPollResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_LAST_DAILY_POLL_TIME] = timestampMs
            prefs[PreferencesManager.KEY_LAST_DAILY_POLL_STATUS] = status
        }
    }

    suspend fun getSmartcarCredentials(): Pair<String?, String?> = dataStore.data.map {
        Pair(it[PreferencesManager.KEY_SMARTCAR_CLIENT_ID], it[PreferencesManager.KEY_SMARTCAR_CLIENT_SECRET])
    }.first()

    suspend fun setSmartcarCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) {
        dataStore.edit { prefs ->
            if (clientId.isNullOrBlank()) prefs.remove(PreferencesManager.KEY_SMARTCAR_CLIENT_ID)
            else prefs[PreferencesManager.KEY_SMARTCAR_CLIENT_ID] = clientId.trim()

            if (clientSecret.isNullOrBlank()) prefs.remove(PreferencesManager.KEY_SMARTCAR_CLIENT_SECRET)
            else prefs[PreferencesManager.KEY_SMARTCAR_CLIENT_SECRET] = clientSecret.trim()

            if (accessToken != null) {
                val trimmedToken = accessToken.trim()
                if (trimmedToken.isEmpty()) {
                    prefs.remove(PreferencesManager.KEY_SMARTCAR_ACCESS_TOKEN)
                    prefs.remove(PreferencesManager.KEY_SMARTCAR_TOKEN_EXPIRY)
                } else if (trimmedToken != prefs[PreferencesManager.KEY_SMARTCAR_ACCESS_TOKEN]) {
                    prefs[PreferencesManager.KEY_SMARTCAR_ACCESS_TOKEN] = trimmedToken
                    prefs.remove(PreferencesManager.KEY_SMARTCAR_TOKEN_EXPIRY)
                }
            }
        }
    }

    suspend fun saveSmartcarTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, vehicleId: String? = null) {
        val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SMARTCAR_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) prefs[PreferencesManager.KEY_SMARTCAR_REFRESH_TOKEN] = refreshToken
            prefs[PreferencesManager.KEY_SMARTCAR_TOKEN_EXPIRY] = expiryMs
            if (!vehicleId.isNullOrBlank()) prefs[PreferencesManager.KEY_SMARTCAR_VEHICLE_ID] = vehicleId
        }
    }

    suspend fun getSmartcarTokens(): Triple<String?, String?, Long> = dataStore.data.map {
        Triple(
            it[PreferencesManager.KEY_SMARTCAR_ACCESS_TOKEN],
            it[PreferencesManager.KEY_SMARTCAR_REFRESH_TOKEN],
            it[PreferencesManager.KEY_SMARTCAR_TOKEN_EXPIRY] ?: 0L
        )
    }.first()

    suspend fun getSmartcarVehicleId(): String? = dataStore.data.map { it[PreferencesManager.KEY_SMARTCAR_VEHICLE_ID] }.first()

    suspend fun setSmartcarVehicleId(vehicleId: String, userId: String? = null) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SMARTCAR_VEHICLE_ID] = vehicleId
            if (!userId.isNullOrBlank()) {
                prefs[PreferencesManager.KEY_SMARTCAR_USER_ID] = userId
            }
        }
    }

    suspend fun getSmartcarUserId(): String? = dataStore.data.map { it[PreferencesManager.KEY_SMARTCAR_USER_ID] }.first()

    suspend fun setSmartcarUserId(userId: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SMARTCAR_USER_ID] = userId
        }
    }

    suspend fun getSmartcarApplicationId(): String? = dataStore.data.map { it[PreferencesManager.KEY_SMARTCAR_APPLICATION_ID] }.first()

    suspend fun setSmartcarApplicationId(appId: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SMARTCAR_APPLICATION_ID] = appId
        }
    }

    suspend fun clearSmartcarVehicleConnection() {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesManager.KEY_SMARTCAR_VEHICLE_ID)
            prefs.remove(PreferencesManager.KEY_SMARTCAR_USER_ID)
            prefs.remove(PreferencesManager.KEY_SMARTCAR_DC_CAPABILITY)
        }
    }

    suspend fun disconnectSmartcar() {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesManager.KEY_SMARTCAR_ACCESS_TOKEN)
            prefs.remove(PreferencesManager.KEY_SMARTCAR_REFRESH_TOKEN)
            prefs.remove(PreferencesManager.KEY_SMARTCAR_TOKEN_EXPIRY)
            prefs.remove(PreferencesManager.KEY_SMARTCAR_VEHICLE_ID)
            prefs.remove(PreferencesManager.KEY_SMARTCAR_USER_ID)
            prefs.remove(PreferencesManager.KEY_SMARTCAR_DC_CAPABILITY)
            prefs.remove(PreferencesManager.KEY_LAST_SMARTCAR_DC_SYNC_STATUS)
        }
    }

    suspend fun getTeslaCredentials(): Pair<String?, String?> = dataStore.data.map {
        Pair(it[PreferencesManager.KEY_TESLA_CLIENT_ID], it[PreferencesManager.KEY_TESLA_CLIENT_SECRET])
    }.first()

    suspend fun setTeslaCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) {
        dataStore.edit { prefs ->
            if (clientId.isNullOrBlank()) prefs.remove(PreferencesManager.KEY_TESLA_CLIENT_ID)
            else prefs[PreferencesManager.KEY_TESLA_CLIENT_ID] = clientId.trim()

            if (clientSecret.isNullOrBlank()) prefs.remove(PreferencesManager.KEY_TESLA_CLIENT_SECRET)
            else prefs[PreferencesManager.KEY_TESLA_CLIENT_SECRET] = clientSecret.trim()

            if (accessToken != null) {
                val trimmedToken = accessToken.trim()
                if (trimmedToken.isEmpty()) {
                    prefs.remove(PreferencesManager.KEY_TESLA_ACCESS_TOKEN)
                    prefs.remove(PreferencesManager.KEY_TESLA_REFRESH_TOKEN)
                    prefs.remove(PreferencesManager.KEY_TESLA_TOKEN_EXPIRY)
                } else if (trimmedToken != prefs[PreferencesManager.KEY_TESLA_ACCESS_TOKEN]) {
                    prefs[PreferencesManager.KEY_TESLA_ACCESS_TOKEN] = trimmedToken
                    prefs.remove(PreferencesManager.KEY_TESLA_REFRESH_TOKEN)
                    prefs.remove(PreferencesManager.KEY_TESLA_TOKEN_EXPIRY)
                }
            }
        }
    }

    suspend fun saveTeslaTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_TESLA_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) prefs[PreferencesManager.KEY_TESLA_REFRESH_TOKEN] = refreshToken
            prefs[PreferencesManager.KEY_TESLA_TOKEN_EXPIRY] = expiryMs
        }
    }

    suspend fun getTeslaTokens(): Triple<String?, String?, Long> = dataStore.data.map {
        Triple(
            it[PreferencesManager.KEY_TESLA_ACCESS_TOKEN],
            it[PreferencesManager.KEY_TESLA_REFRESH_TOKEN],
            it[PreferencesManager.KEY_TESLA_TOKEN_EXPIRY] ?: 0L
        )
    }.first()

    suspend fun disconnectTesla() {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesManager.KEY_TESLA_ACCESS_TOKEN)
            prefs.remove(PreferencesManager.KEY_TESLA_REFRESH_TOKEN)
            prefs.remove(PreferencesManager.KEY_TESLA_TOKEN_EXPIRY)
        }
    }
}
