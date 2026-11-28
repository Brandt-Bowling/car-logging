package com.example.car_logger.telemetry

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.TemporaryFolder
import java.util.Base64

internal object TelemetryTestFixtures {

    fun createTestPreferencesManager(tempFolder: TemporaryFolder, name: String): PreferencesManager {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("$name.preferences_pb") }
        )
        return PreferencesManager(dataStore)
    }

    fun createJwt(scp: List<String>? = null, expEpochSec: Long? = null): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claimsParts = mutableListOf<String>()
        if (scp != null) {
            val arr = scp.joinToString(",") { "\"$it\"" }
            claimsParts.add("\"scp\":[$arr]")
        }
        if (expEpochSec != null) {
            claimsParts.add("\"exp\":$expEpochSec")
        }
        val payload = encoder.encodeToString("{${claimsParts.joinToString(",")}}".toByteArray())
        return "$header.$payload.sig"
    }

    fun createMockClient(handler: (url: String, authHeader: String?) -> Pair<Int, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val (code, bodyStr) = handler(req.url.toString(), req.header("Authorization"))
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("HTTP $code")
                    .body(bodyStr.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
    }

    class FakeCarDao : CarDao {
        val cars = mutableMapOf<String, CarEntity>()

        override fun getAllCars(): Flow<List<CarEntity>> = emptyFlow()
        override suspend fun getAllCarsList(): List<CarEntity> = cars.values.toList()
        override suspend fun getCarById(carId: String): CarEntity? = cars[carId]
        override fun observeCarById(carId: String): Flow<CarEntity?> = emptyFlow()
        override suspend fun insertCar(car: CarEntity) { cars[car.id] = car }
        override suspend fun insertCars(cars: List<CarEntity>) { cars.forEach { this.cars[it.id] = it } }
        override suspend fun updateCar(car: CarEntity) { cars[car.id] = car }
        override suspend fun toggleArchive(carId: String) {}
        override suspend fun updateOdometer(carId: String, odometer: Int) {
            cars[carId]?.let { cars[carId] = it.copy(odometer = odometer) }
        }
        override suspend fun updateOdometerFromTelemetry(
            carId: String,
            odometer: Int,
            provider: String,
            status: String,
            syncTime: Long
        ) {
            cars[carId]?.let {
                cars[carId] = it.copy(
                    odometer = odometer,
                    telemetryProvider = provider,
                    telemetryStatus = status,
                    lastOdometerSyncTime = syncTime
                )
            }
        }
        override suspend fun updateTelemetryStatus(carId: String, provider: String, status: String) {}
        override suspend fun updateDriveFolder(carId: String, folderId: String?, folderName: String?) {}
        override suspend fun deleteCar(car: CarEntity) { cars.remove(car.id) }
        override suspend fun deleteCarById(carId: String) { cars.remove(carId) }
        override suspend fun getCarCount(): Int = cars.size
    }

    class FakeOdometerLogDao : OdometerLogDao {
        val logs = mutableListOf<OdometerLogEntity>()

        override fun getAllLogs(): Flow<List<OdometerLogEntity>> = emptyFlow()
        override suspend fun getAllLogsList(): List<OdometerLogEntity> = logs.sortedBy { it.timestamp }
        override fun getLogsForCar(carId: String): Flow<List<OdometerLogEntity>> = emptyFlow()
        override suspend fun getLogsForCarList(carId: String): List<OdometerLogEntity> =
            logs.filter { it.carId == carId }.sortedBy { it.timestamp }
        override suspend fun getLatestLogForCar(carId: String): OdometerLogEntity? =
            logs.filter { it.carId == carId }.maxByOrNull { it.timestamp }
        override suspend fun getLogCount(): Int = logs.size
        override suspend fun insertLog(log: OdometerLogEntity) {
            logs.removeAll { it.id == log.id }
            logs.add(log)
        }
        override suspend fun insertLogs(logs: List<OdometerLogEntity>) {
            logs.forEach { insertLog(it) }
        }
        override suspend fun deleteLogsForCar(carId: String) {
            logs.removeAll { it.carId == carId }
        }
        override suspend fun deleteNonTelemetryLogs() {
            logs.removeAll {
                it.source in listOf("MAINTENANCE", "INITIAL") ||
                    it.id.startsWith("rec_") ||
                    it.id.startsWith("car_odo_")
            }
        }
    }

    class FakeChargingSessionDao : ChargingSessionDao {
        val sessions = mutableListOf<ChargingSessionEntity>()

        override fun getAllSessions(): Flow<List<ChargingSessionEntity>> = emptyFlow()
        override suspend fun getAllSessionsList(): List<ChargingSessionEntity> =
            sessions.sortedByDescending { it.startDateMs }
        override suspend fun getAllFingerprints(): List<String> = sessions.map { it.fingerprint }
        override suspend fun insertSessions(sessions: List<ChargingSessionEntity>) {
            sessions.forEach { session ->
                if (this.sessions.none { it.id == session.id }) {
                    this.sessions.add(session)
                }
            }
        }
        override suspend fun insertSession(session: ChargingSessionEntity) {
            sessions.removeAll { it.id == session.id }
            sessions.add(session)
        }
        override suspend fun deleteSessionById(id: String) { sessions.removeAll { it.id == id } }
        override suspend fun deleteSessionsByIds(ids: List<String>) { sessions.removeAll { it.id in ids } }
        override suspend fun deleteSessionsByBatchId(batchId: String) {
            sessions.removeAll { it.uploadBatchId == batchId }
        }
    }
}
