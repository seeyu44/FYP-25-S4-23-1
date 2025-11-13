package com.example.fyp_25_s4_23.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.fyp_25_s4_23.data.dao.AlertEventDao
import com.example.fyp_25_s4_23.data.dao.CallRecordDao
import com.example.fyp_25_s4_23.data.dao.UserDao
import com.example.fyp_25_s4_23.data.entities.AlertEventEntity
import com.example.fyp_25_s4_23.data.entities.CallRecordEntity
import com.example.fyp_25_s4_23.data.entities.UserEntity

@Database(
    entities = [UserEntity::class, CallRecordEntity::class, AlertEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "antideepfake.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

