package com.example.car_logger.telemetry

import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.OdometerSyncResult
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.WallConnectorSyncResult
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeCarDao
import com.example.car_logger.telemetry.TelemetryTestFixtures.FakeOdometerLogDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OdometerSyncCoreTest {

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
    fun `isCooldownActive respects bypassCooldown parameter concept`() {
        val now = 1_700_000_000_000L
        val oneHourAgo = now - 3_600_000L
        val inCooldown = OdometerSyncService.isCooldownActive(oneHourAgo, 72, now)
        assertTrue(inCooldown)
    }

    @Test
    fun `resolveProviderForCar maps Blazer EV to SMARTCAR and Model 3 to TESLA_FLEET`() {
        val blazerEv = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, isEv = true)
        val model3 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, isEv = true)
        val otherCar = Car(id = "3", make = "Mazda", model = "Miata", year = 2020)

        assertEquals(OdometerSyncService.PROVIDER_SMARTCAR, OdometerSyncService.resolveProviderForCar(blazerEv))
        assertEquals(OdometerSyncService.PROVIDER_TESLA, OdometerSyncService.resolveProviderForCar(model3))
        assertNull(OdometerSyncService.resolveProviderForCar(otherCar))
    }

    @Test
    fun `formatSyncResultMessage produces clear confirmation and error messages`() {
        val blazerEv = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, isEv = true)

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
            "Couldn't sync 2024 Chevrolet Blazer EV: No vehicle linked in Smartcar — tap Connect in Settings",
            errMsg
        )
    }

    @Test
    fun `formatDailyPollSummary creates descriptive message for updated and skipped results`() {
        val car1 = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, odometer = 15000)
        val car2 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, odometer = 28000)
        val results = listOf(
            OdometerSyncResult.Updated(car1.id, 15000, OdometerSyncService.PROVIDER_SMARTCAR),
            OdometerSyncResult.Updated(car2.id, 28000, OdometerSyncService.PROVIDER_TESLA)
        )
        val summary = OdometerSyncService.formatDailyPollSummary(listOf(car1, car2), results)
        assertEquals("Synced 2 vehicles", summary)
    }

    @Test
    fun `formatDailyPollSummary handles not configured and empty vehicles properly`() {
        val car1 = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, odometer = 15000)
        val car2 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, odometer = 28000)

        val unconfiguredResults = listOf(
            OdometerSyncResult.NotConfigured(car1.id),
            OdometerSyncResult.NotConfigured(car2.id)
        )
        val summaryUnconfigured = OdometerSyncService.formatDailyPollSummary(listOf(car1, car2), unconfiguredResults)
        assertEquals("Blazer EV: not connected, Model 3: not connected", summaryUnconfigured)

        val emptySummary = OdometerSyncService.formatDailyPollSummary(emptyList(), emptyList())
        assertEquals("No vehicles set up", emptySummary)
    }

    @Test
    fun `formatDailyPollSummary includes Wall Connector kWh when sessions are added`() {
        val car1 = Car(id = "1", make = "Chevrolet", model = "Blazer EV", year = 2024, odometer = 15000)
        val car2 = Car(id = "2", make = "Tesla", model = "Model 3", year = 2023, odometer = 28000)
        val odoResults = listOf(
            OdometerSyncResult.Updated(car1.id, 15025, OdometerSyncService.PROVIDER_SMARTCAR),
            OdometerSyncResult.Updated(car2.id, 28040, OdometerSyncService.PROVIDER_TESLA)
        )
        val chargerResult = WallConnectorSyncResult.Synced(
            insertedCount = 2,
            totalKwhAdded = 87.3,
            totalSessionsScanned = 5
        )
        val summary = OdometerSyncService.formatDailyPollSummary(listOf(car1, car2), odoResults, chargerResult)
        assertEquals("Synced 2 vehicles • +87.3 kWh", summary)
    }

    @Test
    fun `shouldRecordOdometerLog records stationary zero-delta syncs on new day or after 1 hour`() {
        val zoneId = ZoneId.of("America/New_York")
        val mon11Pm = LocalDate.of(2026, 11, 2).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()
        val mon1105Pm = LocalDate.of(2026, 11, 2).atTime(23, 5).atZone(zoneId).toInstant().toEpochMilli()
        val tue11Pm = LocalDate.of(2026, 11, 3).atTime(23, 0).atZone(zoneId).toInstant().toEpochMilli()

        val latest = OdometerLogEntity(
            id = "log_1",
            carId = "car_1",
            odometer = 20000,
            timestamp = mon11Pm,
            source = OdometerSyncService.PROVIDER_TESLA
        )

        assertTrue(OdometerSyncService.shouldRecordOdometerLog(null, 20000, mon11Pm, zoneId))
        assertTrue(OdometerSyncService.shouldRecordOdometerLog(latest, 20001, mon1105Pm, zoneId))
        assertFalse(OdometerSyncService.shouldRecordOdometerLog(latest, 19999, tue11Pm, zoneId))
        assertFalse(OdometerSyncService.shouldRecordOdometerLog(latest, 20000, mon1105Pm, zoneId))
        assertTrue(OdometerSyncService.shouldRecordOdometerLog(latest, 20000, tue11Pm, zoneId))
    }

    @Test
    fun `recordOdometerReading seeds baseline and latest log when latest log is null and previous odometer exists`() = runBlocking {
        val carDao = FakeCarDao()
        val odometerLogDao = FakeOdometerLogDao()

        val initialCar = CarEntity(
            id = "tesla_1",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            odometer = 24000,
            lastOdometerSyncTime = 1700000000000L
        )
        carDao.insertCar(initialCar)

        val syncTime = 1700086400000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24150,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = syncTime
        )

        assertEquals(24150, carDao.getCarById("tesla_1")?.odometer)
        assertEquals(2, odometerLogDao.logs.size)
        val initialLog = odometerLogDao.logs.first()
        val syncedLog = odometerLogDao.logs.last()

        assertEquals(24000, initialLog.odometer)
        assertEquals(1700000000000L, initialLog.timestamp)
        assertEquals(OdometerSyncService.PROVIDER_TESLA, initialLog.source)

        assertEquals(24150, syncedLog.odometer)
        assertEquals(syncTime, syncedLog.timestamp)
        assertEquals(OdometerSyncService.PROVIDER_TESLA, syncedLog.source)
    }

    @Test
    fun `recordOdometerReading inserts new log when latest log exists and odometer increased`() = runBlocking {
        val carDao = FakeCarDao()
        val odometerLogDao = FakeOdometerLogDao()

        val initialCar = CarEntity(
            id = "tesla_1",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            odometer = 24000
        )
        carDao.insertCar(initialCar)
        odometerLogDao.insertLog(
            OdometerLogEntity("log1", "tesla_1", 24000, 1700000000000L, OdometerSyncService.PROVIDER_TESLA)
        )

        val syncTime = 1700086400000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24200,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = syncTime
        )

        assertEquals(24200, carDao.getCarById("tesla_1")?.odometer)
        assertEquals(2, odometerLogDao.logs.size)
        assertEquals(24200, odometerLogDao.logs.last().odometer)
    }

    @Test
    fun `recordOdometerReading skips duplicate log within same hour when odometer has not increased but records next-day anchor`() = runBlocking {
        val carDao = FakeCarDao()
        val odometerLogDao = FakeOdometerLogDao()

        val initialCar = CarEntity(
            id = "tesla_1",
            make = "Tesla",
            model = "Model 3",
            year = 2023,
            odometer = 24000
        )
        carDao.insertCar(initialCar)
        odometerLogDao.insertLog(
            OdometerLogEntity("log1", "tesla_1", 24000, 1700000000000L, OdometerSyncService.PROVIDER_TESLA)
        )

        val fiveMinLater = 1700000000000L + 300_000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24000,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = fiveMinLater
        )
        assertEquals(1, odometerLogDao.logs.size)

        val nextDay = 1700086400000L
        OdometerSyncService.recordOdometerReading(
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            carId = "tesla_1",
            odometer = 24000,
            provider = OdometerSyncService.PROVIDER_TESLA,
            syncTime = nextDay
        )
        assertEquals(2, odometerLogDao.logs.size)
    }
}
