package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp_25_s4_23.entity.data.entities.DetectionResultEntity

@Dao
interface DetectionResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detection: DetectionResultEntity)
    
    @Query("INSERT OR REPLACE INTO detection_results (id, call_id, probability, is_deepfake, timestamp_seconds, model_version, confidence_level) VALUES (:id, :callId, :probability, :isDeepfake, :timestamp, :modelVersion, :confidenceLevel)")
    fun insertRaw(id: String, callId: String, probability: Float, isDeepfake: Boolean, timestamp: Long, modelVersion: String, confidenceLevel: String)

    @Query("SELECT * FROM detection_results WHERE call_id = :callId ORDER BY timestamp_seconds ASC")
    suspend fun getByCallId(callId: String): List<DetectionResultEntity>

    @Query("SELECT * FROM detection_results WHERE call_id = :callId ORDER BY timestamp_seconds DESC LIMIT 1")
    suspend fun getLatestByCallId(callId: String): DetectionResultEntity?

    @Query("SELECT * FROM detection_results WHERE is_deepfake = 1 ORDER BY timestamp_seconds DESC")
    suspend fun getAllDeepfakes(): List<DetectionResultEntity>

    @Query("DELETE FROM detection_results")
    suspend fun clear()
}
