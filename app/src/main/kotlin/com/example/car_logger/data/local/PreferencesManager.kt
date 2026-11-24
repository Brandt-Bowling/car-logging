package com.example.car_logger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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
    AWAKE_ONLY("awake_only", "When awake"),
    POST_DRIVE("post_drive", "After driving"),
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
        val KEY_CHARGER_SYNC_ENABLED = booleanPreferencesKey("telemetry_charger_sync_enabled")
        val KEY_CHARGER_SYNC_HOUR = intPreferencesKey("telemetry_charger_sync_hour")
        val KEY_CHARGER_SYNC_MINUTE = intPreferencesKey("telemetry_charger_sync_minute")
        val KEY_LAST_CHARGER_SYNC_TIME = longPreferencesKey("telemetry_last_charger_sync_time")
        val KEY_LAST_CHARGER_SYNC_STATUS = stringPreferencesKey("telemetry_last_charger_sync_status")

        // Work Charging Automation & Optional Work GPS Geofence
        val KEY_WORK_CHARGING_SYNC_ENABLED = booleanPreferencesKey("telemetry_work_charging_sync_enabled")
        val KEY_WORK_CHARGING_WINDOW_START_HOUR = intPreferencesKey("telemetry_work_charging_window_start_hour")
        val KEY_WORK_CHARGING_WINDOW_START_MINUTE = intPreferencesKey("telemetry_work_charging_window_start_minute")
        val KEY_WORK_CHARGING_WINDOW_END_HOUR = intPreferencesKey("telemetry_work_charging_window_end_hour")
        val KEY_WORK_CHARGING_WINDOW_END_MINUTE = intPreferencesKey("telemetry_work_charging_window_end_minute")
        val KEY_WORK_CHARGING_WEEKDAYS_ONLY = booleanPreferencesKey("telemetry_work_charging_weekdays_only")
        val KEY_LAST_WORK_CHARGING_SYNC_TIME = longPreferencesKey("telemetry_last_work_charging_sync_time")
        val KEY_LAST_WORK_CHARGING_SYNC_STATUS = stringPreferencesKey("telemetry_last_work_charging_sync_status")
        val KEY_WORK_LOCATION_LAT = doublePreferencesKey("telemetry_work_location_lat")
        val KEY_WORK_LOCATION_LNG = doublePreferencesKey("telemetry_work_location_lng")
        val KEY_WORK_LOCATION_RADIUS_METERS = intPreferencesKey("telemetry_work_location_radius_meters")

        // Tesla charge_energy_added Deduplication Watermark
        val KEY_TESLA_LAST_CHARGE_ENERGY_ADDED = doublePreferencesKey("telemetry_tesla_last_charge_energy_added")
        val KEY_TESLA_LAST_CHARGE_SEEN_DATE = stringPreferencesKey("telemetry_tesla_last_charge_seen_date")
        val KEY_TESLA_LAST_CHARGE_SEEN_VENUE = stringPreferencesKey("telemetry_tesla_last_charge_seen_venue")

        // Paid Tesla Supercharger History Sync
        val KEY_SUPERCHARGER_SYNC_ENABLED = booleanPreferencesKey("telemetry_supercharger_sync_enabled")
        val KEY_LAST_SUPERCHARGER_SYNC_TIME = longPreferencesKey("telemetry_last_supercharger_sync_time")
        val KEY_LAST_SUPERCHARGER_SYNC_STATUS = stringPreferencesKey("telemetry_last_supercharger_sync_status")

        // Smartcar v3 DC Charging Sync & Capability Cache
        val KEY_SMARTCAR_DC_SYNC_ENABLED = booleanPreferencesKey("telemetry_smartcar_dc_sync_enabled")
        val KEY_SMARTCAR_DC_CAPABILITY = stringPreferencesKey("telemetry_smartcar_dc_capability")
        val KEY_LAST_SMARTCAR_DC_SYNC_TIME = longPreferencesKey("telemetry_last_smartcar_dc_sync_time")
        val KEY_LAST_SMARTCAR_DC_SYNC_STATUS = stringPreferencesKey("telemetry_last_smartcar_dc_sync_status")

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
        const val DEFAULT_CHARGER_SYNC_HOUR = 8
        const val DEFAULT_CHARGER_SYNC_MINUTE = 0
        const val DEFAULT_WORK_CHARGING_WINDOW_START_HOUR = 12
        const val DEFAULT_WORK_CHARGING_WINDOW_START_MINUTE = 0
        const val DEFAULT_WORK_CHARGING_WINDOW_END_HOUR = 17
        const val DEFAULT_WORK_CHARGING_WINDOW_END_MINUTE = 0
        const val DEFAULT_WORK_LOCATION_RADIUS_METERS = 500
        const val DEFAULT_GAS_PRICE = 3.65f
        const val DEFAULT_BASELINE_MPG = 28.0f
        const val DEFAULT_ELECTRICITY_RATE = 0.16f

        const val SMARTCAR_CAPABILITY_UNKNOWN = "UNKNOWN"
        const val SMARTCAR_CAPABILITY_CHARGE_RECORDS = "CHARGE_RECORDS"
        const val SMARTCAR_CAPABILITY_LIVE_ONLY = "LIVE_ONLY"
        const val SMARTCAR_CAPABILITY_MISSING_PERMISSION = "MISSING_PERMISSION"
        const val SMARTCAR_CAPABILITY_UNSUPPORTED = "UNSUPPORTED"
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

    val chargerSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CHARGER_SYNC_ENABLED] ?: true
    }

    val chargerSyncHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_CHARGER_SYNC_HOUR] ?: DEFAULT_CHARGER_SYNC_HOUR).coerceIn(0, 23)
    }

    val chargerSyncMinuteFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_CHARGER_SYNC_MINUTE] ?: DEFAULT_CHARGER_SYNC_MINUTE).coerceIn(0, 59)
    }

    val lastChargerSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CHARGER_SYNC_TIME] ?: 0L
    }

    val lastChargerSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CHARGER_SYNC_STATUS]
    }

    // Work Charging Automation & Optional Work GPS Flows
    val workChargingSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WORK_CHARGING_SYNC_ENABLED] ?: true
    }

    val workChargingWindowStartHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_WORK_CHARGING_WINDOW_START_HOUR] ?: DEFAULT_WORK_CHARGING_WINDOW_START_HOUR).coerceIn(0, 23)
    }

    val workChargingWindowStartMinuteFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_WORK_CHARGING_WINDOW_START_MINUTE] ?: DEFAULT_WORK_CHARGING_WINDOW_START_MINUTE).coerceIn(0, 59)
    }

    val workChargingWindowEndHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_WORK_CHARGING_WINDOW_END_HOUR] ?: DEFAULT_WORK_CHARGING_WINDOW_END_HOUR).coerceIn(0, 23)
    }

    val workChargingWindowEndMinuteFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_WORK_CHARGING_WINDOW_END_MINUTE] ?: DEFAULT_WORK_CHARGING_WINDOW_END_MINUTE).coerceIn(0, 59)
    }

    val workChargingWeekdaysOnlyFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WORK_CHARGING_WEEKDAYS_ONLY] ?: true
    }

    val lastWorkChargingSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_WORK_CHARGING_SYNC_TIME] ?: 0L
    }

    val lastWorkChargingSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_WORK_CHARGING_SYNC_STATUS]
    }

    val workLocationLatFlow: Flow<Double?> = dataStore.data.map { prefs ->
        prefs[KEY_WORK_LOCATION_LAT]
    }

    val workLocationLngFlow: Flow<Double?> = dataStore.data.map { prefs ->
        prefs[KEY_WORK_LOCATION_LNG]
    }

    val workLocationRadiusMetersFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_WORK_LOCATION_RADIUS_METERS] ?: DEFAULT_WORK_LOCATION_RADIUS_METERS).coerceIn(100, 2000)
    }

    // Paid Tesla Supercharger Sync Flows
    val superchargerSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SUPERCHARGER_SYNC_ENABLED] ?: true
    }

    val lastSuperchargerSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SUPERCHARGER_SYNC_TIME] ?: 0L
    }

    val lastSuperchargerSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SUPERCHARGER_SYNC_STATUS]
    }

    // Smartcar v3 DC Charging Flows
    val smartcarDcSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SMARTCAR_DC_SYNC_ENABLED] ?: true
    }

    val smartcarDcCapabilityFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SMARTCAR_DC_CAPABILITY] ?: SMARTCAR_CAPABILITY_UNKNOWN
    }

    val lastSmartcarDcSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SMARTCAR_DC_SYNC_TIME] ?: 0L
    }

    val lastSmartcarDcSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_SMARTCAR_DC_SYNC_STATUS]
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
    val teslaAccessTokenFlow: Flow<String?> = dataStore.data.map { it[KEY_TESLA_ACCESS_TOKEN] }

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

    suspend fun getChargerSyncEnabled(): Boolean = chargerSyncEnabledFlow.first()

    suspend fun getChargerSyncHour(): Int = chargerSyncHourFlow.first()

    suspend fun getChargerSyncMinute(): Int = chargerSyncMinuteFlow.first()

    suspend fun setChargerSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CHARGER_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setChargerSyncTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_CHARGER_SYNC_HOUR] = hour.coerceIn(0, 23)
            prefs[KEY_CHARGER_SYNC_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun getLastChargerSyncTime(): Long = lastChargerSyncTimeFlow.first()

    suspend fun recordChargerSyncResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHARGER_SYNC_TIME] = timestampMs
            prefs[KEY_LAST_CHARGER_SYNC_STATUS] = status
        }
    }

    // Work Charging Accessors
    suspend fun getWorkChargingSyncEnabled(): Boolean = workChargingSyncEnabledFlow.first()

    suspend fun getWorkChargingWindowStartHour(): Int = workChargingWindowStartHourFlow.first()

    suspend fun getWorkChargingWindowStartMinute(): Int = workChargingWindowStartMinuteFlow.first()

    suspend fun getWorkChargingWindowEndHour(): Int = workChargingWindowEndHourFlow.first()

    suspend fun getWorkChargingWindowEndMinute(): Int = workChargingWindowEndMinuteFlow.first()

    suspend fun getWorkChargingWeekdaysOnly(): Boolean = workChargingWeekdaysOnlyFlow.first()

    suspend fun setWorkChargingSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WORK_CHARGING_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setWorkChargingWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val startH = startHour.coerceIn(0, 23)
        val startM = startMinute.coerceIn(0, 59)
        val endH = endHour.coerceIn(0, 23)
        val endM = endMinute.coerceIn(0, 59)
        val startTotal = startH * 60 + startM
        val endTotal = endH * 60 + endM
        val (normStartH, normStartM, normEndH, normEndM) = if (endTotal < startTotal) {
            listOf(endH, endM, startH, startM)
        } else {
            listOf(startH, startM, endH, endM)
        }
        dataStore.edit { prefs ->
            prefs[KEY_WORK_CHARGING_WINDOW_START_HOUR] = normStartH
            prefs[KEY_WORK_CHARGING_WINDOW_START_MINUTE] = normStartM
            prefs[KEY_WORK_CHARGING_WINDOW_END_HOUR] = normEndH
            prefs[KEY_WORK_CHARGING_WINDOW_END_MINUTE] = normEndM
        }
    }

    suspend fun setWorkChargingWeekdaysOnly(weekdaysOnly: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WORK_CHARGING_WEEKDAYS_ONLY] = weekdaysOnly
        }
    }

    suspend fun recordWorkChargingSyncResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_WORK_CHARGING_SYNC_TIME] = timestampMs
            prefs[KEY_LAST_WORK_CHARGING_SYNC_STATUS] = status
        }
    }

    suspend fun getWorkLocation(): Triple<Double?, Double?, Int> = dataStore.data.map { prefs ->
        Triple(
            prefs[KEY_WORK_LOCATION_LAT],
            prefs[KEY_WORK_LOCATION_LNG],
            (prefs[KEY_WORK_LOCATION_RADIUS_METERS] ?: DEFAULT_WORK_LOCATION_RADIUS_METERS).coerceIn(100, 2000)
        )
    }.first()

    suspend fun setWorkLocation(lat: Double, lng: Double, radiusMeters: Int = DEFAULT_WORK_LOCATION_RADIUS_METERS) {
        dataStore.edit { prefs ->
            prefs[KEY_WORK_LOCATION_LAT] = lat
            prefs[KEY_WORK_LOCATION_LNG] = lng
            prefs[KEY_WORK_LOCATION_RADIUS_METERS] = radiusMeters.coerceIn(100, 2000)
        }
    }

    suspend fun clearWorkLocation() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_WORK_LOCATION_LAT)
            prefs.remove(KEY_WORK_LOCATION_LNG)
        }
    }

    suspend fun getTeslaChargeWatermark(): Triple<Double, String?, String?> = dataStore.data.map { prefs ->
        Triple(
            prefs[KEY_TESLA_LAST_CHARGE_ENERGY_ADDED] ?: -1.0,
            prefs[KEY_TESLA_LAST_CHARGE_SEEN_DATE],
            prefs[KEY_TESLA_LAST_CHARGE_SEEN_VENUE]
        )
    }.first()

    suspend fun setTeslaChargeWatermark(energyAddedKwh: Double, dateIso: String, venue: String) {
        dataStore.edit { prefs ->
            prefs[KEY_TESLA_LAST_CHARGE_ENERGY_ADDED] = energyAddedKwh
            prefs[KEY_TESLA_LAST_CHARGE_SEEN_DATE] = dateIso
            prefs[KEY_TESLA_LAST_CHARGE_SEEN_VENUE] = venue
        }
    }

    // Supercharger Sync Accessors
    suspend fun getSuperchargerSyncEnabled(): Boolean = superchargerSyncEnabledFlow.first()

    suspend fun setSuperchargerSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SUPERCHARGER_SYNC_ENABLED] = enabled
        }
    }

    suspend fun getLastSuperchargerSyncTime(): Long = lastSuperchargerSyncTimeFlow.first()

    suspend fun recordSuperchargerSyncResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SUPERCHARGER_SYNC_TIME] = timestampMs
            prefs[KEY_LAST_SUPERCHARGER_SYNC_STATUS] = status
        }
    }

    // Smartcar DC Charging Accessors
    suspend fun getSmartcarDcSyncEnabled(): Boolean = smartcarDcSyncEnabledFlow.first()

    suspend fun setSmartcarDcSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_DC_SYNC_ENABLED] = enabled
        }
    }

    suspend fun getSmartcarDcCapability(): String = smartcarDcCapabilityFlow.first()

    suspend fun setSmartcarDcCapability(capability: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SMARTCAR_DC_CAPABILITY] = capability
        }
    }

    suspend fun recordSmartcarDcSyncResult(timestampMs: Long, status: String, capability: String? = null) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SMARTCAR_DC_SYNC_TIME] = timestampMs
            prefs[KEY_LAST_SMARTCAR_DC_SYNC_STATUS] = status
            if (!capability.isNullOrBlank()) {
                prefs[KEY_SMARTCAR_DC_CAPABILITY] = capability
            }
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
            prefs.remove(KEY_SMARTCAR_DC_CAPABILITY)
        }
    }

    suspend fun disconnectSmartcar() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SMARTCAR_ACCESS_TOKEN)
            prefs.remove(KEY_SMARTCAR_REFRESH_TOKEN)
            prefs.remove(KEY_SMARTCAR_TOKEN_EXPIRY)
            prefs.remove(KEY_SMARTCAR_VEHICLE_ID)
            prefs.remove(KEY_SMARTCAR_USER_ID)
            prefs.remove(KEY_SMARTCAR_DC_CAPABILITY)
            prefs.remove(KEY_LAST_SMARTCAR_DC_SYNC_STATUS)
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
