package com.example.car_logger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.car_logger.data.local.entity.CarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY isArchived ASC, make ASC, model ASC")
    fun getAllCars(): Flow<List<CarEntity>>

    @Query("SELECT * FROM cars")
    suspend fun getAllCarsList(): List<CarEntity>

    @Query("SELECT * FROM cars WHERE id = :carId LIMIT 1")
    suspend fun getCarById(carId: String): CarEntity?

    @Query("SELECT * FROM cars WHERE id = :carId LIMIT 1")
    fun observeCarById(carId: String): Flow<CarEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCar(car: CarEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCars(cars: List<CarEntity>)

    @Update
    suspend fun updateCar(car: CarEntity)

    @Query("UPDATE cars SET isArchived = NOT isArchived WHERE id = :carId")
    suspend fun toggleArchive(carId: String)

    @Query("UPDATE cars SET odometer = :odometer WHERE id = :carId")
    suspend fun updateOdometer(carId: String, odometer: Int)

    @Query("UPDATE cars SET driveFolderId = :folderId, driveFolderName = :folderName WHERE id = :carId")
    suspend fun updateDriveFolder(carId: String, folderId: String?, folderName: String?)

    @Delete
    suspend fun deleteCar(car: CarEntity)

    @Query("DELETE FROM cars WHERE id = :carId")
    suspend fun deleteCarById(carId: String)

    @Query("SELECT COUNT(*) FROM cars")
    suspend fun getCarCount(): Int
}
