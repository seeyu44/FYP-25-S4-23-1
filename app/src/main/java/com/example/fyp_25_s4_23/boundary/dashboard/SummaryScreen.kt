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
import com.example.fyp_25_s4_23.entity.domain.entities.FirebaseCallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
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
    var localError by remember { mutableStateOf<String?>(null) }

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
            val completedCalls = callsForDay.filter { it.status == "completed" }
            val missedCalls = callsForDay.filter { it.status in listOf("missed", "declined") }
            
            // Calculate detection metrics using the detection score
            val callsWithScore = callsForDay.filter { it.detectionScore != null }
            val suspiciousCalls = callsWithScore.filter { (it.detectionScore ?: 0.0) >= 0.5 }
            val warnedCalls = callsWithScore.filter { 
                val score = it.detectionScore ?: 0.0
                score >= 0.5 && score < 0.8 
            }
            val avgScore = if (callsWithScore.isNotEmpty()) {
                callsWithScore.mapNotNull { it.detectionScore }.average()
            } else {
                -1.0
            }

            SummaryMetrics(
                label = date,
                totalCalls = callsForDay.size,
                answered = completedCalls.size,
                missed = missedCalls.size,
                suspicious = suspiciousCalls.size,
                blocked = 0, // Would need blocked status from Firebase
                warned = warnedCalls.size,
                avgConfidence = avgScore
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
                showCustomDatePicker(context) { start, end ->
                    if (start > end) {
                        localError = "Invalid date range"
                    } else {
                        startMillis = start
                        endMillis = end
                        rangeLabel = "Custom"
                        localError = null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Custom Range")
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
                            text = "${item.label} — $rangeLabel",
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

                            // Average Call Time
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Avg Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            // Average Detection Rate
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                val detectionRate =
                                    if (item.avgConfidence >= 0)
                                        "${(item.avgConfidence * 100).toInt()}%"
                                    else "N/A"
                                Text(
                                    text = detectionRate,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Detection Rate",
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Detection Breakdown
                        Text(
                            text = "Detection Results",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("⚠️ Suspicious: ${item.suspicious}")
                            Text("🚫 Blocked: ${item.blocked}")
                            Text("⚡ Warned: ${item.warned}")
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

                Text(
                    text = "${metric.totalCalls}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

private fun showCustomDatePicker(
    context: Context,
    onRangeSelected: (Long, Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    DatePickerDialog(
        context,
        { _, y, m, d ->
            val startDate = LocalDate.of(y, m + 1, d)
            DatePickerDialog(
                context,
                { _, ey, em, ed ->
                    val endDate = LocalDate.of(ey, em + 1, ed)
                    val startMillis =
                        startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    val endMillis =
                        endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    onRangeSelected(startMillis, endMillis)
                },
                today.year,
                today.monthValue - 1,
                today.dayOfMonth
            ).show()
        },
        today.year,
        today.monthValue - 1,
        today.dayOfMonth
    ).show()
}
