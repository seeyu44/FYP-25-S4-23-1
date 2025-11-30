package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp_25_s4_23.entity.data.entities.CallRecordEntity

@Dao
interface CallRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CallRecordEntity)

    @Query("SELECT * FROM call_records ORDER BY start_time DESC")
    suspend fun listRecent(): List<CallRecordEntity>

    @Query("DELETE FROM call_records")
    suspend fun clear()
}

