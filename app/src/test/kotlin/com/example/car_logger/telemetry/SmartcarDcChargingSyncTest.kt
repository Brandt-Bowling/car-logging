package com.example.car_logger.telemetry

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SmartcarDcSyncResult
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeCarDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeChargingSessionDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeOdometerLogDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.createMockClient
import com.example.car_logger.telemetry.TelemetryTestFixtures.createTestPreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class SmartcarDcChargingSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `parseSmartcarChargeRecords keeps DC and public sessions, skips home AC, and sums cost`() {
        val rawJson = """
            {
              "data": {
                "attributes": {
                  "body": {
                    "values": [
                      {
                        "id": "rec_home_ac",
                        "chargingType": "AC",
                        "isPublicCharger": false,
                        "energyAdded": 35.0,
                        "startTime": "2026-11-10T01:00:00Z",
                        "endTime": "2026-11-10T05:00:00Z"
                      },
                      {
                        "id": "rec_ea_dc",
                        "chargingType": "DC",
                        "isPublicCharger": true,
                        "energyAdded": 48.2,
                        "startTime": "2026-11-11T15:00:00Z",
                        "endTime": "2026-11-11T15:35:00Z",
                        "cost": { "energyAmount": 22.10, "otherAmount": 1.50 },
                        "location": { "name": "Electrify America - Meijer" }
                      },
                      {
                        "id": "rec_public_unpriced",
                        "chargingType": "DC",
                        "isPublicCharger": true,
                        "energyAdded": 25.0,
                        "startTime": "2026-11-12T12:00:00Z",
                        "endTime": "2026-11-12T12:20:00Z"
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = OdometerSyncService.parseSmartcarChargeRecords(rawJson)
        assertEquals(2, parsed.size)
        assertEquals("rec_ea_dc", parsed[0].id)
        assertEquals(48.2, parsed[0].energyKwh, 0.01)
        assertEquals(23.60, parsed[0].costTotal!!, 0.01)
        assertEquals("Electrify America - Meijer", parsed[0].locationName)

        assertEquals("rec_public_unpriced", parsed[1].id)
        assertEquals(25.0, parsed[1].energyKwh, 0.01)
        assertNull(parsed[1].costTotal)
    }

    @Test
    fun `syncSmartcarDcCharging handles 200 records, 403 permission, cached UNSUPPORTED zero-call skip, and live DC probe`() = runBlocking {
        val blazer = Car(
            id = "car_blazer", make = "Chevrolet", model = "Blazer EV", year = 2024,
            isEv = true, vin = "3GNKDCRJ8RS123456"
        )

        suspend fun initSmartcarPrefs(name: String): PreferencesManager {
            val prefs = createTestPreferencesManager(tempFolder, name)
            prefs.setSmartcarCredentials("client_test", "secret_test")
            prefs.saveSmartcarTokens("sc_token", "sc_refresh", 3600L, "veh_blazer_1")
            prefs.setSmartcarUserId("user_blazer_1")
            return prefs
        }

        val prefs1 = initSmartcarPrefs("smartcar_200")
        val dao1 = FakeChargingSessionDao()
        val scTime = Instant.parse("2026-11-11T15:00:00Z").toEpochMilli()
        dao1.insertSession(
            ChargingSessionEntity(
                id = "sc_tesla_existing",
                carId = "car_blazer",
                startDateMs = scTime,
                endDateMs = scTime + 30 * 60_000L,
                kwh = 48.0,
                venue = ElectricityImportParser.VENUE_PUBLIC,
                directCost = 20.0,
                fingerprint = "fp_existing_sc"
            )
        )
        val client1 = createMockClient { url, _ ->
            assertTrue(url.contains("/signals/charge-chargerecords"))
            200 to """
                {
                  "data": {
                    "attributes": {
                      "body": {
                        "values": [
                          {
                            "id": "dup_sc",
                            "chargingType": "DC",
                            "isPublicCharger": true,
                            "energyAdded": 48.2,
                            "startTime": "2026-11-11T15:05:00Z",
                            "endTime": "2026-11-11T15:35:00Z",
                            "location": { "name": "Tesla Supercharger" }
                          },
                          {
                            "id": "new_evgo",
                            "chargingType": "DC",
                            "isPublicCharger": true,
                            "energyAdded": 31.4,
                            "startTime": "2026-11-12T18:00:00Z",
                            "endTime": "2026-11-12T18:25:00Z",
                            "cost": { "energyAmount": 16.20 },
                            "location": { "name": "EVgo Fast Charge" }
                          }
                        ]
                      }
                    }
                  }
                }
            """.trimIndent()
        }
        val res1 = OdometerSyncService(prefs1, FakeCarDao(), FakeOdometerLogDao(), dao1, client1)
            .syncSmartcarDcCharging(listOf(blazer), forceCapabilityRecheck = true, allowLiveProbe = false)
        assertTrue(res1 is SmartcarDcSyncResult.Synced)
        assertEquals(1, (res1 as SmartcarDcSyncResult.Synced).insertedCount)
        assertEquals(PreferencesManager.SMARTCAR_CAPABILITY_CHARGE_RECORDS, prefs1.getSmartcarDcCapability())
        assertEquals(2, dao1.sessions.size)
        assertEquals("EVgo Fast Charge", dao1.sessions.last().chargerName)

        val prefs2 = initSmartcarPrefs("smartcar_403")
        val client2 = createMockClient { _, _ ->
            403 to """{"errors":[{"type":"PERMISSION","code":"FORBIDDEN","description":"Missing permission"}]}"""
        }
        val res2 = OdometerSyncService(prefs2, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), client2)
            .syncSmartcarDcCharging(listOf(blazer), forceCapabilityRecheck = true, allowLiveProbe = false)
        assertTrue(res2 is SmartcarDcSyncResult.MissingPermission)
        assertEquals(PreferencesManager.SMARTCAR_CAPABILITY_MISSING_PERMISSION, prefs2.getSmartcarDcCapability())

        val prefs3 = initSmartcarPrefs("smartcar_unsupported")
        var callCount3 = 0
        val client3 = createMockClient { _, _ ->
            callCount3++
            501 to """{"errors":[{"type":"COMPATIBILITY","code":"VEHICLE_NOT_CAPABLE","description":"Not capable"}]}"""
        }
        val service3 = OdometerSyncService(prefs3, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), client3)
        val res3a = service3.syncSmartcarDcCharging(listOf(blazer), forceCapabilityRecheck = false, allowLiveProbe = false)
        assertTrue(res3a is SmartcarDcSyncResult.UnsupportedByVehicle)
        assertEquals(1, callCount3)
        assertEquals(PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED, prefs3.getSmartcarDcCapability())

        val res3b = service3.syncSmartcarDcCharging(listOf(blazer), forceCapabilityRecheck = false, allowLiveProbe = false)
        assertTrue(res3b is SmartcarDcSyncResult.UnsupportedByVehicle)
        assertEquals(1, callCount3)

        val prefs4 = initSmartcarPrefs("smartcar_live_dc")
        val dao4 = FakeChargingSessionDao()
        val client4 = createMockClient { url, _ ->
            when {
                url.contains("charge-chargerecords") -> 501 to """{"errors":[{"type":"COMPATIBILITY","code":"VEHICLE_NOT_CAPABLE"}]}"""
                url.contains("charge-isfastchargerpresent") -> 200 to """{"data":{"attributes":{"body":{"value":true}}}}"""
                url.contains("charge-energyadded") -> 200 to """{"data":{"attributes":{"body":{"value":27.4}}}}"""
                url.contains("charge-wattage") -> 200 to """{"data":{"attributes":{"body":{"value":145000.0}}}}"""
                url.contains("charge-fastchargertype") -> 200 to """{"data":{"attributes":{"body":{"value":"CCS"}}}}"""
                else -> error("Unexpected Smartcar url $url")
            }
        }
        val res4 = OdometerSyncService(prefs4, FakeCarDao(), FakeOdometerLogDao(), dao4, client4)
            .syncSmartcarDcCharging(listOf(blazer), forceCapabilityRecheck = true, allowLiveProbe = true)
        assertTrue(res4 is SmartcarDcSyncResult.Synced)
        assertEquals(1, (res4 as SmartcarDcSyncResult.Synced).insertedCount)
        assertEquals(PreferencesManager.SMARTCAR_CAPABILITY_LIVE_ONLY, prefs4.getSmartcarDcCapability())
        assertEquals(1, dao4.sessions.size)
        assertEquals(ElectricityImportParser.VENUE_PUBLIC, dao4.sessions[0].venue)
        assertEquals(27.4, dao4.sessions[0].kwh, 0.01)
        assertNull(dao4.sessions[0].directCost)
        assertEquals("DC Fast Charger (CCS)", dao4.sessions[0].chargerName)

        val prefs5 = initSmartcarPrefs("smartcar_gm_all_unsupported")
        val client5 = createMockClient { _, _ ->
            501 to """{"errors":[{"type":"COMPATIBILITY","code":"VEHICLE_NOT_CAPABLE"}]}"""
        }
        val res5 = OdometerSyncService(prefs5, FakeCarDao(), FakeOdometerLogDao(), FakeChargingSessionDao(), client5)
            .syncSmartcarDcCharging(listOf(blazer), forceCapabilityRecheck = true, allowLiveProbe = true)
        assertTrue(res5 is SmartcarDcSyncResult.UnsupportedByVehicle)
        assertEquals(PreferencesManager.SMARTCAR_CAPABILITY_UNSUPPORTED, prefs5.getSmartcarDcCapability())
    }
}
