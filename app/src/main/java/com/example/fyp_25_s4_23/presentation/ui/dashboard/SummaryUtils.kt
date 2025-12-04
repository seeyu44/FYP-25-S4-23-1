package com.example.fyp_25_s4_23.presentation.ui.dashboard

import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.valueobjects.CallStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

data class SummaryMetrics(
    val label: String,
    val totalCalls: Int,
    val answered: Int,
    val missed: Int,
    val suspicious: Int,
    val blocked: Int,
    val warned: Int,
    val avgConfidence: Double // -1 means N/A
)

fun buildDailySummary(callRecords: List<CallRecord>): List<SummaryMetrics> {
    val zone = ZoneId.systemDefault()
    val grouped = callRecords.groupBy { record ->
        Instant.ofEpochMilli(record.metadata.startTimeMillis).atZone(zone).toLocalDate()
    }

    return grouped.entries.map { (date, records) ->
        val metrics = summarizeRecords(records)
        metrics.copy(label = date.toString())
    }.sortedByDescending { it.label }
}

fun buildWeeklySummary(callRecords: List<CallRecord>): List<SummaryMetrics> {
    val zone = ZoneId.systemDefault()
    val wf = WeekFields.of(Locale.getDefault())

    val grouped = callRecords.groupBy { record ->
        val local = Instant.ofEpochMilli(record.metadata.startTimeMillis).atZone(zone).toLocalDate()
        val week = local.get(wf.weekOfWeekBasedYear())
        val year = local.get(wf.weekBasedYear())
        Pair(year, week)
    }

    return grouped.entries.map { (pair, records) ->
        val (year, week) = pair
        val label = "Week $week of $year"
        val metrics = summarizeRecords(records)
        metrics.copy(label = label)
    }.sortedByDescending { it.label }
}

private fun summarizeRecords(records: List<CallRecord>): SummaryMetrics {
    val total = records.size
    val answered = records.count { it.status == CallStatus.COMPLETED }
    val missed = records.count { it.status == CallStatus.DROPPED }
    val suspicious = records.count { it.lastDetection?.isDeepfake == true }
    val blocked = records.count { it.status == CallStatus.BLOCKED }
    val warned = records.count { it.lastDetection?.isDeepfake == true && it.status != CallStatus.BLOCKED }

    val confidences = records.mapNotNull { r -> r.lastDetection?.takeIf { it.isDeepfake }?.probability?.toDouble() }
    val avgConf = if (confidences.isNotEmpty()) confidences.average() else -1.0

    return SummaryMetrics(
        label = "",
        totalCalls = total,
        answered = answered,
        missed = missed,
        suspicious = suspicious,
        blocked = blocked,
        warned = warned,
        avgConfidence = avgConf
    )
}