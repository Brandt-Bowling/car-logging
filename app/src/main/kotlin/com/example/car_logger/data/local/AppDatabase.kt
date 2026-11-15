package com.example.car_logger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.GloveboxDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.dao.TireDao
import com.example.car_logger.data.local.dao.UtilityBillDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.GloveboxEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.TireEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity

@Database(
    entities = [
        CarEntity::class,
        MaintenanceRecordEntity::class,
        TireEntity::class,
        GloveboxEntity::class,
        OdometerLogEntity::class,
        UtilityBillEntity::class,
        ChargingSessionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun maintenanceRecordDao(): MaintenanceRecordDao
    abstract fun tireDao(): TireDao
    abstract fun gloveboxDao(): GloveboxDao
    abstract fun odometerLogDao(): OdometerLogDao
    abstract fun utilityBillDao(): UtilityBillDao
    abstract fun chargingSessionDao(): ChargingSessionDao

    companion object {
        private fun ensureAllCarColumns(db: SupportSQLiteDatabase) {
            val statements = listOf(
                "ALTER TABLE cars ADD COLUMN driveFolderId TEXT",
                "ALTER TABLE cars ADD COLUMN driveFolderName TEXT",
                "ALTER TABLE cars ADD COLUMN telemetryProvider TEXT",
                "ALTER TABLE cars ADD COLUMN telemetryStatus TEXT",
                "ALTER TABLE cars ADD COLUMN lastOdometerSyncTime INTEGER"
            )
            for (sql in statements) {
                try {
                    db.execSQL(sql)
                } catch (_: Exception) {
                    // Column already exists
                }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllCarColumns(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAllCarColumns(db)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS odometer_logs (
                        id TEXT PRIMARY KEY NOT NULL,
                        carId TEXT NOT NULL,
                        odometer INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        source TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_odometer_logs_carId_timestamp ON odometer_logs (carId, timestamp)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS utility_bills (
                        id TEXT PRIMARY KEY NOT NULL,
                        providerName TEXT NOT NULL,
                        startDateMs INTEGER NOT NULL,
                        endDateMs INTEGER NOT NULL,
                        totalKwh REAL NOT NULL,
                        totalAmount REAL NOT NULL,
                        fixedCharges REAL NOT NULL,
                        calculatedRate REAL NOT NULL,
                        overrideRate REAL,
                        effectiveRate REAL NOT NULL,
                        uploadedAtMs INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS charging_sessions (
                        id TEXT PRIMARY KEY NOT NULL,
                        carId TEXT,
                        startDateMs INTEGER NOT NULL,
                        endDateMs INTEGER NOT NULL,
                        kwh REAL NOT NULL,
                        venue TEXT NOT NULL,
                        directCost REAL,
                        chargerName TEXT,
                        isSummary INTEGER NOT NULL,
                        fingerprint TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_startDateMs_endDateMs ON charging_sessions (startDateMs, endDateMs)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_charging_sessions_fingerprint ON charging_sessions (fingerprint)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "car_logger_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
