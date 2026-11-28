package com.example.car_logger.telemetry

import com.example.car_logger.services.OdometerSyncService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartcarOAuthAndOdometerTest {

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
    fun `parseSmartcarErrorDetail detects v3 signal permission error`() {
        val permErrorPayload = """
            {
              "data": {
                "id": "odometer-traveleddistance",
                "type": "signal",
                "attributes": {
                  "code": "odometer-traveleddistance",
                  "name": "TraveledDistance",
                  "group": "Odometer",
                  "status": {
                    "value": "ERROR",
                    "error": {
                      "type": "PERMISSION",
                      "code": "UNKNOWN",
                      "title": "Unknown",
                      "detail": "Your application has insufficient permissions to access the requested resource. Please prompt the user to re-authenticate using Smartcar Connect.",
                      "resolution": {
                        "type": "REAUTHENTICATE"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        assertEquals(
            "Missing odometer permission — tap Reconnect in Settings",
            OdometerSyncService.parseSmartcarErrorDetail(permErrorPayload)
        )
    }

    @Test
    fun `buildSmartcarAuthUrl constructs clean url without scope parameter`() {
        val testAppId = "c6e28e0b-2199-47e6-b367-51cb96965b30"
        val url = OdometerSyncService.buildSmartcarAuthUrl(testAppId)

        assertTrue(url.startsWith("https://connect.smartcar.com/oauth/authorize?"))
        assertTrue(url.contains("application_id=$testAppId"))
        assertTrue(url.contains("response_type=none"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fcar-logger-track.web.app%2Foauth%2Fsmartcar"))
        assertTrue(url.contains("mode=live"))
        assertFalse("Url should not contain scope parameter because v3 permissions are configured in Smartcar Dashboard", url.contains("scope="))
    }

    @Test
    fun `buildSmartcarAuthUrl falls back from client_id prefix to default application id`() {
        val clientPrefixedId = "client_01M5REPWJCMZEPD8QKP13FJN4H"
        val url = OdometerSyncService.buildSmartcarAuthUrl(clientPrefixedId)

        assertTrue(url.contains("application_id=c6e28e0b-2199-47e6-b367-51cb96965b30"))
        assertFalse(url.contains("client_01M5REPWJCMZEPD8QKP13FJN4H"))
        assertFalse(url.contains("scope="))
    }
}
