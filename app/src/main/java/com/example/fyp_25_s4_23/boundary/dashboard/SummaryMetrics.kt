package com.example.fyp_25_s4_23.boundary.dashboard

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