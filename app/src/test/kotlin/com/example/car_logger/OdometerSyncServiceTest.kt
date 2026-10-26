package com.example.car_logger

import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerSyncServiceTest {

    @Test
    fun `isCooldownActive enforces 72h window accurately`() {
        val now = 1_700_000_000_000L
        val fourHoursAgo = now - (4 * 3_600_000L)
        val eightyHoursAgo = now - (80 * 3_600_000L)

        assertTrue(OdometerSyncService.isCooldownActive(fourHoursAgo, 72, now))
        assertEquals(68, OdometerSyncService.remainingCooldownHours(fourHoursAgo, 72, now))

        assertFalse(OdometerSyncService.isCooldownActive(eightyHoursAgo, 72, now))
        assertEquals(0, OdometerSyncService.remainingCooldownHours(eightyHoursAgo, 72, now))
        assertFalse(OdometerSyncService.isCooldownActive(null, 72, now))
    }

    @Test
    fun `parseSmartcarOdometerMiles parses v2 distance and rounds`() {
        val payload = """{"distance": 14820.42}"""
        assertEquals(14820, OdometerSyncService.parseSmartcarOdometerMiles(payload))

        val payloadRoundUp = """{"distance": 14820.68}"""
        assertEquals(14821, OdometerSyncService.parseSmartcarOdometerMiles(payloadRoundUp))
    }

    @Test
    fun `parseSmartcarOdometerMiles parses v3 signal JSON API and converts km to miles`() {
        val v3KmPayload = """
            {
              "data": {
                "id": "odometer-traveleddistance",
                "type": "signal",
                "attributes": {
                  "body": {
                    "value": 16093.44,
                    "unit": "km"
                  }
                }
              }
            }
        """.trimIndent()
        assertEquals(10000, OdometerSyncService.parseSmartcarOdometerMiles(v3KmPayload))
    }

    @Test
    fun `parseSmartcarV3Connection extracts vehicleId and userId`() {
        val v3Connections = """
            {
              "data": [
                {
                  "id": "conn_1",
                  "attributes": {
                    "vehicleId": "veh_abc123",
                    "userId": "usr_xyz789"
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = OdometerSyncService.parseSmartcarV3Connection(v3Connections)
        assertEquals("veh_abc123", parsed?.first)
        assertEquals("usr_xyz789", parsed?.second)
    }

    @Test
    fun `parseTeslaVehicleListState detects asleep vs online by VIN`() {
        val payload = """
            {
              "response": [
                {
                  "id": 12345,
                  "vin": "5YJ3E1EA1PF000001",
                  "state": "asleep"
                }
              ],
              "count": 1
            }
        """.trimIndent()

        val result = OdometerSyncService.parseTeslaVehicleListState(payload, "5YJ3E1EA1PF000001")
        assertEquals("5YJ3E1EA1PF000001", result?.first)
        assertEquals("asleep", result?.second)
    }

    @Test
    fun `parseTeslaOdometerMiles extracts nested vehicle_state odometer`() {
        val payload = """
            {
              "response": {
                "vehicle_state": {
                  "odometer": 28410.35,
                  "car_version": "2026.12.1"
                }
              }
            }
        """.trimIndent()

        assertEquals(28410, OdometerSyncService.parseTeslaOdometerMiles(payload))
    }

    @Test
    fun `resolveProviderForCar maps Blazer EV to SMARTCAR and Model 3 to TESLA_FLEET`() {
        val blazerEv = Car(
            id = "1",
            make = "Chevrolet",
            model = "Blazer EV",
            year = 2024,
            isEv = true
        )
        val model3 = Car(
            id = "2",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            isEv = true
        )
        val otherCar = Car(
            id = "3",
            make = "Mazda",
            model = "Miata",
            year = 2020
        )

        assertEquals(OdometerSyncService.PROVIDER_SMARTCAR, OdometerSyncService.resolveProviderForCar(blazerEv))
        assertEquals(OdometerSyncService.PROVIDER_TESLA, OdometerSyncService.resolveProviderForCar(model3))
        assertNull(OdometerSyncService.resolveProviderForCar(otherCar))
    }

    @Test
    fun `parseSmartcarV3Connection supports JSON API relationships and empty list`() {
        val emptyPayload = """{"data":[],"meta":{"totalCount":0}}"""
        assertNull(OdometerSyncService.parseSmartcarV3Connection(emptyPayload))

        val relsPayload = """
            {
              "data": [
                {
                  "id": "conn_1",
                  "type": "connection",
                  "relationships": {
                    "user": {
                      "data": {
                        "id": "usr_rel_111",
                        "type": "user"
                      }
                    },
                    "vehicle": {
                      "data": {
                        "id": "veh_rel_222",
                        "type": "vehicle"
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = OdometerSyncService.parseSmartcarV3Connection(relsPayload)
        assertEquals("veh_rel_222", parsed?.first)
        assertEquals("usr_rel_111", parsed?.second)
    }

    @Test
    fun `parseSmartcarV3ApplicationId extracts UUID from applications payload`() {
        val payload = """
            {
              "data": [
                {
                  "id": "c6e28e0b-2199-47e6-b367-51cb96965b30",
                  "type": "application",
                  "attributes": {
                    "name": "Garage Journal",
                    "clientId": "client_01M5REPWJCMZEPD8QKP13FJN4H"
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals(
            "c6e28e0b-2199-47e6-b367-51cb96965b30",
            OdometerSyncService.parseSmartcarV3ApplicationId(payload)
        )
    }

    @Test
    fun `formatSyncResultMessage produces clear confirmation and error messages`() {
        val blazerEv = Car(
            id = "1",
            make = "Chevrolet",
            model = "Blazer EV",
            year = 2024,
            isEv = true
        )

        val updatedMsg = OdometerSyncService.formatSyncResultMessage(
            blazerEv,
            OdometerSyncResult.Updated("1", 14820, OdometerSyncService.PROVIDER_SMARTCAR)
        )
        assertEquals("Synced 2024 Chevrolet Blazer EV: 14,820 mi", updatedMsg)

        val errMsg = OdometerSyncService.formatSyncResultMessage(
            blazerEv,
            OdometerSyncResult.Error("1", "No vehicle linked in Smartcar — tap Connect in Settings")
        )
        assertEquals(
            "Sync failed for 2024 Chevrolet Blazer EV: No vehicle linked in Smartcar — tap Connect in Settings",
            errMsg
        )
    }
}
