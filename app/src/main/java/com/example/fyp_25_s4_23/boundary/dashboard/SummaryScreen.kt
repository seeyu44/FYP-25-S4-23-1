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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    var periodDaily by remember { mutableStateOf(true) }
    var rangeLabel by remember { mutableStateOf("Last 7 days") }
    var startMillis by remember { mutableStateOf(0L) }
    var endMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var localError by remember { mutableStateOf<String?>(null) }

    /* =========================
       INITIAL DATE RANGE
       ========================= */
    LaunchedEffect(Unit) {
        val today = LocalDate.now(zone)

        endMillis = today
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() - 1

        startMillis = today
            .minusDays(6)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    /* =========================
       REQUEST DATA WHEN INPUTS CHANGE
       ========================= */
    LaunchedEffect(startMillis, endMillis, periodDaily) {
        if (startMillis <= endMillis) {
            onRequestSummary(startMillis, endMillis, periodDaily)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        /* =========================
           PERIOD TOGGLE
           ========================= */
        Row(modifier = Modifier.padding(top = 12.dp)) {
            RadioButton(
                selected = periodDaily,
                onClick = { periodDaily = true }
            )
            Text("Daily", modifier = Modifier.padding(end = 12.dp))

            RadioButton(
                selected = !periodDaily,
                onClick = { periodDaily = false }
            )
            Text("Weekly")
        }

        /* =========================
           DATE RANGE BUTTONS
           ========================= */
        Row(modifier = Modifier.padding(top = 12.dp)) {

            Button(onClick = {
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
                localError = null
            }) {
                Text("Last 7 days")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {
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
                localError = null
            }) {
                Text("Last 30 days")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {
                val today = LocalDate.now(zone)
                endMillis = today.plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli() - 1
                startMillis = today.withDayOfMonth(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                rangeLabel = "This month"
                localError = null
            }) {
                Text("This month")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
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
        }) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            items(metrics) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${item.label} — $rangeLabel",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Total calls: ${item.totalCalls}")
                        Text("Answered: ${item.answered}   Missed: ${item.missed}")
                        Text("Suspicious: ${item.suspicious}   Blocked: ${item.blocked}   Warned: ${item.warned}")

                        val avg =
                            if (item.avgConfidence >= 0)
                                "${(item.avgConfidence * 100).toInt()}%"
                            else "N/A"

                        Text("Average confidence: $avg")
                    }
                }
            }
        }
    }
}

/* =========================
   DATE PICKER
   ========================= */

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
