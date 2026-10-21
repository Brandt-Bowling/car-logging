package com.example.car_logger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.car_logger.data.local.entity.GloveboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GloveboxDao {
    @Query("SELECT * FROM glovebox WHERE carId = :carId LIMIT 1")
    fun getGloveboxForCar(carId: String): Flow<GloveboxEntity?>

    @Query("SELECT * FROM glovebox WHERE carId = :carId LIMIT 1")
    suspend fun getGloveboxDirect(carId: String): GloveboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlovebox(glovebox: GloveboxEntity)
}
