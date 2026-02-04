package com.example.fyp_25_s4_23.boundary.dashboard

import android.app.DatePickerDialog
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/* =========================
   SUMMARY SCREEN
   ========================= */

@Composable
fun SummaryScreen(
    user: UserAccount,
    firebaseCalls: List<FirebaseCallRecord>,
    isLoading: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    var rangeLabel by remember { mutableStateOf("Last 7 days") }
    var startMillis by remember { mutableStateOf(0L) }
    var endMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var startDateDisplay by remember { mutableStateOf("") }
    var endDateDisplay by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Filter for incoming calls only (where user is callee, not caller)
    val incomingCalls = firebaseCalls.filter { !it.isCaller }

    // Helper function to generate summary metrics from calls in a date range
    fun generateMetrics(callsInRange: List<FirebaseCallRecord>): List<SummaryMetrics> {
        if (callsInRange.isEmpty()) return emptyList()

        // Group calls by day
        val callsByDay = callsInRange.groupBy { call ->
            val date = call.createdAt?.toDate() ?: java.util.Date()
            val cal = java.util.Calendar.getInstance()
            cal.time = date
            LocalDate.of(cal.get(java.util.Calendar.YEAR), 
                        cal.get(java.util.Calendar.MONTH) + 1,
                        cal.get(java.util.Calendar.DAY_OF_MONTH))
                .toString()
        }

        return callsByDay.map { (date, callsForDay) ->
            // Detection logic:
            // Count all incoming calls in the date range
            // Answered calls: have detectionScore (deepfake analysis was performed)
            // Missed calls: no detectionScore
            // Suspicious calls: isDeepfake == true
            val allCalls = callsForDay
            val answeredCalls = callsForDay.filter { it.detectionScore != null }
            val missedCalls = callsForDay.filter { it.detectionScore == null }
            val suspiciousCalls = answeredCalls.filter { it.isDeepfake == true }
            
            // Calculate average confidence from detection scores (only for answered calls)
            val detectionScores = answeredCalls.mapNotNull { it.detectionScore }
            val avgConfidence = if (detectionScores.isNotEmpty()) {
                detectionScores.average()
            } else {
                -1.0 // N/A
            }
            
            // Calculate average call duration in seconds (only for answered calls with detection score)
            val avgDuration = if (answeredCalls.isNotEmpty()) {
                answeredCalls.sumOf { it.duration } / answeredCalls.size.toDouble()
            } else {
                0.0
            }
            
            Log.i("SummaryScreen", "Date: $date - Total: ${allCalls.size}, Answered: ${answeredCalls.size}, Missed: ${missedCalls.size}, Suspicious: ${suspiciousCalls.size}, AvgConfidence: $avgConfidence, AvgDuration: $avgDuration")
            answeredCalls.forEach { call ->
                Log.d("SummaryScreen", "  Call ${call.id}: detectionScore=${call.detectionScore}, isDeepfake=${call.isDeepfake}, duration=${call.duration}")
            }

            SummaryMetrics(
                label = date,
                totalCalls = answeredCalls.size,
                answered = answeredCalls.size,
                missed = missedCalls.size,
                suspicious = suspiciousCalls.size,
                blocked = 0,
                warned = 0,
                avgConfidence = avgConfidence,
                avgDuration = avgDuration
            )
        }.sortedByDescending { it.label }
    }

    /* =========================
       INITIAL DATE RANGE AND LOAD
       ========================= */
    LaunchedEffect(Unit) {
        val today = LocalDate.now(zone)

        val newEndMillis = today
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() - 1

        val newStartMillis = today
            .minusDays(7)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        endMillis = newEndMillis
        startMillis = newStartMillis
        
        // Format dates for display
        val startDate = Instant.ofEpochMilli(newStartMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(newEndMillis).atZone(zone).toLocalDate()
        startDateDisplay = startDate.toString()
        endDateDisplay = endDate.toString()
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "summary",
                onNavigate = { route ->
                    when (route) {
                        "home" -> onNavigate("home")
                        "summary" -> { /* Already on summary */ }
                        "call_history" -> onNavigate("call_history")
                        "dialer" -> onNavigate("dialer")
                        "contacts" -> onNavigate("contacts")
                        "logout" -> onNavigate("logout")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

        /* =========================
           HEADER
           ========================= */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Summary for ${user.displayName}",
                style = MaterialTheme.typography.titleLarge
            )
        }

        /* =========================
           DATE RANGE BUTTONS
           ========================= */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    Log.i("SummaryScreen", "Last 7 days button clicked")
                    val today = LocalDate.now(zone)
                    endMillis = today.plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli() - 1
                    startMillis = today.minusDays(7)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                    
                    // Update display dates
                    val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
                    val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
                    startDateDisplay = startDate.toString()
                    endDateDisplay = endDate.toString()
                    
                    Log.i("SummaryScreen", "Updated: startMillis=$startMillis, endMillis=$endMillis")
                    rangeLabel = "Last 7 days"
                    localError = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Last 7 days")
            }

            Button(
                onClick = {
                    Log.i("SummaryScreen", "Last 30 days button clicked")
                    val today = LocalDate.now(zone)
                    endMillis = today.plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli() - 1
                    startMillis = today.minusDays(30)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                    
                    // Update display dates
                    val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
                    val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
                    startDateDisplay = startDate.toString()
                    endDateDisplay = endDate.toString()
                    
                    Log.i("SummaryScreen", "Updated: startMillis=$startMillis, endMillis=$endMillis")
                    rangeLabel = "Last 30 days"
                    localError = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Last 30 days")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                showDateRangePicker = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Custom Range")
        }
        
        // Date Range Picker Dialog
        if (showDateRangePicker) {
            DateRangePickerModal(
                onDismiss = { showDateRangePicker = false },
                onConfirm = { start, end ->
                    Log.i("SummaryScreen", "Custom range selected: start=$start, end=$end")
                    if (start != null && end != null) {
                        if (start > end) {
                            localError = "Invalid date range"
                        } else {
                            startMillis = start
                            endMillis = end
                            
                            // Update display dates
                            val startDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                            val endDate = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                            startDateDisplay = startDate.toString()
                            endDateDisplay = endDate.toString()
                            
                            rangeLabel = "Custom"
                            localError = null
                            Log.i("SummaryScreen", "Updated date range: startMillis=$startMillis, endMillis=$endMillis")
                        }
                    }
                    showDateRangePicker = false
                }
            )
        }

        /* =========================
           ERROR MESSAGE
           ========================= */
        localError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        /* =========================
           SUMMARY DATA
           ========================= */
        // Filter calls for the selected date range
        val metrics = remember(startMillis, endMillis, incomingCalls) {
            Log.i("SummaryScreen", "Recalculating metrics: startMillis=$startMillis, endMillis=$endMillis, incomingCalls=${incomingCalls.size}")
            
            // Check if timestamps are valid
            val hasValidTimestamps = incomingCalls.any { it.getCreatedAtMillis() > 0 }
            Log.i("SummaryScreen", "Has valid timestamps: $hasValidTimestamps")
            
            val callsInRange = if (!hasValidTimestamps) {
                // If no valid timestamps, show all calls
                Log.w("SummaryScreen", "No valid timestamps found, showing all calls")
                incomingCalls
            } else {
                incomingCalls.filter { call ->
                    var callTimeMillis = call.getCreatedAtMillis()
                    // If getCreatedAtMillis returns a value that looks like seconds (< year 2000), convert it
                    if (callTimeMillis > 0 && callTimeMillis < 946684800000L) {
                        callTimeMillis *= 1000 // Convert seconds to milliseconds
                    }
                    callTimeMillis in startMillis..endMillis
                }
            }
            
            Log.i("SummaryScreen", "Calls in range: ${callsInRange.size}")
            generateMetrics(callsInRange)
        }

        /* =========================
           EMPTY STATE
           ========================= */
        if (metrics.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No data available",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "No incoming calls found for the selected period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            items(metrics) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "$startDateDisplay to $endDateDisplay — $rangeLabel",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Key Metrics Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Total Calls
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${item.totalCalls}",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Total Calls",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Average Call Time (in seconds)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                // Calculate average duration for calls in this date range
                                // This will be calculated in the metrics generation
                                val avgTimeText = if (item.avgDuration > 0) {
                                    "${item.avgDuration.toInt()}s"
                                } else {
                                    "—"
                                }
                                Text(
                                    text = avgTimeText,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Avg Call Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Average Confidence Score
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                val confidenceScore =
                                    if (item.avgConfidence >= 0)
                                        "${(item.avgConfidence * 100).toInt()}%"
                                    else "N/A"
                                Text(
                                    text = confidenceScore,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Avg Confidence",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Divider
                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        // Call Status Breakdown
                        Text(
                            text = "Call Status",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("✓ Answered: ${item.answered}")
                            Text("✗ Missed: ${item.missed}")
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        // Detection Results - Only Suspicious
                        Text(
                            text = "Detection Results",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("⚠ Suspicious: ${item.suspicious}")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Simple Bar Graph for Daily Distribution
                        if (metrics.size > 1) {
                            Text(
                                text = "Daily Distribution",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                            SimpleCallsBarGraph(metrics = metrics)
                        }
                    }
                }
            }
        }
        }
    }
}

/* =========================
   SIMPLE BAR GRAPH COMPOSABLE
   ========================= */

@Composable
private fun SimpleCallsBarGraph(metrics: List<SummaryMetrics>) {
    val maxCalls = metrics.maxOfOrNull { it.totalCalls }?.toFloat() ?: 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        metrics.take(7).forEach { metric ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(50.dp)
                )

                val barWidth = (metric.totalCalls.toFloat() / maxCalls) * 200f
                Surface(
                    modifier = Modifier
                        .width(barWidth.dp)
                        .height(20.dp),
                    color = when {
                        metric.suspicious > 0 -> MaterialTheme.colorScheme.error
                        metric.warned > 0 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = MaterialTheme.shapes.small
                ) {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerModal(
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(horizontal = 0.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(0.dp)
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = {
                        Text(
                            text = "Select Date Range",
                            modifier = Modifier.padding(16.dp)
                        )
                    },
                    showModeToggle = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val zone = ZoneId.systemDefault()
                            val start = dateRangePickerState.selectedStartDateMillis?.let { millis ->
                                // Convert from UTC midnight to local start of day
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                    .atStartOfDay(zone)
                                    .toInstant()
                                    .toEpochMilli()
                            }
                            val end = dateRangePickerState.selectedEndDateMillis?.let { millis ->
                                // Convert from UTC midnight to local end of day
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                    .plusDays(1)
                                    .atStartOfDay(zone)
                                    .toInstant()
                                    .toEpochMilli() - 1
                            }
                            onConfirm(start, end)
                        },
                        enabled = dateRangePickerState.selectedStartDateMillis != null &&
                                dateRangePickerState.selectedEndDateMillis != null
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
