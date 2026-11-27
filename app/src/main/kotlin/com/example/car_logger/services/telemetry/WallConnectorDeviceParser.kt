package com.example.car_logger.services.telemetry

import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.TeslaWallConnectorDevice
import com.example.car_logger.services.WallConnectorType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

internal object WallConnectorDeviceParser {

    private fun extractPartNumberFromDin(din: String?): String? {
        if (din.isNullOrBlank()) return null
        val trimmed = din.trim()
        return if (trimmed.contains("--")) {
            trimmed.substringBefore("--").trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

    fun classifyWallConnectorType(
        din: String? = null,
        partNumber: String? = null,
        siteName: String? = null,
        sessionVin: String? = null,
        index: Int = 0
    ): WallConnectorType {
        val effectivePart = (partNumber?.takeIf { it.isNotBlank() } ?: extractPartNumberFromDin(din) ?: din)
            ?.trim()
            ?.uppercase(Locale.US)
            .orEmpty()
        val nameLower = siteName.orEmpty().lowercase(Locale.US)
        val vinUpper = sessionVin.orEmpty().trim().uppercase(Locale.US)

        // Tesla Universal Wall Connector part numbers begin with 1734412-
        if (effectivePart.startsWith("1734412") ||
            effectivePart.contains("UNIVERSAL") ||
            nameLower.contains("universal") ||
            nameLower.contains("j1772") ||
            nameLower.contains("blazer") ||
            nameLower.contains("chevy") ||
            nameLower.contains("chevrolet") ||
            nameLower.contains("gm") ||
            (vinUpper.length == 17 && !vinUpper.startsWith("5YJ") && !vinUpper.startsWith("7SA") && !vinUpper.startsWith("7G2"))
        ) {
            return WallConnectorType.UNIVERSAL
        }

        // Tesla Gen 3 Wall Connector part numbers begin with 1457768-, 1529455-, or 1509549-
        if (effectivePart.startsWith("1457768") ||
            effectivePart.startsWith("1529455") ||
            effectivePart.startsWith("1509549") ||
            effectivePart.contains("GEN3") ||
            effectivePart.contains("GEN 3") ||
            nameLower.contains("gen 3") ||
            nameLower.contains("gen3") ||
            nameLower.contains("model 3") ||
            nameLower.contains("tesla") ||
            nameLower.contains("nacs") ||
            vinUpper.startsWith("5YJ") ||
            vinUpper.startsWith("7SA") ||
            vinUpper.startsWith("7G2")
        ) {
            return WallConnectorType.GEN_3
        }

        return if (index % 2 == 1) WallConnectorType.UNIVERSAL else WallConnectorType.GEN_3
    }

    private fun parseWallConnectorElement(
        wcElem: JsonElement,
        energySiteId: String,
        siteName: String?,
        index: Int
    ): TeslaWallConnectorDevice? = when (wcElem) {
        is JsonObject -> {
            val din = wcElem["din"].asStringOrNull()
                ?: wcElem["id"].asStringOrNull()
                ?: wcElem["device_id"].asStringOrNull()
            val partNumber = wcElem["part_number"].asStringOrNull()
                ?: extractPartNumberFromDin(din)
            val partName = wcElem["part_name"].asStringOrNull()
            val wcName = wcElem["site_name"].asStringOrNull()
                ?: wcElem["name"].asStringOrNull()
                ?: siteName
            val classificationHint = listOfNotNull(wcName, partName)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
            val type = classifyWallConnectorType(
                din = din,
                partNumber = partNumber,
                siteName = classificationHint,
                index = index
            )
            TeslaWallConnectorDevice(
                energySiteId = energySiteId,
                din = din,
                partNumber = partNumber,
                siteName = wcName,
                connectorType = type
            )
        }
        is JsonPrimitive -> {
            val din = wcElem.asStringOrNull() ?: return null
            val partNumber = extractPartNumberFromDin(din)
            val type = classifyWallConnectorType(
                din = din,
                partNumber = partNumber,
                siteName = siteName,
                index = index
            )
            TeslaWallConnectorDevice(
                energySiteId = energySiteId,
                din = din,
                partNumber = partNumber,
                siteName = siteName,
                connectorType = type
            )
        }
        else -> null
    }

    fun parseTeslaEnergySites(productsJson: String): List<TeslaWallConnectorDevice> {
        return try {
            val root = telemetryJson.parseToJsonElement(productsJson) as? JsonObject ?: return emptyList()
            val responseArr = root["response"] as? JsonArray ?: return emptyList()
            val devices = mutableListOf<TeslaWallConnectorDevice>()

            for (element in responseArr) {
                val obj = element as? JsonObject ?: continue
                // Skip pure vehicle objects from /api/1/products
                val hasVin = obj["vin"].asStringOrNull() != null
                val energySiteId = obj["energy_site_id"].asStringOrNull()
                val resourceType = obj["resource_type"].asStringOrNull()?.lowercase(Locale.US).orEmpty()
                val componentsObj = obj["components"] as? JsonObject
                val wallConnectorsArr = (componentsObj?.get("wall_connectors") as? JsonArray)
                    ?: (obj["wall_connectors"] as? JsonArray)

                if (energySiteId == null && wallConnectorsArr == null &&
                    (resourceType == "vehicle" || (hasVin && resourceType.isEmpty()))
                ) {
                    continue
                }

                val siteId = energySiteId
                    ?: obj["id"].asStringOrNull()
                    ?: continue
                val siteName = obj["site_name"].asStringOrNull()

                if (wallConnectorsArr != null && wallConnectorsArr.isNotEmpty()) {
                    for (wcElem in wallConnectorsArr) {
                        parseWallConnectorElement(
                            wcElem = wcElem,
                            energySiteId = siteId,
                            siteName = siteName,
                            index = devices.size
                        )?.let { devices.add(it) }
                    }
                } else {
                    val rawId = obj["id"].asStringOrNull()
                    val din = obj["din"].asStringOrNull()
                        ?: rawId?.takeIf { it != siteId && it.contains("--") }
                    val partNumber = obj["part_number"].asStringOrNull()
                        ?: extractPartNumberFromDin(din)
                    val partName = obj["part_name"].asStringOrNull()
                    val classificationHint = listOfNotNull(siteName, partName)
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }
                    val type = classifyWallConnectorType(
                        din = din,
                        partNumber = partNumber,
                        siteName = classificationHint,
                        index = devices.size
                    )
                    devices.add(
                        TeslaWallConnectorDevice(
                            energySiteId = siteId,
                            din = din,
                            partNumber = partNumber,
                            siteName = siteName,
                            connectorType = type
                        )
                    )
                }
            }
            devices
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseTeslaSiteInfoWallConnectors(
        siteInfoJson: String,
        energySiteId: String,
        fallbackSiteName: String? = null,
        startingIndex: Int = 0
    ): List<TeslaWallConnectorDevice> {
        return try {
            val root = telemetryJson.parseToJsonElement(siteInfoJson) as? JsonObject ?: return emptyList()
            val response = (root["response"] as? JsonObject) ?: root
            val siteName = response["site_name"].asStringOrNull()
                ?: fallbackSiteName
            val components = response["components"] as? JsonObject
            val wallConnectors = (components?.get("wall_connectors") as? JsonArray)
                ?: (response["wall_connectors"] as? JsonArray)

            val devices = mutableListOf<TeslaWallConnectorDevice>()
            if (wallConnectors != null && wallConnectors.isNotEmpty()) {
                for (wcElem in wallConnectors) {
                    parseWallConnectorElement(
                        wcElem = wcElem,
                        energySiteId = energySiteId,
                        siteName = siteName,
                        index = startingIndex + devices.size
                    )?.let { devices.add(it) }
                }
            } else {
                val din = response["din"].asStringOrNull()
                    ?: response["id"].asStringOrNull()?.takeIf { it != energySiteId }
                val partNumber = response["part_number"].asStringOrNull()
                    ?: extractPartNumberFromDin(din)
                if (din != null || partNumber != null || siteName != null) {
                    devices.add(
                        TeslaWallConnectorDevice(
                            energySiteId = energySiteId,
                            din = din,
                            partNumber = partNumber,
                            siteName = siteName,
                            connectorType = classifyWallConnectorType(
                                din = din,
                                partNumber = partNumber,
                                siteName = siteName,
                                index = startingIndex
                            )
                        )
                    )
                }
            }
            devices
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun resolveCarForWallConnector(
        connectorType: WallConnectorType,
        sessionVin: String? = null,
        cars: List<Car>,
        gen3OverrideCarId: String? = null,
        universalOverrideCarId: String? = null
    ): Car? {
        if (cars.isEmpty()) return null
        val activeCars = cars.filter { !it.isArchived }.ifEmpty { cars }

        // 1. Explicit user override in PreferencesManager takes highest priority
        when (connectorType) {
            WallConnectorType.GEN_3 -> {
                if (!gen3OverrideCarId.isNullOrBlank()) {
                    activeCars.firstOrNull { it.id == gen3OverrideCarId }?.let { return it }
                }
            }
            WallConnectorType.UNIVERSAL -> {
                if (!universalOverrideCarId.isNullOrBlank()) {
                    activeCars.firstOrNull { it.id == universalOverrideCarId }?.let { return it }
                }
            }
        }

        // 2. Direct VIN match if the Wall Connector session reported the vehicle VIN
        if (!sessionVin.isNullOrBlank()) {
            val cleanVin = sessionVin.trim().uppercase(Locale.US)
            activeCars.firstOrNull { it.vin?.trim()?.uppercase(Locale.US) == cleanVin }?.let { return it }
        }

        // 3. Auto-assign Gen 3 -> Model 3 (Tesla) and Universal -> Blazer EV (GM / Smartcar)
        return when (connectorType) {
            WallConnectorType.GEN_3 -> {
                activeCars.firstOrNull { it.model.contains("Model 3", ignoreCase = true) }
                    ?: activeCars.firstOrNull {
                        TelemetryJsonUtils.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_TESLA ||
                            it.make.contains("Tesla", ignoreCase = true)
                    }
                    ?: activeCars.firstOrNull { it.isEv }
            }
            WallConnectorType.UNIVERSAL -> {
                activeCars.firstOrNull { it.model.contains("Blazer", ignoreCase = true) }
                    ?: activeCars.firstOrNull {
                        TelemetryJsonUtils.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_SMARTCAR
                    }
                    ?: activeCars.firstOrNull { it.isEv && !it.make.contains("Tesla", ignoreCase = true) }
                    ?: activeCars.lastOrNull { it.isEv }
            }
        }
    }
}
