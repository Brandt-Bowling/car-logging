package com.example.car_logger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.car_logger.data.local.prefs.ChargingSyncPreferencesStore
import com.example.car_logger.data.local.prefs.GeneralPreferencesStore
import com.example.car_logger.data.local.prefs.PricingPreferencesStore
import com.example.car_logger.data.local.prefs.TelemetryPreferencesStore
import kotlinx.coroutines.flow.Flow

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

    private val generalStore = GeneralPreferencesStore(dataStore)
    private val telemetryStore = TelemetryPreferencesStore(dataStore)
    private val chargingSyncStore = ChargingSyncPreferencesStore(dataStore)
    private val pricingStore = PricingPreferencesStore(dataStore)

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

    // Pricing
    val gasPriceFlow: Flow<Float> get() = pricingStore.gasPriceFlow
    val baselineMpgFlow: Flow<Float> get() = pricingStore.baselineMpgFlow
    val defaultElectricityRateFlow: Flow<Float> get() = pricingStore.defaultElectricityRateFlow
    val useCustomElectricityRateFlow: Flow<Boolean> get() = pricingStore.useCustomElectricityRateFlow
    suspend fun setGasPrice(price: Float) = pricingStore.setGasPrice(price)
    suspend fun setBaselineMpg(mpg: Float) = pricingStore.setBaselineMpg(mpg)
    suspend fun setDefaultElectricityRate(rate: Float) = pricingStore.setDefaultElectricityRate(rate)
    suspend fun setCustomElectricityRate(rate: Float) = pricingStore.setCustomElectricityRate(rate)
    suspend fun setUseCustomElectricityRate(enabled: Boolean) = pricingStore.setUseCustomElectricityRate(enabled)

    // General
    val garageNameFlow: Flow<String> get() = generalStore.garageNameFlow
    val syncFolderIdFlow: Flow<String?> get() = generalStore.syncFolderIdFlow
    val syncFolderNameFlow: Flow<String?> get() = generalStore.syncFolderNameFlow
    val geminiApiKeyFlow: Flow<String?> get() = generalStore.geminiApiKeyFlow
    val autoDevApiKeyFlow: Flow<String?> get() = generalStore.autoDevApiKeyFlow
    val importedFileIdsFlow: Flow<Set<String>> get() = generalStore.importedFileIdsFlow
    val isMigrationCompleteFlow: Flow<Boolean> get() = generalStore.isMigrationCompleteFlow
    val isMigrationV2CompleteFlow: Flow<Boolean> get() = generalStore.isMigrationV2CompleteFlow
    val isMigrationV3CompleteFlow: Flow<Boolean> get() = generalStore.isMigrationV3CompleteFlow
    suspend fun getGarageName(): String = generalStore.getGarageName()
    suspend fun getSyncFolderId(): String? = generalStore.getSyncFolderId()
    suspend fun getSyncFolderName(): String? = generalStore.getSyncFolderName()
    suspend fun getGeminiApiKey(): String? = generalStore.getGeminiApiKey()
    suspend fun getAutoDevApiKey(): String? = generalStore.getAutoDevApiKey()
    suspend fun getImportedFileIds(): Set<String> = generalStore.getImportedFileIds()
    suspend fun isMigrationComplete(): Boolean = generalStore.isMigrationComplete()
    suspend fun isMigrationV2Complete(): Boolean = generalStore.isMigrationV2Complete()
    suspend fun isMigrationV3Complete(): Boolean = generalStore.isMigrationV3Complete()
    suspend fun setGarageName(name: String) = generalStore.setGarageName(name)
    suspend fun setSyncFolder(folderId: String?, folderName: String?) = generalStore.setSyncFolder(folderId, folderName)
    suspend fun setGeminiApiKey(key: String?) = generalStore.setGeminiApiKey(key)
    suspend fun setAutoDevApiKey(key: String?) = generalStore.setAutoDevApiKey(key)
    suspend fun markFileAsImported(fileId: String) = generalStore.markFileAsImported(fileId)
    suspend fun unmarkFileAsImported(fileId: String) = generalStore.unmarkFileAsImported(fileId)
    suspend fun setImportedFileIds(fileIds: Set<String>) = generalStore.setImportedFileIds(fileIds)
    suspend fun setMigrationComplete(complete: Boolean = true) = generalStore.setMigrationComplete(complete)
    suspend fun setMigrationV2Complete(complete: Boolean = true) = generalStore.setMigrationV2Complete(complete)
    suspend fun setMigrationV3Complete(complete: Boolean = true) = generalStore.setMigrationV3Complete(complete)

    // Telemetry & OAuth
    val wakePolicyFlow: Flow<WakeProtectionPolicy> get() = telemetryStore.wakePolicyFlow
    val syncCooldownHoursFlow: Flow<Int> get() = telemetryStore.syncCooldownHoursFlow
    val dailyPollEnabledFlow: Flow<Boolean> get() = telemetryStore.dailyPollEnabledFlow
    val lastDailyPollTimeFlow: Flow<Long> get() = telemetryStore.lastDailyPollTimeFlow
    val lastDailyPollStatusFlow: Flow<String?> get() = telemetryStore.lastDailyPollStatusFlow
    val smartcarClientIdFlow: Flow<String?> get() = telemetryStore.smartcarClientIdFlow
    val smartcarConnectedFlow: Flow<Boolean> get() = telemetryStore.smartcarConnectedFlow
    val teslaClientIdFlow: Flow<String?> get() = telemetryStore.teslaClientIdFlow
    val teslaConnectedFlow: Flow<Boolean> get() = telemetryStore.teslaConnectedFlow
    val teslaAccessTokenFlow: Flow<String?> get() = telemetryStore.teslaAccessTokenFlow
    suspend fun getWakePolicy(): WakeProtectionPolicy = telemetryStore.getWakePolicy()
    suspend fun getSyncCooldownHours(): Int = telemetryStore.getSyncCooldownHours()
    suspend fun setWakePolicy(policy: WakeProtectionPolicy) = telemetryStore.setWakePolicy(policy)
    suspend fun setSyncCooldownHours(hours: Int) = telemetryStore.setSyncCooldownHours(hours)
    suspend fun getDailyPollEnabled(): Boolean = telemetryStore.getDailyPollEnabled()
    suspend fun setDailyPollEnabled(enabled: Boolean) = telemetryStore.setDailyPollEnabled(enabled)
    suspend fun recordDailyPollResult(timestampMs: Long, status: String) = telemetryStore.recordDailyPollResult(timestampMs, status)
    suspend fun getSmartcarCredentials(): Pair<String?, String?> = telemetryStore.getSmartcarCredentials()
    suspend fun setSmartcarCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) =
        telemetryStore.setSmartcarCredentials(clientId, clientSecret, accessToken)
    suspend fun saveSmartcarTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, vehicleId: String? = null) =
        telemetryStore.saveSmartcarTokens(accessToken, refreshToken, expiresInSeconds, vehicleId)
    suspend fun getSmartcarTokens(): Triple<String?, String?, Long> = telemetryStore.getSmartcarTokens()
    suspend fun getSmartcarVehicleId(): String? = telemetryStore.getSmartcarVehicleId()
    suspend fun setSmartcarVehicleId(vehicleId: String, userId: String? = null) = telemetryStore.setSmartcarVehicleId(vehicleId, userId)
    suspend fun getSmartcarUserId(): String? = telemetryStore.getSmartcarUserId()
    suspend fun setSmartcarUserId(userId: String) = telemetryStore.setSmartcarUserId(userId)
    suspend fun getSmartcarApplicationId(): String? = telemetryStore.getSmartcarApplicationId()
    suspend fun setSmartcarApplicationId(appId: String) = telemetryStore.setSmartcarApplicationId(appId)
    suspend fun clearSmartcarVehicleConnection() = telemetryStore.clearSmartcarVehicleConnection()
    suspend fun disconnectSmartcar() = telemetryStore.disconnectSmartcar()
    suspend fun getTeslaCredentials(): Pair<String?, String?> = telemetryStore.getTeslaCredentials()
    suspend fun setTeslaCredentials(clientId: String?, clientSecret: String?, accessToken: String? = null) =
        telemetryStore.setTeslaCredentials(clientId, clientSecret, accessToken)
    suspend fun saveTeslaTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) =
        telemetryStore.saveTeslaTokens(accessToken, refreshToken, expiresInSeconds)
    suspend fun getTeslaTokens(): Triple<String?, String?, Long> = telemetryStore.getTeslaTokens()
    suspend fun disconnectTesla() = telemetryStore.disconnectTesla()

    // Charging Sync
    val wallConnectorGen3CarIdFlow: Flow<String?> get() = chargingSyncStore.wallConnectorGen3CarIdFlow
    val wallConnectorUniversalCarIdFlow: Flow<String?> get() = chargingSyncStore.wallConnectorUniversalCarIdFlow
    val chargerSyncEnabledFlow: Flow<Boolean> get() = chargingSyncStore.chargerSyncEnabledFlow
    val chargerSyncHourFlow: Flow<Int> get() = chargingSyncStore.chargerSyncHourFlow
    val chargerSyncMinuteFlow: Flow<Int> get() = chargingSyncStore.chargerSyncMinuteFlow
    val lastChargerSyncTimeFlow: Flow<Long> get() = chargingSyncStore.lastChargerSyncTimeFlow
    val lastChargerSyncStatusFlow: Flow<String?> get() = chargingSyncStore.lastChargerSyncStatusFlow
    val workChargingSyncEnabledFlow: Flow<Boolean> get() = chargingSyncStore.workChargingSyncEnabledFlow
    val workChargingWindowStartHourFlow: Flow<Int> get() = chargingSyncStore.workChargingWindowStartHourFlow
    val workChargingWindowStartMinuteFlow: Flow<Int> get() = chargingSyncStore.workChargingWindowStartMinuteFlow
    val workChargingWindowEndHourFlow: Flow<Int> get() = chargingSyncStore.workChargingWindowEndHourFlow
    val workChargingWindowEndMinuteFlow: Flow<Int> get() = chargingSyncStore.workChargingWindowEndMinuteFlow
    val workChargingWeekdaysOnlyFlow: Flow<Boolean> get() = chargingSyncStore.workChargingWeekdaysOnlyFlow
    val lastWorkChargingSyncTimeFlow: Flow<Long> get() = chargingSyncStore.lastWorkChargingSyncTimeFlow
    val lastWorkChargingSyncStatusFlow: Flow<String?> get() = chargingSyncStore.lastWorkChargingSyncStatusFlow
    val workLocationLatFlow: Flow<Double?> get() = chargingSyncStore.workLocationLatFlow
    val workLocationLngFlow: Flow<Double?> get() = chargingSyncStore.workLocationLngFlow
    val workLocationRadiusMetersFlow: Flow<Int> get() = chargingSyncStore.workLocationRadiusMetersFlow
    val superchargerSyncEnabledFlow: Flow<Boolean> get() = chargingSyncStore.superchargerSyncEnabledFlow
    val lastSuperchargerSyncTimeFlow: Flow<Long> get() = chargingSyncStore.lastSuperchargerSyncTimeFlow
    val lastSuperchargerSyncStatusFlow: Flow<String?> get() = chargingSyncStore.lastSuperchargerSyncStatusFlow
    val smartcarDcSyncEnabledFlow: Flow<Boolean> get() = chargingSyncStore.smartcarDcSyncEnabledFlow
    val smartcarDcCapabilityFlow: Flow<String> get() = chargingSyncStore.smartcarDcCapabilityFlow
    val lastSmartcarDcSyncTimeFlow: Flow<Long> get() = chargingSyncStore.lastSmartcarDcSyncTimeFlow
    val lastSmartcarDcSyncStatusFlow: Flow<String?> get() = chargingSyncStore.lastSmartcarDcSyncStatusFlow
    suspend fun getWallConnectorCarMapping(): Pair<String?, String?> = chargingSyncStore.getWallConnectorCarMapping()
    suspend fun setWallConnectorCarMapping(gen3CarId: String?, universalCarId: String?) =
        chargingSyncStore.setWallConnectorCarMapping(gen3CarId, universalCarId)
    suspend fun getChargerSyncEnabled(): Boolean = chargingSyncStore.getChargerSyncEnabled()
    suspend fun getChargerSyncHour(): Int = chargingSyncStore.getChargerSyncHour()
    suspend fun getChargerSyncMinute(): Int = chargingSyncStore.getChargerSyncMinute()
    suspend fun setChargerSyncEnabled(enabled: Boolean) = chargingSyncStore.setChargerSyncEnabled(enabled)
    suspend fun setChargerSyncTime(hour: Int, minute: Int) = chargingSyncStore.setChargerSyncTime(hour, minute)
    suspend fun getLastChargerSyncTime(): Long = chargingSyncStore.getLastChargerSyncTime()
    suspend fun recordChargerSyncResult(timestampMs: Long, status: String) = chargingSyncStore.recordChargerSyncResult(timestampMs, status)
    suspend fun getWorkChargingSyncEnabled(): Boolean = chargingSyncStore.getWorkChargingSyncEnabled()
    suspend fun getWorkChargingWindowStartHour(): Int = chargingSyncStore.getWorkChargingWindowStartHour()
    suspend fun getWorkChargingWindowStartMinute(): Int = chargingSyncStore.getWorkChargingWindowStartMinute()
    suspend fun getWorkChargingWindowEndHour(): Int = chargingSyncStore.getWorkChargingWindowEndHour()
    suspend fun getWorkChargingWindowEndMinute(): Int = chargingSyncStore.getWorkChargingWindowEndMinute()
    suspend fun getWorkChargingWeekdaysOnly(): Boolean = chargingSyncStore.getWorkChargingWeekdaysOnly()
    suspend fun setWorkChargingSyncEnabled(enabled: Boolean) = chargingSyncStore.setWorkChargingSyncEnabled(enabled)
    suspend fun setWorkChargingWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) =
        chargingSyncStore.setWorkChargingWindow(startHour, startMinute, endHour, endMinute)
    suspend fun setWorkChargingWeekdaysOnly(weekdaysOnly: Boolean) = chargingSyncStore.setWorkChargingWeekdaysOnly(weekdaysOnly)
    suspend fun recordWorkChargingSyncResult(timestampMs: Long, status: String) =
        chargingSyncStore.recordWorkChargingSyncResult(timestampMs, status)
    suspend fun getWorkLocation(): Triple<Double?, Double?, Int> = chargingSyncStore.getWorkLocation()
    suspend fun setWorkLocation(lat: Double, lng: Double, radiusMeters: Int = DEFAULT_WORK_LOCATION_RADIUS_METERS) =
        chargingSyncStore.setWorkLocation(lat, lng, radiusMeters)
    suspend fun clearWorkLocation() = chargingSyncStore.clearWorkLocation()
    suspend fun getTeslaChargeWatermark(): Triple<Double, String?, String?> = chargingSyncStore.getTeslaChargeWatermark()
    suspend fun setTeslaChargeWatermark(energyAddedKwh: Double, dateIso: String, venue: String) =
        chargingSyncStore.setTeslaChargeWatermark(energyAddedKwh, dateIso, venue)
    suspend fun getSuperchargerSyncEnabled(): Boolean = chargingSyncStore.getSuperchargerSyncEnabled()
    suspend fun setSuperchargerSyncEnabled(enabled: Boolean) = chargingSyncStore.setSuperchargerSyncEnabled(enabled)
    suspend fun getLastSuperchargerSyncTime(): Long = chargingSyncStore.getLastSuperchargerSyncTime()
    suspend fun recordSuperchargerSyncResult(timestampMs: Long, status: String) =
        chargingSyncStore.recordSuperchargerSyncResult(timestampMs, status)
    suspend fun getSmartcarDcSyncEnabled(): Boolean = chargingSyncStore.getSmartcarDcSyncEnabled()
    suspend fun setSmartcarDcSyncEnabled(enabled: Boolean) = chargingSyncStore.setSmartcarDcSyncEnabled(enabled)
    suspend fun getSmartcarDcCapability(): String = chargingSyncStore.getSmartcarDcCapability()
    suspend fun setSmartcarDcCapability(capability: String) = chargingSyncStore.setSmartcarDcCapability(capability)
    suspend fun recordSmartcarDcSyncResult(timestampMs: Long, status: String, capability: String? = null) =
        chargingSyncStore.recordSmartcarDcSyncResult(timestampMs, status, capability)
}
