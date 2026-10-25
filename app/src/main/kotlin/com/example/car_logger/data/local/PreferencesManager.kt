package com.example.car_logger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

class PreferencesManager(private val context: Context) {

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

        val KEY_SMARTCAR_CLIENT_ID = stringPreferencesKey("smartcar_client_id")
        val KEY_SMARTCAR_CLIENT_SECRET = stringPreferencesKey("smartcar_client_secret")
        val KEY_SMARTCAR_ACCESS_TOKEN = stringPreferencesKey("smartcar_access_token")
        val KEY_SMARTCAR_REFRESH_TOKEN = stringPreferencesKey("smartcar_refresh_token")
        val KEY_SMARTCAR_TOKEN_EXPIRY = longPreferencesKey("smartcar_token_expiry")
        val KEY_SMARTCAR_VEHICLE_ID = stringPreferencesKey("smartcar_vehicle_id")

        val KEY_TESLA_CLIENT_ID = stringPreferencesKey("tesla_client_id")
        val KEY_TESLA_CLIENT_SECRET = stringPreferencesKey("tesla_client_secret")
        val KEY_TESLA_ACCESS_TOKEN = stringPreferencesKey("tesla_access_token")
        val KEY_TESLA_REFRESH_TOKEN = stringPreferencesKey("tesla_refresh_token")
        val KEY_TESLA_TOKEN_EXPIRY = longPreferencesKey("tesla_token_expiry")

