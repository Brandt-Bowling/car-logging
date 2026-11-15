package com.example.car_logger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Query("SELECT * FROM charging_sessions ORDER BY startDateMs DESC")
    fun getAllSessions(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions ORDER BY startDateMs DESC")
    suspend fun getAllSessionsList(): List<ChargingSessionEntity>

    @Query("SELECT fingerprint FROM charging_sessions")
    suspend fun getAllFingerprints(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(sessions: List<ChargingSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity)

    @Query("DELETE FROM charging_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM charging_sessions WHERE id IN (:ids)")
    suspend fun deleteSessionsByIds(ids: List<String>)

    @Query("DELETE FROM charging_sessions WHERE uploadBatchId = :batchId")
    suspend fun deleteSessionsByBatchId(batchId: String)
}
