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
import com.example.car_logger.data.local.dao.TireDao
import com.example.car_logger.data.local.entity.CarEntity
import com.example.car_logger.data.local.entity.GloveboxEntity
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.local.entity.TireEntity

@Database(
    entities = [
        CarEntity::class,
        MaintenanceRecordEntity::class,
        TireEntity::class,
        GloveboxEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun maintenanceRecordDao(): MaintenanceRecordDao
    abstract fun tireDao(): TireDao
    abstract fun gloveboxDao(): GloveboxDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cars ADD COLUMN driveFolderId TEXT")
                db.execSQL("ALTER TABLE cars ADD COLUMN driveFolderName TEXT")
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
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
