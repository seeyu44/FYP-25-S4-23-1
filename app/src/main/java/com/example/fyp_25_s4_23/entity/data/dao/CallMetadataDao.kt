package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp_25_s4_23.entity.data.entities.CallMetadataEntity

@Dao
interface CallMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: CallMetadataEntity)

    @Query("SELECT * FROM call_metadata WHERE call_id = :callId LIMIT 1")
    suspend fun getByCallId(callId: String): CallMetadataEntity?

    @Query("SELECT * FROM call_metadata WHERE phone_number = :phoneNumber ORDER BY start_time_millis DESC")
    suspend fun getByPhoneNumber(phoneNumber: String): List<CallMetadataEntity>

    @Query("DELETE FROM call_metadata")
    suspend fun clear()
}
