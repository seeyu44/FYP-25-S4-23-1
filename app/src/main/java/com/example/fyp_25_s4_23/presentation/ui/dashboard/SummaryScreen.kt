package com.example.fyp_25_s4_23.presentation.ui.dashboard

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.domain.entities.CallRecord
import com.example.fyp_25_s4_23.entity.domain.entities.UserAccount
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SummaryScreen(
    user: UserAccount,
    callRecords: List<CallRecord>,
    onBack: () -> Unit,
    fetchAggregates: (suspend (Long, Long, Boolean) -> List<SummaryMetrics>)? = null
) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Column {
                Text(text = "Summary for ${user.displayName}", style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = onBack) { Text("Back") }
        }

        var periodDaily by remember { mutableStateOf(true) }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            RadioButton(selected = periodDaily, onClick = { periodDaily = true })
            Text(text = "Daily", modifier = Modifier.padding(start = 4.dp, end = 12.dp))
            RadioButton(selected = !periodDaily, onClick = { periodDaily = false })
            Text(text = "Weekly", modifier = Modifier.padding(start = 4.dp))
        }

        var rangeLabel by remember { mutableStateOf("Last 7 days") }
        var startMillis by remember { mutableStateOf(0L) }
        var endMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        var localError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            startMillis = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                startMillis = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
                rangeLabel = "Last 7 days"
                localError = null
            }) { Text("Last 7 days") }

            Button(onClick = {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                startMillis = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
                rangeLabel = "Last 30 days"
                localError = null
            }, modifier = Modifier.padding(start = 8.dp)) { Text("Last 30 days") }

            Button(onClick = {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                startMillis = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                rangeLabel = "This month"
                localError = null
            }, modifier = Modifier.padding(start = 8.dp)) { Text("This month") }

            Button(onClick = {
                showCustomDatePicker(ctx) { start, end ->
                    if (start > end) {
                        localError = "Invalid range: start date must be before or equal to end date"
                    } else {
                        startMillis = start
                        endMillis = end
                        rangeLabel = "Custom"
                        localError = null
                    }
                }
            }, modifier = Modifier.padding(start = 8.dp)) { Text("Custom") }
        }

        val filtered = callRecords.filter { it.metadata.startTimeMillis in startMillis..endMillis }

        if (localError != null) {
            Text(text = localError!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        var summaryList by remember { mutableStateOf<List<SummaryMetrics>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(startMillis, endMillis, periodDaily) {
            if (startMillis > endMillis) {
                localError = "Invalid date range"
                summaryList = emptyList()
                return@LaunchedEffect
            }
            isLoading = true
            summaryList = try {
                if (fetchAggregates != null) {
                    fetchAggregates(startMillis, endMillis, periodDaily)
                } else {
                    if (periodDaily) buildDailySummary(filtered) else buildWeeklySummary(filtered)
                }
            } catch (t: Throwable) {
                if (periodDaily) buildDailySummary(filtered) else buildWeeklySummary(filtered)
            } finally {
                isLoading = false
            }
        }

        if (isLoading) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            items(summaryList) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${item.label} — $rangeLabel", style = MaterialTheme.typography.titleMedium)
                        Text("Total calls: ${item.totalCalls}")
                        Text("Answered: ${item.answered}   Missed: ${item.missed}")
                        Text("Suspicious: ${item.suspicious}   Blocked: ${item.blocked}   Warned: ${item.warned}")
                        val avg = if (item.avgConfidence >= 0) "${(item.avgConfidence * 100).toInt()}%" else "N/A"
                        Text("Average confidence for deepfake detections: $avg")
                    }
                }
            }
        }
    }
}

private fun showCustomDatePicker(context: Context, onRangeSelected: (Long, Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var startYear = today.year
    var startMonth = today.monthValue - 1
    var startDay = today.dayOfMonth

    DatePickerDialog(context, { _, y, m, d ->
        startYear = y
        startMonth = m
        startDay = d
        DatePickerDialog(context, { _, ey, em, ed ->
            val startDate = LocalDate.of(startYear, startMonth + 1, startDay)
            val endDate = LocalDate.of(ey, em + 1, ed)
            val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            onRangeSelected(startMillis, endMillis)
        }, today.year, today.monthValue - 1, today.dayOfMonth).show()
    }, today.year, today.monthValue - 1, today.dayOfMonth).show()
}