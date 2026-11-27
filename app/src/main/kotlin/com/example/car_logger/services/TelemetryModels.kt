package com.example.car_logger.services

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.WakeProtectionPolicy
import com.example.car_logger.data.local.entity.ChargingSessionEntity

enum class WallConnectorType(val label: String, val defaultChargerName: String) {
    GEN_3("Gen 3 Wall Connector", "Tesla Wall Connector (Gen 3)"),
    UNIVERSAL("Universal Wall Connector", "Tesla Universal Wall Connector")
}

data class TeslaWallConnectorDevice(
    val energySiteId: String,
    val din: String? = null,
    val partNumber: String? = null,
    val siteName: String? = null,
    val connectorType: WallConnectorType
)

data class TeslaWallConnectorChargeSession(
    val energySiteId: String,
    val din: String? = null,
    val vin: String? = null,
    val startDateMs: Long,
    val endDateMs: Long,
    val energyWh: Double
)

sealed class WallConnectorSyncResult {
    data class Synced(
        val insertedCount: Int,
        val totalKwhAdded: Double,
        val totalSessionsScanned: Int
    ) : WallConnectorSyncResult()
    data object NotConfigured : WallConnectorSyncResult()
    data class NoDevices(val message: String = "No Wall Connectors found on Tesla account") : WallConnectorSyncResult()
    data class MissingScope(val message: String) : WallConnectorSyncResult()
    data class Error(val message: String) : WallConnectorSyncResult()
}

data class TeslaChargeSnapshot(
    val vin: String,
    val chargeEnergyAddedKwh: Double,
    val chargingState: String? = null,
    val fastChargerPresent: Boolean = false,
    val fastChargerType: String? = null,
    val connChargeCable: String? = null,
    val batteryLevel: Int? = null,
    val timestampMs: Long? = null,
    val odometerMiles: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isCachedResponse: Boolean = false
)

data class WorkLocationConfig(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = PreferencesManager.DEFAULT_WORK_LOCATION_RADIUS_METERS
)

sealed class WorkChargeDecision {
    data class CaptureNew(
        val energyKwh: Double,
        val sessionId: String,
        val verifiedByGps: Boolean
    ) : WorkChargeDecision()

    data class UpdateExisting(
        val existingSession: ChargingSessionEntity,
        val updatedEnergyKwh: Double,
        val verifiedByGps: Boolean
    ) : WorkChargeDecision()

    data class AlreadyLogged(val energyKwh: Double) : WorkChargeDecision()

    data class Skip(
        val reason: String,
        val watermarkVenue: String?
    ) : WorkChargeDecision()
}

sealed class WorkChargingSyncResult {
    data class Captured(
        val kwh: Double,
        val updatedExisting: Boolean,
        val verifiedByGps: Boolean
    ) : WorkChargingSyncResult()

    data class UpToDate(val message: String) : WorkChargingSyncResult()
    data class SkippedAsleep(val message: String = "Tesla asleep (zero-wake check skipped)") : WorkChargingSyncResult()
    data object NotConfigured : WorkChargingSyncResult()
    data class Error(val message: String) : WorkChargingSyncResult()
}

sealed class WorkLocationCaptureResult {
    data class Saved(val latitude: Double, val longitude: Double) : WorkLocationCaptureResult()
    data class MissingScope(val message: String) : WorkLocationCaptureResult()
    data class SkippedAsleep(val message: String = "Tesla is asleep — wake vehicle to save work location") : WorkLocationCaptureResult()
    data object NotConfigured : WorkLocationCaptureResult()
    data class Error(val message: String) : WorkLocationCaptureResult()
}

data class TeslaSuperchargerSession(
    val sessionId: String,
    val vin: String? = null,
    val vehicleMakeType: String? = null,
    val siteLocationName: String? = null,
    val startDateMs: Long,
    val endDateMs: Long,
    val energyKwh: Double,
    val totalPaidCost: Double,
    val currencyCode: String? = null
)

sealed class SuperchargerSyncResult {
    data class Synced(
        val insertedCount: Int,
        val totalKwhAdded: Double,
        val totalCostAdded: Double,
        val totalSessionsScanned: Int
    ) : SuperchargerSyncResult()

    data object NotConfigured : SuperchargerSyncResult()
    data class MissingScope(val message: String) : SuperchargerSyncResult()
    data class Error(val message: String) : SuperchargerSyncResult()
}

data class SmartcarChargeRecord(
    val id: String,
    val startDateMs: Long,
    val endDateMs: Long,
    val energyKwh: Double,
    val chargingType: String? = null,
    val isPublicCharger: Boolean? = null,
    val costTotal: Double? = null,
    val locationName: String? = null
)

data class SmartcarLiveDcSnapshot(
    val isFastChargerPresent: Boolean? = null,
    val fastChargerType: String? = null,
    val wattageWatts: Double? = null,
    val energyAddedKwh: Double? = null,
    val isCharging: Boolean? = null
)

sealed class SmartcarDcSyncResult {
    data class Synced(
        val insertedCount: Int,
        val totalKwhAdded: Double,
        val totalCostAdded: Double,
        val source: String
    ) : SmartcarDcSyncResult()

    data object NotConfigured : SmartcarDcSyncResult()
    data class MissingPermission(val message: String) : SmartcarDcSyncResult()
    data class UnsupportedByVehicle(val message: String) : SmartcarDcSyncResult()
    data class NotCurrentlyFastCharging(val message: String) : SmartcarDcSyncResult()
    data class Error(val message: String) : SmartcarDcSyncResult()
}

sealed class OdometerSyncResult {
    data class Updated(val carId: String, val odometerMiles: Int, val provider: String) : OdometerSyncResult()
    data class SkippedAsleep(val carId: String) : OdometerSyncResult()
    data class SkippedCooldown(val carId: String, val hoursRemaining: Int) : OdometerSyncResult()
    data class SkippedPolicy(val carId: String, val policy: WakeProtectionPolicy) : OdometerSyncResult()
    data class NotConfigured(val carId: String) : OdometerSyncResult()
    data class Error(val carId: String, val message: String) : OdometerSyncResult()
}
