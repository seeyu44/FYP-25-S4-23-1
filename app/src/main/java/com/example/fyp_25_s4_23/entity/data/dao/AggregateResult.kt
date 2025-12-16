package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.ColumnInfo

/**
 * Result class for aggregation queries on the new normalized schema.
 * Used for daily/weekly analytics and summary views.
 */
data class AggregateResult(
    @ColumnInfo(name = "period") val period: String,
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "answered") val answered: Int,
    @ColumnInfo(name = "missed") val missed: Int,
    @ColumnInfo(name = "suspicious") val suspicious: Int,
    @ColumnInfo(name = "blocked") val blocked: Int,
    @ColumnInfo(name = "avg_confidence") val avgConfidence: Double?
)
