package com.example.car_logger.telemetry

import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.TeslaWallConnectorDevice
import com.example.car_logger.services.WallConnectorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WallConnectorParserTest {

    @Test
    fun `parseTeslaEnergySites extracts Gen 3 and Universal Wall Connectors while ignoring vehicle entries`() {
        val productsPayload = """
            {
              "response": [
                {
                  "id": 999111,
                  "vehicle_id": 888222,
                  "vin": "5YJ3E1EA1PF000001",
                  "display_name": "Model 3",
                  "state": "asleep"
                },
                {
                  "energy_site_id": "225511001",
                  "resource_type": "wall_connector",
                  "site_name": "Garage Gen 3",
                  "id": "1457768-02-G--TWC11111",
                  "components": {
                    "wall_connectors": [
                      {
                        "din": "1457768-02-G--TWC11111",
                        "part_number": "1457768-02-G"
                      }
                    ]
                  }
                },
                {
                  "energy_site_id": "225511002",
                  "resource_type": "wall_connector",
                  "site_name": "Driveway Universal",
                  "id": "1734412-02-D--TWC22222",
                  "components": {
                    "wall_connectors": [
                      {
                        "din": "1734412-02-D--TWC22222",
                        "part_number": "1734412-02-D"
                      }
                    ]
                  }
                }
              ],
              "count": 3
            }
        """.trimIndent()

        val devices = OdometerSyncService.parseTeslaEnergySites(productsPayload)
        assertEquals(2, devices.size)

        assertEquals("225511001", devices[0].energySiteId)
        assertEquals("1457768-02-G--TWC11111", devices[0].din)
        assertEquals(WallConnectorType.GEN_3, devices[0].connectorType)

        assertEquals("225511002", devices[1].energySiteId)
        assertEquals("1734412-02-D--TWC22222", devices[1].din)
        assertEquals(WallConnectorType.UNIVERSAL, devices[1].connectorType)
    }

    @Test
    fun `parseTeslaEnergySites ignores JsonNull fields on vehicles and parses string-array wall_connectors`() {
        val productsPayload = """
            {
              "response": [
                {
                  "id": 999111,
                  "vin": "5YJ3E1EA1PF000001",
                  "energy_site_id": null,
                  "resource_type": null,
                  "state": "asleep"
                },
                {
                  "id": 999112,
                  "vin": null,
                  "energy_site_id": null,
                  "resource_type": "vehicle"
                },
                {
                  "energy_site_id": "225511099",
                  "resource_type": "energy",
                  "site_name": "Garage Chargers",
                  "id": "site-uuid-99",
                  "components": {
                    "wall_connectors": [
                      "1457768-02-G--GEN3ABC",
                      "1734412-02-D--UNIVXYZ"
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val devices = OdometerSyncService.parseTeslaEnergySites(productsPayload)
        assertEquals(2, devices.size)
        assertEquals("1457768-02-G--GEN3ABC", devices[0].din)
        assertEquals(WallConnectorType.GEN_3, devices[0].connectorType)
        assertEquals("1734412-02-D--UNIVXYZ", devices[1].din)
        assertEquals(WallConnectorType.UNIVERSAL, devices[1].connectorType)
    }

    @Test
    fun `resolveCarForWallConnector maps Gen 3 to Model 3 and Universal to Blazer EV and honors user overrides`() {
        val blazerEv = Car(
            id = "car_blazer",
            make = "Chevrolet",
            model = "Blazer EV",
            year = 2024,
            vin = "3GNKDCRJ5RS100002",
            isEv = true
        )
        val model3 = Car(
            id = "car_model3",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            vin = "5YJ3E1EA1PF000001",
            isEv = true
        )
        val garage = listOf(blazerEv, model3)

        assertEquals(
            "car_model3",
            OdometerSyncService.resolveCarForWallConnector(connectorType = WallConnectorType.GEN_3, cars = garage)?.id
        )
        assertEquals(
            "car_blazer",
            OdometerSyncService.resolveCarForWallConnector(connectorType = WallConnectorType.UNIVERSAL, cars = garage)?.id
        )

        assertEquals(
            "car_blazer",
            OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.GEN_3,
                cars = garage,
                gen3OverrideCarId = "car_blazer",
                universalOverrideCarId = "car_model3"
            )?.id
        )
        assertEquals(
            "car_model3",
            OdometerSyncService.resolveCarForWallConnector(
                connectorType = WallConnectorType.UNIVERSAL,
                cars = garage,
                gen3OverrideCarId = "car_blazer",
                universalOverrideCarId = "car_model3"
            )?.id
        )
    }

    @Test
    fun `parseTeslaWallConnectorChargeHistory and buildWallConnectorSessionEntities convert Wh to kWh and deduplicate`() {
        val blazerEv = Car(id = "car_blazer", make = "Chevrolet", model = "Blazer EV", year = 2024, isEv = true)
        val model3 = Car(id = "car_model3", make = "Tesla", model = "Model 3", year = 2023, vin = "5YJ3E1EA1PF000001", isEv = true)
        val garage = listOf(blazerEv, model3)

        val devices = listOf(
            TeslaWallConnectorDevice("site_home", "1457768-02-G--GEN3", "1457768-02-G", "Left Bay", WallConnectorType.GEN_3),
            TeslaWallConnectorDevice("site_home", "1734412-02-D--UNIV", "1734412-02-D", "Right Bay", WallConnectorType.UNIVERSAL)
        )

        val telemetryHistoryJson = """
            {
              "response": {
                "time_series": [
                  {
                    "charge_start_time": "2026-11-14T21:15:00-08:00",
                    "charge_stop_time": "2026-11-15T01:05:00-08:00",
                    "charge_energy_imported": 34500,
                    "din": "1457768-02-G--GEN3",
                    "vin": "5YJ3E1EA1PF000001"
                  },
                  {
                    "charge_start_time": "2026-11-14T22:00:00-08:00",
                    "charge_stop_time": "2026-11-15T03:30:00-08:00",
                    "charge_energy_imported": 52800,
                    "din": "1734412-02-D--UNIV"
                  },
                  {
                    "charge_start_time": "2026-11-15T04:00:00-08:00",
                    "charge_stop_time": "2026-11-15T04:01:00-08:00",
                    "charge_energy_imported": 10,
                    "din": "1734412-02-D--UNIV"
                  }
                ]
              }
            }
        """.trimIndent()

        val rawSessions = OdometerSyncService.parseTeslaWallConnectorChargeHistory(telemetryHistoryJson, "site_home")
        assertEquals("Standby <50Wh noise should be filtered out", 2, rawSessions.size)

        val zoneId = ZoneId.of("America/Los_Angeles")
        val entities = OdometerSyncService.buildWallConnectorSessionEntities(
            rawSessions = rawSessions,
            devices = devices,
            cars = garage,
            uploadedAtMs = 1_763_200_000_000L,
            zoneId = zoneId
        )
        assertEquals(2, entities.size)

        val model3Session = entities[0]
        assertEquals("car_model3", model3Session.carId)
        assertEquals(34.5, model3Session.kwh, 0.001)
        assertEquals("HOME", model3Session.venue)
        assertEquals("Tesla Wall Connector (Gen 3) (Left Bay)", model3Session.chargerName)
        assertEquals(ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID, model3Session.uploadBatchId)

        val blazerSession = entities[1]
        assertEquals("car_blazer", blazerSession.carId)
        assertEquals(52.8, blazerSession.kwh, 0.001)
        assertEquals("HOME", blazerSession.venue)
        assertEquals("Tesla Universal Wall Connector (Right Bay)", blazerSession.chargerName)
        assertEquals(ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID, blazerSession.uploadBatchId)

        val secondPollEntities = OdometerSyncService.buildWallConnectorSessionEntities(
            rawSessions = rawSessions,
            devices = devices,
            cars = garage,
            existingIds = entities.map { it.id }.toSet(),
            existingFingerprints = entities.map { it.fingerprint }.toSet(),
            uploadedAtMs = 1_763_286_400_000L,
            zoneId = zoneId
        )
        assertTrue(secondPollEntities.isEmpty())
    }

    @Test
    fun `parseTeslaWallConnectorChargeHistory supports time value keys and JsonNull optionals`() {
        val json = """
            {
              "response": {
                "time_series": [
                  {
                    "time": "2026-11-15T10:00:00-08:00",
                    "value": 18500,
                    "charge_duration": 7200,
                    "din": null,
                    "vin": null
                  }
                ]
              }
            }
        """.trimIndent()

        val sessions = OdometerSyncService.parseTeslaWallConnectorChargeHistory(json, "site_1")
        assertEquals(1, sessions.size)
        assertEquals(18500.0, sessions[0].energyWh, 0.01)
        assertNull(sessions[0].din)
        assertNull(sessions[0].vin)
        assertEquals(sessions[0].startDateMs + 7_200_000L, sessions[0].endDateMs)
    }

    @Test
    fun `parseTeslaWallConnectorChargeHistory parses protobuf seconds objects for charge_start_time and charge_duration`() {
        val realTeslaPayload = """
            {
              "response": {
                "charge_history": [
                  {
                    "charge_start_time": { "seconds": 1731643200 },
                    "charge_duration": { "seconds": 12000 },
                    "energy_added_wh": 25400
                  },
                  {
                    "charging_start_time": { "seconds": "1731729600", "nanos": 500000000 },
                    "charge_duration": { "seconds": "7200", "nanos": 0 },
                    "energy_added_wh": 14800,
                    "din": "1734412-02-D--UNIVXYZ"
                  }
                ]
              }
            }
        """.trimIndent()

        val sessions = OdometerSyncService.parseTeslaWallConnectorChargeHistory(realTeslaPayload, "225511099")
        assertEquals(2, sessions.size)

        assertEquals(1731643200_000L, sessions[0].startDateMs)
        assertEquals(1731643200_000L + 12_000_000L, sessions[0].endDateMs)
        assertEquals(25400.0, sessions[0].energyWh, 0.01)

        assertEquals(1731729600_500L, sessions[1].startDateMs)
        assertEquals(1731729600_500L + 7_200_000L, sessions[1].endDateMs)
        assertEquals(14800.0, sessions[1].energyWh, 0.01)
        assertEquals("1734412-02-D--UNIVXYZ", sessions[1].din)
    }

    @Test
    fun `parseTeslaSiteInfoWallConnectors uses part_name for Gen 3 vs Universal classification`() {
        val siteInfoPayload = """
            {
              "response": {
                "site_name": "Home",
                "components": {
                  "wall_connectors": [
                    {
                      "device_id": "abc-1",
                      "din": "din-gen3-no-part-prefix",
                      "part_name": "Gen 3 Wall Connector",
                      "is_active": true
                    },
                    {
                      "device_id": "abc-2",
                      "din": "din-univ-no-part-prefix",
                      "part_name": "Universal Wall Connector",
                      "is_active": true
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val devices = OdometerSyncService.parseTeslaSiteInfoWallConnectors(siteInfoPayload, "site_home")
        assertEquals(2, devices.size)
        assertEquals(WallConnectorType.GEN_3, devices[0].connectorType)
        assertEquals(WallConnectorType.UNIVERSAL, devices[1].connectorType)
        assertEquals("Home", devices[0].siteName)
    }
}
