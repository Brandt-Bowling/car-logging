package com.example.car_logger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.car_logger.data.local.entity.OdometerLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OdometerLogDao {

    @Query("SELECT * FROM odometer_logs WHERE source NOT IN ('MAINTENANCE', 'INITIAL') AND id NOT LIKE 'rec_%' AND id NOT LIKE 'car_odo_%' ORDER BY timestamp ASC")
    fun getAllLogs(): Flow<List<OdometerLogEntity>>

    @Query("SELECT * FROM odometer_logs WHERE carId = :carId AND source NOT IN ('MAINTENANCE', 'INITIAL') AND id NOT LIKE 'rec_%' AND id NOT LIKE 'car_odo_%' ORDER BY timestamp ASC")
    fun getLogsForCar(carId: String): Flow<List<OdometerLogEntity>>

    @Query("SELECT * FROM odometer_logs WHERE carId = :carId AND source NOT IN ('MAINTENANCE', 'INITIAL') AND id NOT LIKE 'rec_%' AND id NOT LIKE 'car_odo_%' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLogForCar(carId: String): OdometerLogEntity?

    @Query("DELETE FROM odometer_logs WHERE source IN ('MAINTENANCE', 'INITIAL') OR id LIKE 'rec_%' OR id LIKE 'car_odo_%'")
    suspend fun deleteNonTelemetryLogs()

    @Query("SELECT COUNT(*) FROM odometer_logs")
    suspend fun getLogCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OdometerLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<OdometerLogEntity>)

    @Query("DELETE FROM odometer_logs WHERE carId = :carId")
    suspend fun deleteLogsForCar(carId: String)
}