        const val DEFAULT_GARAGE_NAME = "My Garage"
        const val DEFAULT_COOLDOWN_HOURS = 72
    }

    val garageNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GARAGE_NAME] ?: DEFAULT_GARAGE_NAME
    }

    val syncFolderIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_FOLDER_ID]
    }

    val syncFolderNameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_FOLDER_NAME]
    }

    val geminiApiKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY]
    }

    val autoDevApiKeyFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_DEV_API_KEY]
    }

    val importedFileIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_IMPORTED_FILE_IDS] ?: emptySet()
    }

    val isMigrationCompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MIGRATION_V1_COMPLETE] ?: false
    }

    val isMigrationV2CompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MIGRATION_V2_COMPLETE] ?: false
    }

    val isMigrationV3CompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
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
        context.dataStore.edit { prefs ->
            if (trimmed.isEmpty() || trimmed == DEFAULT_GARAGE_NAME) {
                prefs.remove(KEY_GARAGE_NAME)
            } else {
                prefs[KEY_GARAGE_NAME] = trimmed
            }
        }
    }

    suspend fun setSyncFolder(folderId: String?, folderName: String?) {
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(KEY_GEMINI_API_KEY)
            } else {
                prefs[KEY_GEMINI_API_KEY] = key.trim()
            }
        }
    }

    suspend fun setAutoDevApiKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(KEY_AUTO_DEV_API_KEY)
            } else {
                prefs[KEY_AUTO_DEV_API_KEY] = key.trim()
            }
        }
    }

    suspend fun markFileAsImported(fileId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            current.add(fileId)
            prefs[KEY_IMPORTED_FILE_IDS] = current
        }
    }

    suspend fun unmarkFileAsImported(fileId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_IMPORTED_FILE_IDS]?.toMutableSet() ?: mutableSetOf()
            if (current.remove(fileId)) {
                prefs[KEY_IMPORTED_FILE_IDS] = current
            }
        }
    }

    suspend fun setImportedFileIds(fileIds: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IMPORTED_FILE_IDS] = fileIds
        }
    }

    suspend fun setMigrationComplete(complete: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIGRATION_V1_COMPLETE] = complete
        }
    }

    suspend fun setMigrationV2Complete(complete: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIGRATION_V2_COMPLETE] = complete
        }
    }

    suspend fun setMigrationV3Complete(complete: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIGRATION_V3_COMPLETE] = complete
        }
    }

    // Telemetry Flows & Accessors
    val wakePolicyFlow: Flow<WakeProtectionPolicy> = context.dataStore.data.map { prefs ->
        WakeProtectionPolicy.fromKey(prefs[KEY_WAKE_POLICY])
    }

    val syncCooldownHoursFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYNC_COOLDOWN_HOURS] ?: DEFAULT_COOLDOWN_HOURS
    }

    val smartcarClientIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_SMARTCAR_CLIENT_ID] }
    val smartcarConnectedFlow: Flow<Boolean> = context.dataStore.data.map {
        !it[KEY_SMARTCAR_ACCESS_TOKEN].isNullOrBlank() ||
            (com.example.car_logger.BuildConfig.SMARTCAR_CLIENT_ID.isNotBlank() &&
                com.example.car_logger.BuildConfig.SMARTCAR_CLIENT_SECRET.isNotBlank())
    }

    val teslaClientIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_TESLA_CLIENT_ID] }
    val teslaConnectedFlow: Flow<Boolean> = context.dataStore.data.map {
        !it[KEY_TESLA_ACCESS_TOKEN].isNullOrBlank()
    }

    suspend fun getWakePolicy(): WakeProtectionPolicy = wakePolicyFlow.first()
    suspend fun getSyncCooldownHours(): Int = syncCooldownHoursFlow.first()

    suspend fun setWakePolicy(policy: WakeProtectionPolicy) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WAKE_POLICY] = policy.key
        }
    }

    suspend fun setSyncCooldownHours(hours: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SYNC_COOLDOWN_HOURS] = hours
        }
    }

    suspend fun getSmartcarCredentials(): Pair<String?, String?> = context.dataStore.data.map {
        Pair(it[KEY_SMARTCAR_CLIENT_ID], it[KEY_SMARTCAR_CLIENT_SECRET])
    }.first()

    suspend fun setSmartcarCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) {
        context.dataStore.edit { prefs ->
            if (clientId.isNullOrBlank()) prefs.remove(KEY_SMARTCAR_CLIENT_ID)
            else prefs[KEY_SMARTCAR_CLIENT_ID] = clientId.trim()

            if (clientSecret.isNullOrBlank()) prefs.remove(KEY_SMARTCAR_CLIENT_SECRET)
            else prefs[KEY_SMARTCAR_CLIENT_SECRET] = clientSecret.trim()

            if (accessToken != null) {
                if (accessToken.isBlank()) prefs.remove(KEY_SMARTCAR_ACCESS_TOKEN)
                else prefs[KEY_SMARTCAR_ACCESS_TOKEN] = accessToken.trim()
            }
        }
    }

    suspend fun saveSmartcarTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, vehicleId: String? = null) {
        val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        context.dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) prefs[KEY_SMARTCAR_REFRESH_TOKEN] = refreshToken
            prefs[KEY_SMARTCAR_TOKEN_EXPIRY] = expiryMs
            if (!vehicleId.isNullOrBlank()) prefs[KEY_SMARTCAR_VEHICLE_ID] = vehicleId
        }
    }

    suspend fun getSmartcarTokens(): Triple<String?, String?, Long> = context.dataStore.data.map {
        Triple(it[KEY_SMARTCAR_ACCESS_TOKEN], it[KEY_SMARTCAR_REFRESH_TOKEN], it[KEY_SMARTCAR_TOKEN_EXPIRY] ?: 0L)
    }.first()

    suspend fun getSmartcarVehicleId(): String? = context.dataStore.data.map { it[KEY_SMARTCAR_VEHICLE_ID] }.first()

    suspend fun setSmartcarVehicleId(vehicleId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_VEHICLE_ID] = vehicleId
        }
    }

    suspend fun disconnectSmartcar() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SMARTCAR_ACCESS_TOKEN)
            prefs.remove(KEY_SMARTCAR_REFRESH_TOKEN)
            prefs.remove(KEY_SMARTCAR_TOKEN_EXPIRY)
            prefs.remove(KEY_SMARTCAR_VEHICLE_ID)
        }
    }

    suspend fun getTeslaCredentials(): Pair<String?, String?> = context.dataStore.data.map {
        Pair(it[KEY_TESLA_CLIENT_ID], it[KEY_TESLA_CLIENT_SECRET])
    }.first()

    suspend fun setTeslaCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) {
        context.dataStore.edit { prefs ->
            if (clientId.isNullOrBlank()) prefs.remove(KEY_TESLA_CLIENT_ID)
            else prefs[KEY_TESLA_CLIENT_ID] = clientId.trim()

            if (clientSecret.isNullOrBlank()) prefs.remove(KEY_TESLA_CLIENT_SECRET)
            else prefs[KEY_TESLA_CLIENT_SECRET] = clientSecret.trim()

            if (accessToken != null) {
                if (accessToken.isBlank()) prefs.remove(KEY_TESLA_ACCESS_TOKEN)
                else prefs[KEY_TESLA_ACCESS_TOKEN] = accessToken.trim()
            }
        }
    }

    suspend fun saveTeslaTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        val expiryMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        context.dataStore.edit { prefs ->
            prefs[KEY_TESLA_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) prefs[KEY_TESLA_REFRESH_TOKEN] = refreshToken
            prefs[KEY_TESLA_TOKEN_EXPIRY] = expiryMs
        }
    }

    suspend fun getTeslaTokens(): Triple<String?, String?, Long> = context.dataStore.data.map {
        Triple(it[KEY_TESLA_ACCESS_TOKEN], it[KEY_TESLA_REFRESH_TOKEN], it[KEY_TESLA_TOKEN_EXPIRY] ?: 0L)
    }.first()

    suspend fun disconnectTesla() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TESLA_ACCESS_TOKEN)
            prefs.remove(KEY_TESLA_REFRESH_TOKEN)
            prefs.remove(KEY_TESLA_TOKEN_EXPIRY)
        }
    }
}
