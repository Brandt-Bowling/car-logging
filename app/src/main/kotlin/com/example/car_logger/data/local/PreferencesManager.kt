package com.example.car_logger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class WakeProtectionPolicy(val key: String, val label: String) {
    AWAKE_ONLY("awake_only", "Awake-Only"),
    POST_DRIVE("post_drive", "Post-Drive"),
    MANUAL_ONLY("manual_only", "Manual");

    companion object {
        fun fromKey(key: String?): WakeProtectionPolicy =
            entries.firstOrNull { it.key == key } ?: AWAKE_ONLY
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "car_logger_preferences")

class PreferencesManager internal constructor(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        val KEY_GARAGE_NAME = stringPreferencesKey("garage_name")
        val KEY_SYNC_FOLDER_ID = stringPreferencesKey("sync_folder_id")
        val KEY_SYNC_FOLDER_NAME = stringPreferencesKey("sync_folder_name")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_AUTO_DEV_API_KEY = stringPreferencesKey("auto_dev_api_key")
        val KEY_IMPORTED_FILE_IDS = stringSetPreferencesKey("imported_file_ids")
        val KEY_MIGRATION_V1_COMPLETE = booleanPreferencesKey("migration_from_flutter_v1_complete")
        val KEY_MIGRATION_V2_COMPLETE = booleanPreferencesKey("migration_from_flutter_v2_complete")
        val KEY_MIGRATION_V3_COMPLETE = booleanPreferencesKey("migration_from_flutter_v3_complete")

        // Telemetry & Wake Protection
        val KEY_WAKE_POLICY = stringPreferencesKey("telemetry_wake_policy")
        val KEY_SYNC_COOLDOWN_HOURS = intPreferencesKey("telemetry_cooldown_hours")
        val KEY_DAILY_POLL_ENABLED = booleanPreferencesKey("telemetry_daily_poll_enabled")
        val KEY_LAST_DAILY_POLL_TIME = longPreferencesKey("telemetry_last_daily_poll_time")
        val KEY_LAST_DAILY_POLL_STATUS = stringPreferencesKey("telemetry_last_daily_poll_status")
        val KEY_WALL_CONNECTOR_GEN3_CAR_ID = stringPreferencesKey("wall_connector_gen3_car_id")
        val KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID = stringPreferencesKey("wall_connector_universal_car_id")
        val KEY_LAST_CHARGER_SYNC_TIME = longPreferencesKey("telemetry_last_charger_sync_time")
        val KEY_LAST_CHARGER_SYNC_STATUS = stringPreferencesKey("telemetry_last_charger_sync_status")

        val KEY_SMARTCAR_APPLICATION_ID = stringPreferencesKey("smartcar_application_id")
        val KEY_SMARTCAR_CLIENT_ID = stringPreferencesKey("smartcar_client_id")
        val KEY_SMARTCAR_CLIENT_SECRET = stringPreferencesKey("smartcar_client_secret")
        val KEY_SMARTCAR_ACCESS_TOKEN = stringPreferencesKey("smartcar_access_token")
        val KEY_SMARTCAR_REFRESH_TOKEN = stringPreferencesKey("smartcar_refresh_token")
        val KEY_SMARTCAR_TOKEN_EXPIRY = longPreferencesKey("smartcar_token_expiry")
        val KEY_SMARTCAR_VEHICLE_ID = stringPreferencesKey("smartcar_vehicle_id")
        val KEY_SMARTCAR_USER_ID = stringPreferencesKey("smartcar_user_id")

        val KEY_TESLA_CLIENT_ID = stringPreferencesKey("tesla_client_id")
        val KEY_TESLA_CLIENT_SECRET = stringPreferencesKey("tesla_client_secret")
        val KEY_TESLA_ACCESS_TOKEN = stringPreferencesKey("tesla_access_token")
        val KEY_TESLA_REFRESH_TOKEN = stringPreferencesKey("tesla_refresh_token")
        val KEY_TESLA_TOKEN_EXPIRY = longPreferencesKey("tesla_token_expiry")

        val KEY_GAS_PRICE = floatPreferencesKey("comparison_gas_price")
        val KEY_BASELINE_MPG = floatPreferencesKey("comparison_baseline_mpg")
        val KEY_DEFAULT_ELECTRICITY_RATE = floatPreferencesKey("default_electricity_rate")
        val KEY_USE_CUSTOM_ELECTRICITY_RATE = booleanPreferencesKey("use_custom_electricity_rate")

        const val DEFAULT_GARAGE_NAME = "My Garage"
        const val DEFAULT_COOLDOWN_HOURS = 72
        const val DEFAULT_GAS_PRICE = 3.65f
        const val DEFAULT_BASELINE_MPG = 28.0f
        const val DEFAULT_ELECTRICITY_RATE = 0.16f
    }

    val gasPriceFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_GAS_PRICE] ?: DEFAULT_GAS_PRICE
    }

    val baselineMpgFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_BASELINE_MPG] ?: DEFAULT_BASELINE_MPG
    }

    val defaultElectricityRateFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_ELECTRICITY_RATE] ?: DEFAULT_ELECTRICITY_RATE
    }

    val useCustomElectricityRateFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_CUSTOM_ELECTRICITY_RATE] ?: false
    }

    val garageNameFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_GARAGE_NAME] ?: DEFAULT_GARAGE_NAME
    }

    val syncFolderIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SYNC_FOLDER_ID]
    }

    val syncFolderNameFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SYNC_FOLDER_NAME]
    }

    val geminiApiKeyFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY]
    }

    val autoDevApiKeyFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_DEV_API_KEY]
    }

    val importedFileIdsFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_IMPORTED_FILE_IDS] ?: emptySet()
    }

    val isMigrationCompleteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MIGRATION_V1_COMPLETE] ?: false
    }

    val isMigrationV2CompleteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MIGRATION_V2_COMPLETE] ?: false
    }

    val isMigrationV3CompleteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MIGRATION_V3_COMPLETE] ?: false
    }

    suspend fun getGarageName(): String = garageNameFlow.first()
    suspend fun getSyncFolderId(): String? = syncFolderIdFlow.first()
    suspend fun getSyncFolderName(): String? = syncFolderNameFlow.first()
    suspend fun getGeminiApiKey(): String? = geminiApiKeyFlow.first()
    suspend fun getAutoDevApiKey(): String? = autoDevApiKeyFlow.first()
    suspend fun getImportedFileIds(): Set<String> = importedFileIdsFlow.first()
    suspend fun isMigrationComplete(): Boolean = isMigrationCompleteFlow.first()
    suspend fun isMigrationV2Complete(): Boolean = isMigrationV2CompleteFlow.first()
    suspend fun isMigrationV3Complete(): Boolean = isMigrationV3CompleteFlow.first()

    suspend fun setGarageName(name: String) {
        val trimmed = name.trim()
        dataStore.edit { prefs ->
            if (trimmed.isEmpty() || trimmed == DEFAULT_GARAGE_NAME) {
                prefs.remove(KEY_GARAGE_NAME)
            } else {
                prefs[KEY_GARAGE_NAME] = trimmed
            }
        }
    }

    suspend fun setSyncFolder(folderId: String?, folderName: String?) {
        dataStore.edit { prefs ->
            if (folderId == null) {
                prefs.remove(KEY_SYNC_FOLDER_ID)
                prefs.remove(KEY_SYNC_FOLDER_NAME)
            } else {
                prefs[KEY_SYNC_FOLDER_ID] = folderId
                folderName?.let { prefs[KEY_SYNC_FOLDER_NAME] = it }
            }
        }
    }

    suspend fun setGeminiApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(KEY_GEMINI_API_KEY)
            } else {
                prefs[KEY_GEMINI_API_KEY] = key.trim()
            }
        }
    }

    suspend fun setAutoDevApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(KEY_AUTO_DEV_API_KEY)
            } else {
                prefs[KEY_AUTO_DEV_API_KEY] = key.trim()
            }
        }
    }

    suspend fun markFileAsImported(fileId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            current.add(fileId)
            prefs[KEY_IMPORTED_FILE_IDS] = current
        }
    }

    suspend fun unmarkFileAsImported(fileId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            if (current.remove(fileId)) {
                prefs[KEY_IMPORTED_FILE_IDS] = current
            }
        }
    }

    suspend fun setImportedFileIds(fileIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_IMPORTED_FILE_IDS] = fileIds
        }
    }

    suspend fun setMigrationComplete(complete: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[KEY_MIGRATION_V1_COMPLETE] = complete
        }
    }

    suspend fun setMigrationV2Complete(complete: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[KEY_MIGRATION_V2_COMPLETE] = complete
        }
    }

    suspend fun setMigrationV3Complete(complete: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[KEY_MIGRATION_V3_COMPLETE] = complete
        }
    }

    // Telemetry Flows & Accessors
    val wakePolicyFlow: Flow<WakeProtectionPolicy> = dataStore.data.map { prefs ->
        WakeProtectionPolicy.fromKey(prefs[KEY_WAKE_POLICY])
    }

    val syncCooldownHoursFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_SYNC_COOLDOWN_HOURS] ?: DEFAULT_COOLDOWN_HOURS
    }

    val dailyPollEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DAILY_POLL_ENABLED] ?: true
    }

    val lastDailyPollTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_DAILY_POLL_TIME] ?: 0L
    }

    val lastDailyPollStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_DAILY_POLL_STATUS]
    }

    val wallConnectorGen3CarIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_WALL_CONNECTOR_GEN3_CAR_ID]
    }

    val wallConnectorUniversalCarIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID]
    }

    val lastChargerSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CHARGER_SYNC_TIME] ?: 0L
    }

    val lastChargerSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CHARGER_SYNC_STATUS]
    }

    val smartcarClientIdFlow: Flow<String?> = dataStore.data.map { it[KEY_SMARTCAR_CLIENT_ID] }
    val smartcarConnectedFlow: Flow<Boolean> = dataStore.data.map {
        !it[KEY_SMARTCAR_VEHICLE_ID].isNullOrBlank() ||
            !it[KEY_SMARTCAR_USER_ID].isNullOrBlank() ||
            !it[KEY_SMARTCAR_REFRESH_TOKEN].isNullOrBlank()
    }

    val teslaClientIdFlow: Flow<String?> = dataStore.data.map { it[KEY_TESLA_CLIENT_ID] }
    val teslaConnectedFlow: Flow<Boolean> = dataStore.data.map {
        !it[KEY_TESLA_ACCESS_TOKEN].isNullOrBlank()
    }

    suspend fun getWakePolicy(): WakeProtectionPolicy = wakePolicyFlow.first()
    suspend fun getSyncCooldownHours(): Int = syncCooldownHoursFlow.first()

    suspend fun setWakePolicy(policy: WakeProtectionPolicy) {
        dataStore.edit { prefs ->
            prefs[KEY_WAKE_POLICY] = policy.key
        }
    }

    suspend fun setSyncCooldownHours(hours: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_COOLDOWN_HOURS] = hours
        }
    }

    suspend fun getDailyPollEnabled(): Boolean = dailyPollEnabledFlow.first()

    suspend fun setDailyPollEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_POLL_ENABLED] = enabled
        }
    }

    suspend fun recordDailyPollResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_DAILY_POLL_TIME] = timestampMs
            prefs[KEY_LAST_DAILY_POLL_STATUS] = status
        }
    }

    suspend fun getWallConnectorCarMapping(): Pair<String?, String?> = dataStore.data.map {
        Pair(it[KEY_WALL_CONNECTOR_GEN3_CAR_ID], it[KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID])
    }.first()

    suspend fun setWallConnectorCarMapping(gen3CarId: String?, universalCarId: String?) {
        dataStore.edit { prefs ->
            if (gen3CarId.isNullOrBlank()) {
                prefs.remove(KEY_WALL_CONNECTOR_GEN3_CAR_ID)
            } else {
                prefs[KEY_WALL_CONNECTOR_GEN3_CAR_ID] = gen3CarId.trim()
            }
            if (universalCarId.isNullOrBlank()) {
                prefs.remove(KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID)
            } else {
                prefs[KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID] = universalCarId.trim()
            }
        }
    }

    suspend fun getLastChargerSyncTime(): Long = lastChargerSyncTimeFlow.first()

    suspend fun recordChargerSyncResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHARGER_SYNC_TIME] = timestampMs
            prefs[KEY_LAST_CHARGER_SYNC_STATUS] = status
        }
    }

    suspend fun getSmartcarCredentials(): Pair<String?, String?> = dataStore.data.map {
        Pair(it[KEY_SMARTCAR_CLIENT_ID], it[KEY_SMARTCAR_CLIENT_SECRET])
    }.first()

    suspend fun setSmartcarCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) {
        dataStore.edit { prefs ->
            if (clientId.isNullOrBlank()) prefs.remove(KEY_SMARTCAR_CLIENT_ID)
            else prefs[KEY_SMARTCAR_CLIENT_ID] = clientId.trim()

            if (clientSecret.isNullOrBlank()) prefs.remove(KEY_SMARTCAR_CLIENT_SECRET)
            else prefs[KEY_SMARTCAR_CLIENT_SECRET] = clientSecret.trim()

            if (accessToken != null) {
                val trimmedToken = accessToken.trim()
                if (trimmedToken.isEmpty()) {
                    prefs.remove(KEY_SMARTCAR_ACCESS_TOKEN)
                    prefs.remove(KEY_SMARTCAR_TOKEN_EXPIRY)
                } else if (trimmedToken != prefs[KEY_SMARTCAR_ACCESS_TOKEN]) {
                    prefs[KEY_SMARTCAR_ACCESS_TOKEN] = trimmedToken
                    prefs.remove(KEY_SMARTCAR_TOKEN_EXPIRY)
                }
            }
        }
    }

    suspend fun saveSmartcarTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, vehicleId: String? = null) {
        val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) prefs[KEY_SMARTCAR_REFRESH_TOKEN] = refreshToken
            prefs[KEY_SMARTCAR_TOKEN_EXPIRY] = expiryMs
            if (!vehicleId.isNullOrBlank()) prefs[KEY_SMARTCAR_VEHICLE_ID] = vehicleId
        }
    }

    suspend fun getSmartcarTokens(): Triple<String?, String?, Long> = dataStore.data.map {
        Triple(it[KEY_SMARTCAR_ACCESS_TOKEN], it[KEY_SMARTCAR_REFRESH_TOKEN], it[KEY_SMARTCAR_TOKEN_EXPIRY] ?: 0L)
    }.first()

    suspend fun getSmartcarVehicleId(): String? = dataStore.data.map { it[KEY_SMARTCAR_VEHICLE_ID] }.first()

    suspend fun setSmartcarVehicleId(vehicleId: String, userId: String? = null) {
        dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_VEHICLE_ID] = vehicleId
            if (!userId.isNullOrBlank()) {
                prefs[KEY_SMARTCAR_USER_ID] = userId
            }
        }
    }

    suspend fun getSmartcarUserId(): String? = dataStore.data.map { it[KEY_SMARTCAR_USER_ID] }.first()

    suspend fun setSmartcarUserId(userId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_USER_ID] = userId
        }
    }

    suspend fun getSmartcarApplicationId(): String? = dataStore.data.map { it[KEY_SMARTCAR_APPLICATION_ID] }.first()

    suspend fun setSmartcarApplicationId(appId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_APPLICATION_ID] = appId
        }
    }

    suspend fun clearSmartcarVehicleConnection() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SMARTCAR_VEHICLE_ID)
            prefs.remove(KEY_SMARTCAR_USER_ID)
        }
    }

    suspend fun disconnectSmartcar() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SMARTCAR_ACCESS_TOKEN)
            prefs.remove(KEY_SMARTCAR_REFRESH_TOKEN)
            prefs.remove(KEY_SMARTCAR_TOKEN_EXPIRY)
            prefs.remove(KEY_SMARTCAR_VEHICLE_ID)
            prefs.remove(KEY_SMARTCAR_USER_ID)
        }
    }

    suspend fun getTeslaCredentials(): Pair<String?, String?> = dataStore.data.map {
        Pair(it[KEY_TESLA_CLIENT_ID], it[KEY_TESLA_CLIENT_SECRET])
    }.first()

    suspend fun setTeslaCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) {
        dataStore.edit { prefs ->
            if (clientId.isNullOrBlank()) prefs.remove(KEY_TESLA_CLIENT_ID)
            else prefs[KEY_TESLA_CLIENT_ID] = clientId.trim()

            if (clientSecret.isNullOrBlank()) prefs.remove(KEY_TESLA_CLIENT_SECRET)
            else prefs[KEY_TESLA_CLIENT_SECRET] = clientSecret.trim()

            if (accessToken != null) {
                val trimmedToken = accessToken.trim()
                if (trimmedToken.isEmpty()) {
                    prefs.remove(KEY_TESLA_ACCESS_TOKEN)
                    prefs.remove(KEY_TESLA_REFRESH_TOKEN)
                    prefs.remove(KEY_TESLA_TOKEN_EXPIRY)
                } else if (trimmedToken != prefs[KEY_TESLA_ACCESS_TOKEN]) {
                    prefs[KEY_TESLA_ACCESS_TOKEN] = trimmedToken
                    prefs.remove(KEY_TESLA_REFRESH_TOKEN)
                    prefs.remove(KEY_TESLA_TOKEN_EXPIRY)
                }
            }
        }
    }

    suspend fun saveTeslaTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        dataStore.edit { prefs ->
            prefs[KEY_TESLA_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) prefs[KEY_TESLA_REFRESH_TOKEN] = refreshToken
            prefs[KEY_TESLA_TOKEN_EXPIRY] = expiryMs
        }
    }

    suspend fun getTeslaTokens(): Triple<String?, String?, Long> = dataStore.data.map {
        Triple(it[KEY_TESLA_ACCESS_TOKEN], it[KEY_TESLA_REFRESH_TOKEN], it[KEY_TESLA_TOKEN_EXPIRY] ?: 0L)
    }.first()

    suspend fun disconnectTesla() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TESLA_ACCESS_TOKEN)
            prefs.remove(KEY_TESLA_REFRESH_TOKEN)
            prefs.remove(KEY_TESLA_TOKEN_EXPIRY)
        }
    }

    suspend fun setGasPrice(price: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_GAS_PRICE] = price
        }
    }

    suspend fun setBaselineMpg(mpg: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_BASELINE_MPG] = mpg
        }
    }

    suspend fun setDefaultElectricityRate(rate: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_ELECTRICITY_RATE] = rate
        }
    }

    suspend fun setCustomElectricityRate(rate: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_ELECTRICITY_RATE] = rate
            prefs[KEY_USE_CUSTOM_ELECTRICITY_RATE] = true
        }
    }

    suspend fun setUseCustomElectricityRate(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_USE_CUSTOM_ELECTRICITY_RATE] = enabled
        }
    }
}
