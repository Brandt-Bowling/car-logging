package com.example.car_logger.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.car_logger.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class PricingPreferencesStore(private val dataStore: DataStore<Preferences>) {

    val gasPriceFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_GAS_PRICE] ?: PreferencesManager.DEFAULT_GAS_PRICE
    }

    val baselineMpgFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_BASELINE_MPG] ?: PreferencesManager.DEFAULT_BASELINE_MPG
    }

    val defaultElectricityRateFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_DEFAULT_ELECTRICITY_RATE] ?: PreferencesManager.DEFAULT_ELECTRICITY_RATE
    }

    val useCustomElectricityRateFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesManager.KEY_USE_CUSTOM_ELECTRICITY_RATE] ?: false
    }

    suspend fun setGasPrice(price: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_GAS_PRICE] = price
        }
    }

    suspend fun setBaselineMpg(mpg: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_BASELINE_MPG] = mpg
        }
    }

    suspend fun setDefaultElectricityRate(rate: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_DEFAULT_ELECTRICITY_RATE] = rate
        }
    }

    suspend fun setCustomElectricityRate(rate: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_DEFAULT_ELECTRICITY_RATE] = rate
            prefs[PreferencesManager.KEY_USE_CUSTOM_ELECTRICITY_RATE] = true
        }
    }

    suspend fun setUseCustomElectricityRate(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesManager.KEY_USE_CUSTOM_ELECTRICITY_RATE] = enabled
        }
    }
}
