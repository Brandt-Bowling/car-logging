package com.example.car_logger.insights

import com.example.car_logger.data.insights.InsightsCalculationEngine
import com.example.car_logger.data.insights.InsightsTimeGranularity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityRateMatchingTest {

    @Test
    fun variableBlendedRate_subtractsFixedCharges_andHandlesEdgeCases() {
        val rate = ElectricityImportParser.calculateVariableBlendedRate(
            totalAmount = 150.0,
            fixedCharges = 18.0,
            totalKwh = 880.0
        )
        assertEquals(0.15, rate, 0.0001)

        assertEquals(
            0.0,
            ElectricityImportParser.calculateVariableBlendedRate(100.0, 10.0, 0.0),
            0.0001
        )

        assertEquals(
            0.0,
            ElectricityImportParser.calculateVariableBlendedRate(12.0, 20.0, 100.0),
            0.0001
        )
    }

    @Test
    fun csvParser_andVenueClassification_andFingerprintDeduplication() {
        val csv = """
            Start Date,Energy (kWh),Charger Location,Total Cost
            2026-11-04,32.5,Garage Wall Connector,
            2026-11-05,40.0,Tesla Supercharger - Highway 101,${'$'}16.80
        """.trimIndent()

        val parsed = ElectricityImportParser.parseChargerCsv("charge_export.csv", csv)
        assertEquals(2, parsed.size)

        assertEquals("2026-11-04", parsed[0].startDateIso)
        assertEquals(32.5, parsed[0].kwh, 0.01)
        assertEquals("HOME", parsed[0].venue)

        assertEquals("2026-11-05", parsed[1].startDateIso)
        assertEquals(40.0, parsed[1].kwh, 0.01)
        assertEquals("PUBLIC", parsed[1].venue)
        assertEquals(16.80, parsed[1].directCost ?: 0.0, 0.01)

        assertEquals("PUBLIC", ElectricityImportParser.classifyVenue("Electrify America", 0.0))
        assertEquals("HOME", ElectricityImportParser.classifyVenue("Home Emporia", null))
        assertEquals("PUBLIC", ElectricityImportParser.classifyVenue("Unknown Station", 9.50))

        val fp1 = ElectricityImportParser.buildFingerprint(
            startMs("2026-11-04"),
            endMs("2026-11-04"),
            32.5,
            "HOME",
            "car_1",
            zoneId
        )
        val fp2 = ElectricityImportParser.buildFingerprint(
            tsAtHour("2026-11-04", 19),
            tsAtHour("2026-11-04", 22),
            32.51,
            "home",
            "car_1",
            zoneId
        )
        val fpDifferentCar = ElectricityImportParser.buildFingerprint(
            startMs("2026-11-04"),
            endMs("2026-11-04"),
            32.5,
            "HOME",
            "car_2",
            zoneId
        )
        assertEquals(fp1, fp2)
        assertNotEquals(fp1, fpDifferentCar)
    }

    @Test
    fun billMatching_respectsOverrideRate_overlappingUploadRecency_andFallbackChain() {
        val ev = Car(id = "ev_1", year = 2024, make = "Tesla", model = "Model Y", isEv = true)

        val olderBill = UtilityBillEntity(
            id = "bill_1",
            providerName = "PG&E",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-30"),
            totalKwh = 500.0,
            totalAmount = 115.0,
            fixedCharges = 15.0,
            calculatedRate = 0.20,
            overrideRate = null,
            effectiveRate = 0.20,
            uploadedAtMs = 1_000L
        )
        val newerOverrideBill = UtilityBillEntity(
            id = "bill_2",
            providerName = "PG&E EV-TOU",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-15"),
            totalKwh = 500.0,
            totalAmount = 115.0,
            fixedCharges = 15.0,
            calculatedRate = 0.20,
            overrideRate = 0.12,
            effectiveRate = 0.12,
            uploadedAtMs = 5_000L
        )

        val sessions = listOf(
            ChargingSessionEntity(
                id = "s1",
                carId = "ev_1",
                startDateMs = tsAtHour("2026-11-10", 12),
                endDateMs = tsAtHour("2026-11-10", 14),
                kwh = 50.0,
                venue = "HOME",
                fingerprint = "fp_s1"
            ),
            ChargingSessionEntity(
                id = "s2",
                carId = "ev_1",
                startDateMs = tsAtHour("2026-11-20", 12),
                endDateMs = tsAtHour("2026-11-20", 14),
                kwh = 50.0,
                venue = "HOME",
                fingerprint = "fp_s2"
            )
        )

        val stateMatched = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-11-25", 12),
            utilityBills = listOf(olderBill, newerOverrideBill),
            chargingSessions = sessions,
            defaultElectricityRate = 0.16f
        )

        assertTrue(stateMatched.electricityCost.hasHomeData)
        assertEquals(100.0, stateMatched.electricityCost.homeTotalKwh, 0.01)
        assertEquals(16.0, stateMatched.electricityCost.homeTotalCost, 0.01)
        assertFalse(stateMatched.electricityCost.homeIsEstimatedRate)

        val decSession = listOf(
            ChargingSessionEntity(
                id = "s_dec",
                carId = "ev_1",
                startDateMs = tsAtHour("2026-12-05", 12),
                endDateMs = tsAtHour("2026-12-05", 14),
                kwh = 40.0,
                venue = "HOME",
                fingerprint = "fp_dec"
            )
        )
        val stateFallbackLatestBill = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-12-10", 12),
            utilityBills = listOf(olderBill, newerOverrideBill),
            chargingSessions = decSession,
            defaultElectricityRate = 0.16f
        )
        assertEquals(8.0, stateFallbackLatestBill.electricityCost.homeTotalCost, 0.01)
        assertTrue(stateFallbackLatestBill.electricityCost.homeIsEstimatedRate)
        assertFalse(stateFallbackLatestBill.electricityCost.homeHasMatchedBill)

        val stateDefaultRate = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.MONTHLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            periodOffset = 0,
            nowMs = tsAtHour("2026-12-10", 12),
            utilityBills = emptyList(),
            chargingSessions = decSession,
            defaultElectricityRate = 0.16f
        )
        assertEquals(6.40, stateDefaultRate.electricityCost.homeTotalCost, 0.01)
        assertEquals(0.16, stateDefaultRate.electricityCost.homeEffectiveRate, 0.001)
        assertTrue(stateDefaultRate.electricityCost.homeIsEstimatedRate)
    }

    @Test
    fun touOffPeakRate_combinesSupplyAndDistribution_andHandlesMissingOrZero() {
        assertEquals(0.15, ElectricityImportParser.calculateTouOffPeakRate(0.082, 0.068)!!, 0.0001)
        assertEquals(0.095, ElectricityImportParser.calculateTouOffPeakRate(0.095, null)!!, 0.0001)
        assertEquals(0.095, ElectricityImportParser.calculateTouOffPeakRate(0.095, -0.02)!!, 0.0001)
        assertNull(ElectricityImportParser.calculateTouOffPeakRate(null, 0.068))
        assertNull(ElectricityImportParser.calculateTouOffPeakRate(0.0, 0.068))
    }

    @Test
    fun parseUtilityBillJsonResponse_parsesTouUnbundledAndFlatNullPayloads() {
        val touJson = """
            {
              "providerName": "DTE Energy",
              "billingStartDate": "2026-10-01",
              "billingEndDate": "2026-10-31",
              "totalKwh": 1250.0,
              "totalAmount": 195.40,
              "fixedCharges": 12.50,
              "isTouPlan": true,
              "offPeakSupplyRate": 0.082,
              "onPeakSupplyRate": 0.185,
              "distributionRatePerKwh": 0.068,
              "offPeakKwh": 980.0,
              "onPeakKwh": 270.0,
              "breakdownItems": [
                { "label": "Off-Peak Power Supply", "category": "OFF_PEAK_SUPPLY", "kwh": 980.0, "ratePerKwh": 0.082, "amount": 80.36 },
                { "label": "Distribution Delivery", "category": "delivery", "kwh": 1250.0, "ratePerKwh": 0.068, "amount": 85.00 },
                { "label": "Customer Charge", "category": "FIXED", "kwh": null, "ratePerKwh": null, "amount": 12.50 }
              ]
            }
        """.trimIndent()

        val touDraft = GeminiAiService.parseUtilityBillJsonResponse(touJson, "dte_oct.pdf")
        assertEquals("DTE Energy", touDraft.providerName)
        assertEquals("2026-10-01", touDraft.startDateIso)
        assertEquals("2026-10-31", touDraft.endDateIso)
        assertEquals(1250.0, touDraft.totalKwh, 0.001)
        assertEquals(195.40, touDraft.totalAmount, 0.001)
        assertEquals(12.50, touDraft.fixedCharges, 0.001)
        assertTrue(touDraft.isTouPlan)
        assertEquals(0.082, touDraft.offPeakSupplyRate!!, 0.0001)
        assertEquals(0.185, touDraft.onPeakSupplyRate!!, 0.0001)
        assertEquals(0.068, touDraft.distributionRatePerKwh!!, 0.0001)
        assertEquals(980.0, touDraft.offPeakKwh!!, 0.001)
        assertEquals(270.0, touDraft.onPeakKwh!!, 0.001)
        assertEquals(3, touDraft.breakdownItems.size)
        assertEquals("OFF_PEAK_SUPPLY", touDraft.breakdownItems[0].category)
        assertEquals(80.36, touDraft.breakdownItems[0].amount!!, 0.001)
        assertEquals("DISTRIBUTION", touDraft.breakdownItems[1].category)
        assertEquals("FIXED", touDraft.breakdownItems[2].category)
        assertNull(touDraft.breakdownItems[2].kwh)
        assertEquals(
            0.15,
            ElectricityImportParser.calculateTouOffPeakRate(
                touDraft.offPeakSupplyRate,
                touDraft.distributionRatePerKwh
            )!!,
            0.0001
        )

        val flatJson = """
            {
              "providerName": "Flat Power Co",
              "billingStartDate": "2026-11-01",
              "billingEndDate": "2026-11-30",
              "totalKwh": 800.0,
              "totalAmount": 136.0,
              "fixedCharges": 16.0,
              "isTouPlan": false,
              "offPeakSupplyRate": null,
              "onPeakSupplyRate": null,
              "distributionRatePerKwh": 0.0,
              "offPeakKwh": null,
              "onPeakKwh": null,
              "breakdownItems": null
            }
        """.trimIndent()

        val flatDraft = GeminiAiService.parseUtilityBillJsonResponse(flatJson, "flat_nov.pdf")
        assertEquals("Flat Power Co", flatDraft.providerName)
        assertFalse(flatDraft.isTouPlan)
        assertNull(flatDraft.offPeakSupplyRate)
        assertNull(flatDraft.onPeakSupplyRate)
        assertEquals(0.0, flatDraft.distributionRatePerKwh!!, 0.0001)
        assertNull(flatDraft.offPeakKwh)
        assertNull(flatDraft.onPeakKwh)
        assertTrue(flatDraft.breakdownItems.isEmpty())
        assertEquals(
            0.15,
            ElectricityImportParser.calculateVariableBlendedRate(
                flatDraft.totalAmount,
                flatDraft.fixedCharges,
                flatDraft.totalKwh
            ),
            0.0001
        )
    }

    @Test
    fun useCustomElectricityRate_overridesMatchedBillsAndRestoresWhenDisabled() {
        val ev1 = Car(
            id = "ev1",
            year = 2024,
            make = "Tesla",
            model = "Model Y",
            isEv = true,
            telemetryProvider = "tessie"
        )
        val matchedBill = UtilityBillEntity(
            id = "bill_nov",
            providerName = "DTE Energy",
            startDateMs = startMs("2026-11-01"),
            endDateMs = endMs("2026-11-30"),
            totalKwh = 1000.0,
            totalAmount = 212.0,
            fixedCharges = 12.0,
            calculatedRate = 0.20,
            overrideRate = null,
            effectiveRate = 0.20,
            uploadedAtMs = 1_000L
        )
        val sessions = listOf(
            ChargingSessionEntity(
                id = "h_nov",
                carId = "ev1",
                startDateMs = tsAtHour("2026-11-11", 22),
                endDateMs = tsAtHour("2026-11-12", 4),
                kwh = 50.0,
                venue = "HOME",
                fingerprint = "fp_custom_test"
            )
        )

        val customRateState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(matchedBill),
            chargingSessions = sessions,
            defaultElectricityRate = 0.135f,
            useCustomElectricityRate = true
        )

        assertTrue(customRateState.useCustomElectricityRate)
        assertTrue(customRateState.electricityCost.homeIsCustomRate)
        assertFalse(customRateState.electricityCost.homeIsEstimatedRate)
        assertFalse(customRateState.electricityCost.homeHasMatchedBill)
        assertEquals(0.135, customRateState.electricityCost.homeEffectiveRate, 0.0001)
        assertEquals(6.75, customRateState.electricityCost.homeTotalCost, 0.01)
        assertEquals("Custom rate", customRateState.electricityCost.homeRateSourceLabel)
        assertEquals(0.135, customRateState.electricityCost.homePeriodLogs.first().effectiveRate, 0.0001)

        val billRateState = InsightsCalculationEngine.computeUiState(
            cars = listOf(ev1),
            allLogs = emptyList(),
            granularity = InsightsTimeGranularity.WEEKLY,
            selectedCarId = null,
            gasPrice = 3.65f,
            baselineMpg = 28.0f,
            nowMs = tsAtHour("2026-11-14", 12),
            utilityBills = listOf(matchedBill),
            chargingSessions = sessions,
            defaultElectricityRate = 0.135f,
            useCustomElectricityRate = false
        )

        assertFalse(billRateState.useCustomElectricityRate)
        assertFalse(billRateState.electricityCost.homeIsCustomRate)
        assertFalse(billRateState.electricityCost.homeIsEstimatedRate)
        assertTrue(billRateState.electricityCost.homeHasMatchedBill)
        assertEquals(0.20, billRateState.electricityCost.homeEffectiveRate, 0.0001)
        assertEquals(10.0, billRateState.electricityCost.homeTotalCost, 0.01)
        assertNotEquals("Custom rate", billRateState.electricityCost.homeRateSourceLabel)
    }
}
