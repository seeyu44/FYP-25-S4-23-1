package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.fyp_25_s4_23.entity.data.entities.CallRecordEntity

@Dao
interface CallRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CallRecordEntity)

    @Query("SELECT * FROM call_records ORDER BY start_time DESC")
    suspend fun listRecent(): List<CallRecordEntity>

    @Query("DELETE FROM call_records")
    suspend fun clear()

    // Aggregation queries: return dynamic projection via RawQuery if needed
    @RawQuery
    suspend fun rawQuery(query: SupportSQLiteQuery): List<AggregateResult>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', start_time/1000, 'unixepoch', 'localtime') AS period,
               COUNT(*) as total,
               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as answered,
               SUM(CASE WHEN status = 'DROPPED' THEN 1 ELSE 0 END) as missed,
               SUM(CASE WHEN probability >= :threshold THEN 1 ELSE 0 END) as suspicious,
               SUM(CASE WHEN status = 'BLOCKED' THEN 1 ELSE 0 END) as blocked,
               AVG(CASE WHEN probability >= :threshold THEN probability ELSE NULL END) as avg_confidence
        FROM call_records
        WHERE start_time BETWEEN :startMillis AND :endMillis
        GROUP BY period
        ORDER BY period DESC
        """
    )
    suspend fun dailyAggregates(startMillis: Long, endMillis: Long, threshold: Double): List<AggregateResult>

    @Query(
        """
        SELECT strftime('%Y-%W', start_time/1000, 'unixepoch', 'localtime') AS period,
               COUNT(*) as total,
               SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as answered,
               SUM(CASE WHEN status = 'DROPPED' THEN 1 ELSE 0 END) as missed,
               SUM(CASE WHEN probability >= :threshold THEN 1 ELSE 0 END) as suspicious,
               SUM(CASE WHEN status = 'BLOCKED' THEN 1 ELSE 0 END) as blocked,
               AVG(CASE WHEN probability >= :threshold THEN probability ELSE NULL END) as avg_confidence
        FROM call_records
        WHERE start_time BETWEEN :startMillis AND :endMillis
        GROUP BY period
        ORDER BY period DESC
        """
    )
    suspend fun weeklyAggregates(startMillis: Long, endMillis: Long, threshold: Double): List<AggregateResult>
}

// POJO for aggregation results
data class AggregateResult(
    val period: String,
    val total: Int,
    val answered: Int,
    val missed: Int,
    val suspicious: Int,
    val blocked: Int,
    @ColumnInfo(name = "avg_confidence")
    val avgConfidence: Double?
)

