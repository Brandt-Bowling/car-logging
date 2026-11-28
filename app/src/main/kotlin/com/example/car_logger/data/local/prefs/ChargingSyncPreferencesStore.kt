package com.example.car_logger.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.car_logger.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class ChargingSyncPreferencesStore(private val dataStore: DataStore<Preferences>) {

    val wallConnectorGen3CarIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_WALL_CONNECTOR_GEN3_CAR_ID]
    }

    val wallConnectorUniversalCarIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID]
    }

    val chargerSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_CHARGER_SYNC_ENABLED] ?: true
    }

    val chargerSyncHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[PreferencesManager.KEY_CHARGER_SYNC_HOUR] ?: PreferencesManager.DEFAULT_CHARGER_SYNC_HOUR).coerceIn(0, 23)
    }

    val chargerSyncMinuteFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[PreferencesManager.KEY_CHARGER_SYNC_MINUTE] ?: PreferencesManager.DEFAULT_CHARGER_SYNC_MINUTE).coerceIn(0, 59)
    }

    val lastChargerSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_CHARGER_SYNC_TIME] ?: 0L
    }

    val lastChargerSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_CHARGER_SYNC_STATUS]
    }

    val workChargingSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_WORK_CHARGING_SYNC_ENABLED] ?: true
    }

    val workChargingWindowStartHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_START_HOUR]
            ?: PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_START_HOUR).coerceIn(0, 23)
    }

    val workChargingWindowStartMinuteFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_START_MINUTE]
            ?: PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_START_MINUTE).coerceIn(0, 59)
    }

    val workChargingWindowEndHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_END_HOUR]
            ?: PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_END_HOUR).coerceIn(0, 23)
    }

    val workChargingWindowEndMinuteFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_END_MINUTE]
            ?: PreferencesManager.DEFAULT_WORK_CHARGING_WINDOW_END_MINUTE).coerceIn(0, 59)
    }

    val workChargingWeekdaysOnlyFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_WORK_CHARGING_WEEKDAYS_ONLY] ?: true
    }

    val lastWorkChargingSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_WORK_CHARGING_SYNC_TIME] ?: 0L
    }

    val lastWorkChargingSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_WORK_CHARGING_SYNC_STATUS]
    }

    val workLocationLatFlow: Flow<Double?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_WORK_LOCATION_LAT]
    }

    val workLocationLngFlow: Flow<Double?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_WORK_LOCATION_LNG]
    }

    val workLocationRadiusMetersFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[PreferencesManager.KEY_WORK_LOCATION_RADIUS_METERS]
            ?: PreferencesManager.DEFAULT_WORK_LOCATION_RADIUS_METERS).coerceIn(100, 2000)
    }

    val superchargerSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_SUPERCHARGER_SYNC_ENABLED] ?: true
    }

    val lastSuperchargerSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_SUPERCHARGER_SYNC_TIME] ?: 0L
    }

    val lastSuperchargerSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_SUPERCHARGER_SYNC_STATUS]
    }

    val smartcarDcSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_SMARTCAR_DC_SYNC_ENABLED] ?: true
    }

    val smartcarDcCapabilityFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_SMARTCAR_DC_CAPABILITY] ?: PreferencesManager.SMARTCAR_CAPABILITY_UNKNOWN
    }

    val lastSmartcarDcSyncTimeFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_SMARTCAR_DC_SYNC_TIME] ?: 0L
    }

    val lastSmartcarDcSyncStatusFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_LAST_SMARTCAR_DC_SYNC_STATUS]
    }

    suspend fun getWallConnectorCarMapping(): Pair<String?, String?> = dataStore.data.map {
        Pair(it[PreferencesManager.KEY_WALL_CONNECTOR_GEN3_CAR_ID], it[PreferencesManager.KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID])
    }.first()

    suspend fun setWallConnectorCarMapping(gen3CarId: String?, universalCarId: String?) {
        dataStore.edit { prefs ->
            if (gen3CarId.isNullOrBlank()) {
                prefs.remove(PreferencesManager.KEY_WALL_CONNECTOR_GEN3_CAR_ID)
            } else {
                prefs[PreferencesManager.KEY_WALL_CONNECTOR_GEN3_CAR_ID] = gen3CarId.trim()
            }
            if (universalCarId.isNullOrBlank()) {
                prefs.remove(PreferencesManager.KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID)
            } else {
                prefs[PreferencesManager.KEY_WALL_CONNECTOR_UNIVERSAL_CAR_ID] = universalCarId.trim()
            }
        }
    }

    suspend fun getChargerSyncEnabled(): Boolean = chargerSyncEnabledFlow.first()
    suspend fun getChargerSyncHour(): Int = chargerSyncHourFlow.first()
    suspend fun getChargerSyncMinute(): Int = chargerSyncMinuteFlow.first()

    suspend fun setChargerSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_CHARGER_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setChargerSyncTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_CHARGER_SYNC_HOUR] = hour.coerceIn(0, 23)
            prefs[PreferencesManager.KEY_CHARGER_SYNC_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun getLastChargerSyncTime(): Long = lastChargerSyncTimeFlow.first()

    suspend fun recordChargerSyncResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_LAST_CHARGER_SYNC_TIME] = timestampMs
            prefs[PreferencesManager.KEY_LAST_CHARGER_SYNC_STATUS] = status
        }
    }

    suspend fun getWorkChargingSyncEnabled(): Boolean = workChargingSyncEnabledFlow.first()
    suspend fun getWorkChargingWindowStartHour(): Int = workChargingWindowStartHourFlow.first()
    suspend fun getWorkChargingWindowStartMinute(): Int = workChargingWindowStartMinuteFlow.first()
    suspend fun getWorkChargingWindowEndHour(): Int = workChargingWindowEndHourFlow.first()
    suspend fun getWorkChargingWindowEndMinute(): Int = workChargingWindowEndMinuteFlow.first()
    suspend fun getWorkChargingWeekdaysOnly(): Boolean = workChargingWeekdaysOnlyFlow.first()

    suspend fun setWorkChargingSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_WORK_CHARGING_SYNC_ENABLED] = enabled
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
            prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_START_HOUR] = normStartH
            prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_START_MINUTE] = normStartM
            prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_END_HOUR] = normEndH
            prefs[PreferencesManager.KEY_WORK_CHARGING_WINDOW_END_MINUTE] = normEndM
        }
    }

    suspend fun setWorkChargingWeekdaysOnly(weekdaysOnly: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_WORK_CHARGING_WEEKDAYS_ONLY] = weekdaysOnly
        }
    }

    suspend fun recordWorkChargingSyncResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_LAST_WORK_CHARGING_SYNC_TIME] = timestampMs
            prefs[PreferencesManager.KEY_LAST_WORK_CHARGING_SYNC_STATUS] = status
        }
    }

    suspend fun getWorkLocation(): Triple<Double?, Double?, Int> = dataStore.data.map { prefs ->
        Triple(
            prefs[PreferencesManager.KEY_WORK_LOCATION_LAT],
            prefs[PreferencesManager.KEY_WORK_LOCATION_LNG],
            (prefs[PreferencesManager.KEY_WORK_LOCATION_RADIUS_METERS]
                ?: PreferencesManager.DEFAULT_WORK_LOCATION_RADIUS_METERS).coerceIn(100, 2000)
        )
    }.first()

    suspend fun setWorkLocation(lat: Double, lng: Double, radiusMeters: Int = PreferencesManager.DEFAULT_WORK_LOCATION_RADIUS_METERS) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_WORK_LOCATION_LAT] = lat
            prefs[PreferencesManager.KEY_WORK_LOCATION_LNG] = lng
            prefs[PreferencesManager.KEY_WORK_LOCATION_RADIUS_METERS] = radiusMeters.coerceIn(100, 2000)
        }
    }

    suspend fun clearWorkLocation() {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesManager.KEY_WORK_LOCATION_LAT)
            prefs.remove(PreferencesManager.KEY_WORK_LOCATION_LNG)
        }
    }

    suspend fun getTeslaChargeWatermark(): Triple<Double, String?, String?> = dataStore.data.map { prefs ->
        Triple(
            prefs[PreferencesManager.KEY_TESLA_LAST_CHARGE_ENERGY_ADDED] ?: -1.0,
            prefs[PreferencesManager.KEY_TESLA_LAST_CHARGE_SEEN_DATE],
            prefs[PreferencesManager.KEY_TESLA_LAST_CHARGE_SEEN_VENUE]
        )
    }.first()

    suspend fun setTeslaChargeWatermark(energyAddedKwh: Double, dateIso: String, venue: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_TESLA_LAST_CHARGE_ENERGY_ADDED] = energyAddedKwh
            prefs[PreferencesManager.KEY_TESLA_LAST_CHARGE_SEEN_DATE] = dateIso
            prefs[PreferencesManager.KEY_TESLA_LAST_CHARGE_SEEN_VENUE] = venue
        }
    }

    suspend fun getSuperchargerSyncEnabled(): Boolean = superchargerSyncEnabledFlow.first()

    suspend fun setSuperchargerSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SUPERCHARGER_SYNC_ENABLED] = enabled
        }
    }

    suspend fun getLastSuperchargerSyncTime(): Long = lastSuperchargerSyncTimeFlow.first()

    suspend fun recordSuperchargerSyncResult(timestampMs: Long, status: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_LAST_SUPERCHARGER_SYNC_TIME] = timestampMs
            prefs[PreferencesManager.KEY_LAST_SUPERCHARGER_SYNC_STATUS] = status
        }
    }

    suspend fun getSmartcarDcSyncEnabled(): Boolean = smartcarDcSyncEnabledFlow.first()

    suspend fun setSmartcarDcSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SMARTCAR_DC_SYNC_ENABLED] = enabled
        }
    }

    suspend fun getSmartcarDcCapability(): String = smartcarDcCapabilityFlow.first()

    suspend fun setSmartcarDcCapability(capability: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_SMARTCAR_DC_CAPABILITY] = capability
        }
    }

    suspend fun recordSmartcarDcSyncResult(timestampMs: Long, status: String, capability: String? = null) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_LAST_SMARTCAR_DC_SYNC_TIME] = timestampMs
            prefs[PreferencesManager.KEY_LAST_SMARTCAR_DC_SYNC_STATUS] = status
            if (!capability.isNullOrBlank()) {
                prefs[PreferencesManager.KEY_SMARTCAR_DC_CAPABILITY] = capability
            }
        }
    }
}
