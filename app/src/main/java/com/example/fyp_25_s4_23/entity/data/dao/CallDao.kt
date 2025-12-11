package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.fyp_25_s4_23.entity.data.entities.CallEntity

@Dao
interface CallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: CallEntity)

    @Query("SELECT * FROM calls WHERE id = :callId LIMIT 1")
    suspend fun getById(callId: String): CallEntity?

    @Query("SELECT * FROM calls WHERE user_id = :userId ORDER BY created_seconds DESC")
    suspend fun getByUserId(userId: Long): List<CallEntity>

    @Query("SELECT * FROM calls ORDER BY created_seconds DESC")
    suspend fun getAll(): List<CallEntity>

    @Query("DELETE FROM calls")
    suspend fun clear()

    @Transaction
    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCompleteCallRecord(callId: String): CompleteCallRecord?

    @Transaction
    @Query("SELECT * FROM calls ORDER BY created_seconds DESC")
    suspend fun getAllCompleteCallRecords(): List<CompleteCallRecord>

    // Aggregation queries using the new normalized schema
    @Query("""
        SELECT 
            strftime('%Y-%m-%d', cm.start_time_seconds, 'unixepoch', 'localtime') AS period,
            COUNT(DISTINCT c.id) as total,
            SUM(CASE WHEN c.status = 'COMPLETED' THEN 1 ELSE 0 END) as answered,
            SUM(CASE WHEN c.status = 'DROPPED' THEN 1 ELSE 0 END) as missed,
            SUM(CASE WHEN dr.probability >= :threshold THEN 1 ELSE 0 END) as suspicious,
            SUM(CASE WHEN c.status = 'BLOCKED' THEN 1 ELSE 0 END) as blocked,
            AVG(CASE WHEN dr.probability >= :threshold THEN dr.probability ELSE NULL END) as avg_confidence
        FROM calls c
        LEFT JOIN call_metadata cm ON c.id = cm.call_id
        LEFT JOIN (
            SELECT call_id, MAX(probability) as probability 
            FROM detection_results 
            GROUP BY call_id
        ) dr ON c.id = dr.call_id
        WHERE cm.start_time_seconds BETWEEN :startSeconds AND :endSeconds
        GROUP BY period
        ORDER BY period DESC
    """)
    suspend fun dailyAggregates(startSeconds: Long, endSeconds: Long, threshold: Double): List<AggregateResult>

    @Query("""
        SELECT 
            strftime('%Y-%W', cm.start_time_seconds, 'unixepoch', 'localtime') AS period,
            COUNT(DISTINCT c.id) as total,
            SUM(CASE WHEN c.status = 'COMPLETED' THEN 1 ELSE 0 END) as answered,
            SUM(CASE WHEN c.status = 'DROPPED' THEN 1 ELSE 0 END) as missed,
            SUM(CASE WHEN dr.probability >= :threshold THEN 1 ELSE 0 END) as suspicious,
            SUM(CASE WHEN c.status = 'BLOCKED' THEN 1 ELSE 0 END) as blocked,
            AVG(CASE WHEN dr.probability >= :threshold THEN dr.probability ELSE NULL END) as avg_confidence
        FROM calls c
        LEFT JOIN call_metadata cm ON c.id = cm.call_id
        LEFT JOIN (
            SELECT call_id, MAX(probability) as probability 
            FROM detection_results 
            GROUP BY call_id
        ) dr ON c.id = dr.call_id
        WHERE cm.start_time_seconds BETWEEN :startSeconds AND :endSeconds
        GROUP BY period
        ORDER BY period DESC
    """)
    suspend fun weeklyAggregates(startSeconds: Long, endSeconds: Long, threshold: Double): List<AggregateResult>
}
