package com.example.fyp_25_s4_23.entity.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.example.fyp_25_s4_23.entity.data.entities.AlertEventEntity
import com.example.fyp_25_s4_23.entity.data.entities.CallEntity
import com.example.fyp_25_s4_23.entity.data.entities.CallMetadataEntity
import com.example.fyp_25_s4_23.entity.data.entities.DetectionResultEntity

/**
 * Represents a complete call record with all related data joined via Room @Relation.
 */
data class CompleteCallRecord(
    @Embedded val call: CallEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "call_id"
    )
    val metadata: CallMetadataEntity?,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "call_id"
    )
    val detections: List<DetectionResultEntity>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "call_id"
    )
    val alerts: List<AlertEventEntity>
)
