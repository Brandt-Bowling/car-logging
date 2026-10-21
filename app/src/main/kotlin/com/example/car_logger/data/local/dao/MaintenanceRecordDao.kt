package com.example.car_logger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceRecordDao {
    @Query("SELECT * FROM maintenance_records ORDER BY date DESC, odometer DESC")
    fun getAllRecords(): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records")
    suspend fun getAllRecordsList(): List<MaintenanceRecordEntity>

    @Query("SELECT * FROM maintenance_records WHERE carId = :carId ORDER BY date DESC, odometer DESC")
    fun getRecordsForCar(carId: String): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records WHERE carId = :carId ORDER BY date DESC, odometer DESC")
    suspend fun getRecordsListForCar(carId: String): List<MaintenanceRecordEntity>

    @Query("SELECT * FROM maintenance_records WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getRecordByDriveFileId(driveFileId: String): MaintenanceRecordEntity?

    @Query("SELECT * FROM maintenance_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): MaintenanceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MaintenanceRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<MaintenanceRecordEntity>)

    @Update
    suspend fun updateRecord(record: MaintenanceRecordEntity)

    @Delete
    suspend fun deleteRecord(record: MaintenanceRecordEntity)

    @Query("DELETE FROM maintenance_records WHERE id = :id")
    suspend fun deleteRecordById(id: String)

    @Query("DELETE FROM maintenance_records WHERE carId = :carId")
    suspend fun deleteRecordsForCar(carId: String)
}
