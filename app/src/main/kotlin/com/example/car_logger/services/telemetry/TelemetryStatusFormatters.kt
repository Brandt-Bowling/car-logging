package com.example.car_logger.services.telemetry

import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.SmartcarDcSyncResult
import com.example.car_logger.services.SuperchargerSyncResult
import com.example.car_logger.services.WallConnectorSyncResult
import com.example.car_logger.services.WorkChargingSyncResult
import java.text.NumberFormat
import java.util.Locale

internal object TelemetryStatusFormatters {

    fun formatSyncResultMessage(car: Car, result: OdometerSyncResult): String {
        val name = car.displayName
        return when (result) {
            is OdometerSyncResult.Updated -> {
                val formatted = NumberFormat.getNumberInstance(Locale.US).format(result.odometerMiles)
                "Synced $name: $formatted mi"
            }
            is OdometerSyncResult.SkippedAsleep ->
                "$name is asleep"
            is OdometerSyncResult.SkippedCooldown ->
                "$name synced recently (${result.hoursRemaining}h cooldown)"
            is OdometerSyncResult.SkippedPolicy ->
                "$name sync skipped (${result.policy.label})"
            is OdometerSyncResult.NotConfigured ->
                "$name isn't connected — set up in Settings"
            is OdometerSyncResult.Error ->
                "Couldn't sync $name: ${result.message}"
        }
    }

    fun formatDailyPollSummary(
        cars: List<Car>,
        results: List<OdometerSyncResult>,
        chargerResult: WallConnectorSyncResult? = null
    ): String {
        if (cars.isEmpty() && results.isEmpty()) {
            return "No vehicles set up"
        }
        val updatedCount = results.count { it is OdometerSyncResult.Updated }
        val baseSummary = if (updatedCount == results.size && updatedCount > 0) {
            if (updatedCount == 1) "Synced 1 vehicle" else "Synced $updatedCount vehicles"
        } else {
            val carMap = cars.associateBy { it.id }
            val details = results.mapNotNull { res ->
                when (res) {
                    is OdometerSyncResult.Updated -> carMap[res.carId]?.model?.let { "$it: synced" }
                    is OdometerSyncResult.SkippedAsleep -> carMap[res.carId]?.model?.let { "$it: asleep" }
                    is OdometerSyncResult.NotConfigured -> carMap[res.carId]?.model?.let { "$it: not connected" }
                    is OdometerSyncResult.Error -> carMap[res.carId]?.model?.let { "$it: error" }
                    else -> null
                }
            }
            if (details.isNotEmpty()) details.joinToString(", ") else "Daily sync completed"
        }

        return if (chargerResult is WallConnectorSyncResult.Synced && chargerResult.insertedCount > 0) {
            val kwhStr = String.format(Locale.US, "%.1f", chargerResult.totalKwhAdded)
            "$baseSummary • +$kwhStr kWh"
        } else {
            baseSummary
        }
    }

    fun formatWallConnectorSyncMessage(result: WallConnectorSyncResult): String {
        return when (result) {
            is WallConnectorSyncResult.Synced -> {
                when {
                    result.insertedCount > 0 -> {
                        val kwhStr = String.format(Locale.US, "%.1f", result.totalKwhAdded)
                        val label = if (result.insertedCount == 1) "session" else "sessions"
                        "Synced ${result.insertedCount} Wall Connector $label (+$kwhStr kWh)"
                    }
                    result.totalSessionsScanned > 0 -> {
                        val label = if (result.totalSessionsScanned == 1) "session" else "sessions"
                        "Wall Connectors up to date (${result.totalSessionsScanned} $label checked)"
                    }
                    else -> "Wall Connectors synced (no new sessions)"
                }
            }
            is WallConnectorSyncResult.NotConfigured ->
                "Tesla isn't connected — set up in Settings"
            is WallConnectorSyncResult.NoDevices ->
                result.message
            is WallConnectorSyncResult.MissingScope ->
                result.message
            is WallConnectorSyncResult.Error ->
                "Wall Connector sync failed: ${result.message}"
        }
    }

    fun formatWorkChargingSyncMessage(result: WorkChargingSyncResult): String = when (result) {
        is WorkChargingSyncResult.Captured -> {
            val kwhStr = String.format(Locale.US, "%.1f", result.kwh)
            val suffix = if (result.verifiedByGps) " · GPS verified" else ""
            if (result.updatedExisting) {
                "Updated work charge: $kwhStr kWh (Free$suffix)"
            } else {
                "Logged work charge: $kwhStr kWh (Free$suffix)"
            }
        }
        is WorkChargingSyncResult.UpToDate -> result.message
        is WorkChargingSyncResult.SkippedAsleep -> result.message
        WorkChargingSyncResult.NotConfigured -> "Connect Tesla in Settings to capture work charging"
        is WorkChargingSyncResult.Error -> "Work charge sync failed: ${result.message}"
    }

    fun formatSuperchargerSyncMessage(result: SuperchargerSyncResult): String = when (result) {
        is SuperchargerSyncResult.Synced -> {
            if (result.insertedCount > 0) {
                val label = if (result.insertedCount == 1) "session" else "sessions"
                val costStr = String.format(Locale.US, "$%.2f", result.totalCostAdded)
                val kwhStr = String.format(Locale.US, "%.1f", result.totalKwhAdded)
                "Synced ${result.insertedCount} Supercharger $label ($kwhStr kWh · $costStr)"
            } else {
                "Superchargers up to date"
            }
        }
        SuperchargerSyncResult.NotConfigured -> "Connect Tesla in Settings to sync Superchargers"
        is SuperchargerSyncResult.MissingScope -> result.message
        is SuperchargerSyncResult.Error -> "Supercharger sync failed: ${result.message}"
    }

    fun formatSmartcarDcSyncMessage(result: SmartcarDcSyncResult): String = when (result) {
        is SmartcarDcSyncResult.Synced -> {
            if (result.insertedCount > 0) {
                val label = if (result.insertedCount == 1) "DC session" else "DC sessions"
                val kwhStr = String.format(Locale.US, "%.1f", result.totalKwhAdded)
                val costPart = if (result.totalCostAdded > 0.0) {
                    " · ${String.format(Locale.US, "$%.2f", result.totalCostAdded)}"
                } else {
                    ""
                }
                "Synced ${result.insertedCount} Smartcar $label ($kwhStr kWh$costPart)"
            } else {
                "Smartcar DC charging up to date"
            }
        }
        SmartcarDcSyncResult.NotConfigured -> "Connect Smartcar in Settings to check DC charging"
        is SmartcarDcSyncResult.MissingPermission -> result.message
        is SmartcarDcSyncResult.UnsupportedByVehicle -> result.message
        is SmartcarDcSyncResult.NotCurrentlyFastCharging -> result.message
        is SmartcarDcSyncResult.Error -> "Smartcar DC check failed: ${result.message}"
    }
}
