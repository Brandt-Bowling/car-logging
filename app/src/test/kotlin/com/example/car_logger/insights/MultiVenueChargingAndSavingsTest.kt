package com.example.car_logger.insights

import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.insights.InsightsTestFixtures.endMs
import com.example.car_logger.insights.InsightsTestFixtures.startMs
import com.example.car_logger.insights.InsightsTestFixtures.tsAtHour
import com.example.car_logger.insights.InsightsTestFixtures.zoneId
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.GeminiAiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiVenueChargingAndSavingsTest {

    @Test
    fun homeVsPublicSeparation_andSavedVsGasNetSavings() {
        val ev = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val logs = listOf(
            OdometerLogEntity("l1", "ev1", 1000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l2", "ev1", 1560, tsAtHour("2026-11-14", 16), "TELEMETRY")
        )
        val sessions = listOf(
            ChargingSessionEntity(
                id = "h1",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-10", 12),
                endDateMs = tsAtHour("2026-11-10", 14),
                kwh = 100.0,
                venue = "HOME",
                fingerprint = "fp_h1"
            ),
            ChargingSessionEntity(
                id = "p1",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-12", 12),
                endDateMs = tsAtHour("2026-11-12", 13),
                kwh = 50.0,
                venue = "PUBLIC",
                directCost = 20.0,
                fingerprint = "fp_p1"
            )
        )

        val state = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = sessions,
            defaultElectricityRate = 0.15f
        )

        assertTrue(state.electricityCost.hasHomeData)
        assertTrue(state.electricityCost.hasPublicData)
        assertEquals(15.0, state.electricityCost.homeTotalCost, 0.01)
        assertEquals(20.0, state.electricityCost.publicTotalCost, 0.01)
        assertEquals(0.40, state.electricityCost.publicEffectiveRate, 0.001)
        assertEquals(35.0, state.electricityCost.combinedTotalCost, 0.01)

        assertEquals(73.0, state.gasComparison.totalCost, 0.05)
        assertNotNull(state.gasComparison.netFuelSavings)
        assertEquals(38.0, state.gasComparison.netFuelSavings ?: 0.0, 0.1)
    }

    @Test
    fun noOdometerTelemetry_stillComputesElectricityCost_andAllowsPreviousNavigation() {
        val ev = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val pastSession = listOf(
            ChargingSessionEntity(
                id = "old_1",
                carId = "ev1",
                startDateMs = tsAtHour("2026-10-10", 12),
                endDateMs = tsAtHour("2026-10-10", 14),
                kwh = 60.0,
                venue = "HOME",
                fingerprint = "fp_old_1"
            )
        )

        val stateCurrentMonth = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = pastSession,
            defaultElectricityRate = 0.16f
        )
        assertFalse(stateCurrentMonth.hasOdometerData)
        assertTrue(stateCurrentMonth.electricityCost.hasAnyData)
        assertTrue(stateCurrentMonth.canGoPrevious)

        val statePastMonth = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = -1,
            nowMs = tsAtHour("2026-11-14", 12),
            chargingSessions = pastSession,
            defaultElectricityRate = 0.16f
        )
        assertTrue(statePastMonth.canGoNext)
        assertTrue(statePastMonth.electricityCost.hasHomeData)
        assertEquals(9.60, statePastMonth.electricityCost.homeTotalCost, 0.01)
    }

    @Test
    fun undoLastUpload_resolvesExplicitBatch_andLegacyRowidBatch_andConvertsForRetag() {
        val legacyTagged1 = ChargingSessionEntity(
            id = "leg_1",
            carId = "car_1",
            startDateMs = startMs("2026-10-01"),
            endDateMs = endMs("2026-10-01"),
            kwh = 25.0,
            venue = "HOME",
            fingerprint = "fp_leg_1",
            uploadBatchId = null,
            uploadedAtMs = 1L
        )
        val legacyTagged2 = ChargingSessionEntity(
            id = "leg_2",
            carId = "car_1",
            startDateMs = startMs("2026-10-02"),
            endDateMs = endMs("2026-10-02"),
            kwh = 30.0,
            venue = "HOME",
            fingerprint = "fp_leg_2",
            uploadBatchId = null,
            uploadedAtMs = 2L
        )
        val legacyUntagged1 = ChargingSessionEntity(
            id = "leg_3",
            carId = null,
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-01"),
            kwh = 40.0,
            venue = "HOME",
            chargerName = "Wall Connector",
            fingerprint = "fp_leg_3",
            uploadBatchId = null,
            uploadedAtMs = 3L
        )
        val legacyUntagged2 = ChargingSessionEntity(
            id = "leg_4",
            carId = null,
            startDateMs = startMs("2026-11-05"),
            endDateMs = endMs("2026-11-05"),
            kwh = 45.5,
            venue = "PUBLIC",
            directCost = 18.20,
            chargerName = "Supercharger",
            fingerprint = "fp_leg_4",
            uploadBatchId = null,
            uploadedAtMs = 4L
        )

        val legacyAll = listOf(legacyTagged1, legacyTagged2, legacyUntagged1, legacyUntagged2)
        val legacyLastBatch = ElectricityImportParser.resolveLastUploadBatch(legacyAll)
        assertEquals(listOf("leg_4", "leg_3"), legacyLastBatch.map { it.id })

        val retagImport = ElectricityImportParser.sessionsToImportResult(legacyLastBatch, "last upload", zoneId)
        assertEquals("undo", retagImport.source)
        assertEquals(2, retagImport.sessions.size)
        assertEquals("2026-11-05", retagImport.sessions[0].startDateIso)
        assertEquals(45.5, retagImport.sessions[0].kwh, 0.01)
        assertEquals("PUBLIC", retagImport.sessions[0].venue)
        assertEquals(18.20, retagImport.sessions[0].directCost ?: 0.0, 0.01)
        assertEquals("2026-11-01", retagImport.sessions[1].startDateIso)
        assertEquals(40.0, retagImport.sessions[1].kwh, 0.01)
        assertEquals("HOME", retagImport.sessions[1].venue)

        val newBatch1 = ChargingSessionEntity(
            id = "new_1",
            carId = null,
            startDateMs = startMs("2026-11-10"),
            endDateMs = endMs("2026-11-10"),
            kwh = 20.0,
            venue = "HOME",
            fingerprint = "fp_new_1",
            uploadBatchId = "batch_a",
            uploadedAtMs = 1_000_000L
        )
        val newBatch2a = ChargingSessionEntity(
            id = "new_2a",
            carId = null,
            startDateMs = startMs("2026-11-11"),
            endDateMs = endMs("2026-11-11"),
            kwh = 22.0,
            venue = "HOME",
            fingerprint = "fp_new_2a",
            uploadBatchId = "batch_b",
            uploadedAtMs = 2_000_000L
        )
        val newBatch2b = ChargingSessionEntity(
            id = "new_2b",
            carId = null,
            startDateMs = startMs("2026-11-12"),
            endDateMs = endMs("2026-11-12"),
            kwh = 28.0,
            venue = "HOME",
            fingerprint = "fp_new_2b",
            uploadBatchId = "batch_b",
            uploadedAtMs = 2_000_000L
        )

        val withExplicitBatches = legacyAll + listOf(newBatch1, newBatch2a, newBatch2b)
        val resolvedBatchB = ElectricityImportParser.resolveLastUploadBatch(withExplicitBatches)
        assertEquals(listOf("new_2b", "new_2a"), resolvedBatchB.map { it.id })

        val afterUndoingB = withExplicitBatches.filterNot { it.uploadBatchId == "batch_b" }
        val resolvedBatchA = ElectricityImportParser.resolveLastUploadBatch(afterUndoingB)
        assertEquals(listOf("new_1"), resolvedBatchA.map { it.id })

        val autoWallConnectorSession = ChargingSessionEntity(
            id = "twc_1457768_1763200000",
            carId = "car_1",
            startDateMs = startMs("2026-11-15"),
            endDateMs = endMs("2026-11-15"),
            kwh = 38.4,
            venue = "HOME",
            chargerName = "Tesla Wall Connector (Gen 3)",
            fingerprint = "fp_twc_1",
            uploadBatchId = ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID,
            uploadedAtMs = 9_999_999L
        )
        val mixedManualAndAuto = afterUndoingB + autoWallConnectorSession
        val resolvedIgnoringAuto = ElectricityImportParser.resolveLastUploadBatch(mixedManualAndAuto)
        assertEquals(listOf("new_1"), resolvedIgnoringAuto.map { it.id })
    }

    @Test
    fun workCharging_computesZeroCost_avoidedHomeCost_combinedEfficiency_andNetFuelSavings() {
        val ev1 = Car(id = "ev1", year = 2024, make = "Tesla", model = "Model 3", isEv = true)
        val bill = UtilityBillEntity(
            id = "bill_nov",
            providerName = "DTE Energy",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-30"),
            totalKwh = 1000.0,
            totalAmount = 200.0,
            fixedCharges = 0.0,
            calculatedRate = 0.20,
            overrideRate = null,
            effectiveRate = 0.20,
            uploadedAtMs = 1_000L
        )
        val logs = listOf(
            OdometerLogEntity("l1", "ev1", 10000, tsAtHour("2026-11-09", 16), "TELEMETRY"),
            OdometerLogEntity("l2", "ev1", 10280, tsAtHour("2026-11-14", 16), "TELEMETRY")
        )

        val mixedSessions = listOf(
            ChargingSessionEntity(
                id = "h1", carId = "ev1",
                startDateMs = tsAtHour("2026-11-10", 20), endDateMs = tsAtHour("2026-11-10", 22),
                kwh = 20.0, venue = "HOME", fingerprint = "fp_mix_h1"
            ),
            ChargingSessionEntity(
                id = "w1", carId = "ev1",
                startDateMs = tsAtHour("2026-11-11", 9), endDateMs = tsAtHour("2026-11-11", 16),
                kwh = 30.0, venue = "WORK", directCost = 0.0, chargerName = "Work J1772", fingerprint = "fp_mix_w1"
            ),
            ChargingSessionEntity(
                id = "p1", carId = "ev1",
                startDateMs = tsAtHour("2026-11-12", 14), endDateMs = tsAtHour("2026-11-12", 15),
                kwh = 20.0, venue = "PUBLIC", directCost = 8.0, chargerName = "Supercharger", fingerprint = "fp_mix_p1"
            )
        )

        val mixedState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(bill),
            chargingSessions = mixedSessions,
            defaultElectricityRate = 0.16f
        )

        val elec = mixedState.electricityCost
        assertTrue(elec.hasHomeData)
        assertTrue(elec.hasWorkData)
        assertTrue(elec.hasPublicData)
        assertEquals(30.0, elec.workTotalKwh, 0.01)
        assertEquals(6.0, elec.workAvoidedHomeCost, 0.01)
        assertEquals(1, elec.workSessionCount)
        assertEquals(1, elec.workPeriodLogs.size)
        assertEquals(0.0, elec.workPeriodLogs.first().attributedCost, 0.001)
        assertEquals(6.0, elec.workPeriodLogs.first().avoidedHomeCost, 0.01)
        assertEquals(70.0, elec.combinedTotalKwh, 0.01)
        assertEquals(12.0, elec.combinedTotalCost, 0.01)
        assertEquals(4.0, elec.combinedMilesPerKwh!!, 0.01)
        assertEquals(12.0 / 280.0, elec.combinedCostPerMile!!, 0.0005)
        assertEquals(24.50, mixedState.gasComparison.netFuelSavings!!, 0.05)

        val workOnlySessions = listOf(
            ChargingSessionEntity(
                id = "w_only", carId = "ev1",
                startDateMs = startMs("2026-11-01"), endDateMs = endMs("2026-11-30"),
                kwh = 300.0, venue = "WORK", directCost = 0.0, isSummary = true,
                chargerName = "Work (Tesla Charge Stats)", fingerprint = "fp_work_sum"
            )
        )
        val workOnlyState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = logs,
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-11", 12),
            utilityBills = listOf(bill),
            chargingSessions = workOnlySessions,
            defaultElectricityRate = 0.16f
        )
        assertTrue(workOnlyState.electricityCost.hasWorkData)
        assertFalse(workOnlyState.electricityCost.hasHomeData)
        assertEquals(70.0, workOnlyState.electricityCost.workTotalKwh, 0.01)
        assertEquals(14.0, workOnlyState.electricityCost.workAvoidedHomeCost, 0.01)
        assertEquals(0.0, workOnlyState.electricityCost.combinedTotalCost, 0.001)
        assertEquals(4.0, workOnlyState.electricityCost.combinedMilesPerKwh!!, 0.01)
        assertEquals(0.0, workOnlyState.electricityCost.combinedCostPerMile!!, 0.0001)
        assertNotNull(workOnlyState.gasComparison.netFuelSavings)
        assertEquals(36.50, workOnlyState.gasComparison.netFuelSavings!!, 0.05)
    }

    @Test
    fun workVenueClassification_csvZeroCost_andAutomatedBatchExclusion() {
        assertEquals("WORK", ElectricityImportParser.classifyVenue("Work J1772", 0.0))
        assertEquals("WORK", ElectricityImportParser.classifyVenue("Office Charger", null))
        assertEquals("WORK", ElectricityImportParser.classifyVenue("Company Campus Free Charging", 0.0))
        assertEquals("PUBLIC", ElectricityImportParser.classifyVenue("Electrify America", 0.0))
        assertEquals("HOME", ElectricityImportParser.classifyVenue("Garage Wall Connector", null))

        val csv = """
            Date,Energy (kWh),Location,Cost
            2026-11-04,24.0,Work J1772,0.00
            2026-11-05,30.0,Home Wall Connector,
            2026-11-06,42.5,Tesla Supercharger,${'$'}17.50
        """.trimIndent()
        val parsed = ElectricityImportParser.parseChargerCsv("all_venues.csv", csv)
        assertEquals(3, parsed.size)
        assertEquals("WORK", parsed[0].venue)
        assertEquals(0.0, parsed[0].directCost!!, 0.001)
        assertEquals("HOME", parsed[1].venue)
        assertNull(parsed[1].directCost)
        assertEquals("PUBLIC", parsed[2].venue)
        assertEquals(17.50, parsed[2].directCost!!, 0.01)

        val manualSession = ChargingSessionEntity(
            id = "man_1", carId = "ev1",
            startDateMs = startMs("2026-11-01"), endDateMs = endMs("2026-11-01"),
            kwh = 20.0, venue = "HOME", fingerprint = "fp_man_1",
            uploadBatchId = "manual_batch_1", uploadedAtMs = 1_000L
        )
        val autoSessions = listOf(
            ChargingSessionEntity(
                id = "twc_1", carId = "ev1",
                startDateMs = startMs("2026-11-02"), endDateMs = endMs("2026-11-02"),
                kwh = 25.0, venue = "HOME", fingerprint = "fp_auto_1",
                uploadBatchId = ElectricityImportParser.AUTO_WALL_CONNECTOR_BATCH_ID, uploadedAtMs = 9_001L
            ),
            ChargingSessionEntity(
                id = "tesla_work_vin_2026-11-03", carId = "ev1",
                startDateMs = startMs("2026-11-03"), endDateMs = endMs("2026-11-03"),
                kwh = 18.0, venue = "WORK", directCost = 0.0, fingerprint = "fp_auto_2",
                uploadBatchId = ElectricityImportParser.AUTO_WORK_CHARGING_BATCH_ID, uploadedAtMs = 9_002L
            ),
            ChargingSessionEntity(
                id = "sc_tesla_12345", carId = "ev1",
                startDateMs = startMs("2026-11-04"), endDateMs = endMs("2026-11-04"),
                kwh = 40.0, venue = "PUBLIC", directCost = 16.0, fingerprint = "fp_auto_3",
                uploadBatchId = ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID, uploadedAtMs = 9_003L
            ),
            ChargingSessionEntity(
                id = "sc_smartcar_999", carId = "ev2",
                startDateMs = startMs("2026-11-05"), endDateMs = endMs("2026-11-05"),
                kwh = 35.0, venue = "PUBLIC", directCost = 14.0, fingerprint = "fp_auto_4",
                uploadBatchId = ElectricityImportParser.AUTO_SMARTCAR_CHARGING_BATCH_ID, uploadedAtMs = 9_004L
            )
        )
        val resolved = ElectricityImportParser.resolveLastUploadBatch(listOf(manualSession) + autoSessions)
        assertEquals(listOf("man_1"), resolved.map { it.id })
    }

    @Test
    fun parseChargerUsageJsonResponse_parsesTeslaChargeStatsThreeVenues() {
        val rawJson = """
            {
              "sessions": [
                {
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-30",
                  "kwh": 210.0,
                  "venue": "HOME",
                  "directCost": null,
                  "chargerName": "Home (Tesla Charge Stats)",
                  "isSummary": true
                },
                {
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-30",
                  "kwh": 145.5,
                  "venue": "WORK",
                  "directCost": 0.0,
                  "chargerName": "Work (Tesla Charge Stats)",
                  "isSummary": true
                },
                {
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-30",
                  "kwh": 64.0,
                  "venue": "PUBLIC",
                  "directCost": 25.60,
                  "chargerName": "Supercharger (Tesla Charge Stats)",
                  "isSummary": true
                }
              ]
            }
        """.trimIndent()

        val parsed = GeminiAiService.parseChargerUsageJsonResponse(rawJson, "tesla_charge_stats.png")
        assertEquals(3, parsed.size)

        assertEquals("HOME", parsed[0].venue)
        assertEquals(210.0, parsed[0].kwh, 0.01)
        assertNull(parsed[0].directCost)
        assertTrue(parsed[0].isSummary)

        assertEquals("WORK", parsed[1].venue)
        assertEquals(145.5, parsed[1].kwh, 0.01)
        assertEquals(0.0, parsed[1].directCost!!, 0.001)
        assertTrue(parsed[1].isSummary)

        assertEquals("PUBLIC", parsed[2].venue)
        assertEquals(64.0, parsed[2].kwh, 0.01)
        assertEquals(25.60, parsed[2].directCost!!, 0.01)
        assertTrue(parsed[2].isSummary)
    }
}
