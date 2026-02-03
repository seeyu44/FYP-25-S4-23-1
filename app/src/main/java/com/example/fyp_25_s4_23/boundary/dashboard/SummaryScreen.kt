package com.example.fyp_25_s4_23.boundary.dashboard

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import java.time.LocalDate
import java.time.ZoneId

/* =========================
   SUMMARY SCREEN
   ========================= */

@Composable
fun SummaryScreen(
    user: UserAccount,
    metrics: List<SummaryMetrics>,
    isLoading: Boolean,
    onRequestSummary: (startMillis: Long, endMillis: Long, daily: Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    var rangeLabel by remember { mutableStateOf("Last 7 days") }
    var startMillis by remember { mutableStateOf(0L) }
    var endMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var isPeriodWeekly by remember { mutableStateOf(true) }
    var hasInitialLoad by remember { mutableStateOf(false) }

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
            .minusDays(6)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        endMillis = newEndMillis
        startMillis = newStartMillis
        
        // Trigger initial data load
        onRequestSummary(newStartMillis, newEndMillis, false)
        hasInitialLoad = true
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
                    val today = LocalDate.now(zone)
                    endMillis = today.plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli() - 1
                    startMillis = today.minusDays(6)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                    rangeLabel = "Last 7 days"
                    isPeriodWeekly = true
                    localError = null
                    onRequestSummary(startMillis, endMillis, false)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Last 7 days")
            }

            Button(
                onClick = {
                    val today = LocalDate.now(zone)
                    endMillis = today.plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli() - 1
                    startMillis = today.minusDays(29)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                    rangeLabel = "Last 30 days"
                    isPeriodWeekly = false
                    localError = null
                    onRequestSummary(startMillis, endMillis, true)
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
                        onRequestSummary(startMillis, endMillis, !isPeriodWeekly)
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
           LOADING STATE
           ========================= */
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        /* =========================
           SUMMARY LIST
           ========================= */
        if (metrics.isEmpty() && !isLoading) {
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
