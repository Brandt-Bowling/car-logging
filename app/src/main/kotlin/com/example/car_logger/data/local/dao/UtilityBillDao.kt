package com.example.car_logger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.car_logger.data.local.entity.UtilityBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UtilityBillDao {

    @Query("SELECT * FROM utility_bills ORDER BY endDateMs DESC, uploadedAtMs DESC")
    fun getAllBills(): Flow<List<UtilityBillEntity>>

    @Query("SELECT * FROM utility_bills ORDER BY endDateMs DESC, uploadedAtMs DESC")
    suspend fun getAllBillsList(): List<UtilityBillEntity>

    @Query("SELECT * FROM utility_bills WHERE startDateMs = :startDateMs AND endDateMs = :endDateMs LIMIT 1")
    suspend fun findByPeriod(startDateMs: Long, endDateMs: Long): UtilityBillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: UtilityBillEntity)

    @Query("DELETE FROM utility_bills WHERE id = :id")
    suspend fun deleteBillById(id: String)
}
