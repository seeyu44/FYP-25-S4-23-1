package com.example.fyp_25_s4_23.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp_25_s4_23.data.entities.AlertEventEntity

@Dao
interface AlertEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alert: AlertEventEntity)

    @Query("SELECT * FROM alerts ORDER BY trigger_seconds DESC")
    suspend fun listRecent(): List<AlertEventEntity>

    @Query("DELETE FROM alerts")
    suspend fun clear()
}

