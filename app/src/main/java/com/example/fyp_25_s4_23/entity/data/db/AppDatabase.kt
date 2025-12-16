package com.example.fyp_25_s4_23.entity.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.fyp_25_s4_23.entity.data.dao.AlertEventDao
import com.example.fyp_25_s4_23.entity.data.dao.CallDao
import com.example.fyp_25_s4_23.entity.data.dao.CallMetadataDao
import com.example.fyp_25_s4_23.entity.data.dao.DetectionResultDao
import com.example.fyp_25_s4_23.entity.data.dao.UserDao
import com.example.fyp_25_s4_23.entity.data.dao.UserSettingsDao
import com.example.fyp_25_s4_23.entity.data.entities.AlertEventEntity
import com.example.fyp_25_s4_23.entity.data.entities.CallEntity
import com.example.fyp_25_s4_23.entity.data.entities.CallMetadataEntity
import com.example.fyp_25_s4_23.entity.data.entities.DetectionResultEntity
import com.example.fyp_25_s4_23.entity.data.entities.UserEntity
import com.example.fyp_25_s4_23.entity.data.entities.UserSettingsEntity

@Database(
    entities = [
        UserEntity::class,
        UserSettingsEntity::class,
        CallEntity::class,
        CallMetadataEntity::class,
        DetectionResultEntity::class,
        AlertEventEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun callDao(): CallDao
    abstract fun callMetadataDao(): CallMetadataDao
    abstract fun detectionResultDao(): DetectionResultDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN detection_threshold REAL NOT NULL DEFAULT 0.7"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "antideepfake.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
