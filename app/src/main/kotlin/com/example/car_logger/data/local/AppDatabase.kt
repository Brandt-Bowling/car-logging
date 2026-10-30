package com.example.car_logger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.GloveboxDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.dao.TireDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.GloveboxEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.local.entity.TireEntity

@Database(
    entities = [
        CarEntity::class,
        MaintenanceRecordEntity::class,
        TireEntity::class,
        GloveboxEntity::class,
        OdometerLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun maintenanceRecordDao(): MaintenanceRecordDao
    abstract fun tireDao(): TireDao
    abstract fun gloveboxDao(): GloveboxDao
    abstract fun odometerLogDao(): OdometerLogDao

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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "car_logger_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
