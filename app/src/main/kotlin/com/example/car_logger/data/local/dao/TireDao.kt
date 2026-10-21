package com.example.car_logger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.car_logger.data.local.entity.TireEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TireDao {
    @Query("SELECT * FROM tires WHERE carId = :carId ORDER BY dateInstalled DESC LIMIT 1")
    fun getActiveTireForCar(carId: String): Flow<TireEntity?>

    @Query("SELECT * FROM tires WHERE carId = :carId ORDER BY dateInstalled DESC")
    fun getAllTiresForCar(carId: String): Flow<List<TireEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTire(tire: TireEntity)

    @Update
    suspend fun updateTire(tire: TireEntity)

    @Delete
    suspend fun deleteTire(tire: TireEntity)
}
