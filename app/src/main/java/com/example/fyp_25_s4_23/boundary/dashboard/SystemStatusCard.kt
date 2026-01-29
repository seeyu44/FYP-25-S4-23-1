package com.example.fyp_25_s4_23.boundary.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue



@Composable
fun SystemStatusCard(
    uptime: String,
    isSystemHealthy: Boolean,
    memoryUsedGb: Float,
    latencyMs: Int,
    latencyTrend: List<Int>,
    memoryTrend: List<Float>
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = if (isSystemHealthy)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column {

            // ===== HEADER (always visible) =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSystemHealthy)
                            "System running normally"
                        else
                            "System experiencing delays",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Uptime: $uptime",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand system status"
                    )
                }
            }

            // ===== EXPANDED CONTENT =====
            if (expanded) {
                Divider()

                Column(modifier = Modifier.padding(16.dp)) {

                    Text("Connection Health (${latencyMs} ms)")
                    SparklineInt(data = latencyTrend)

                    Spacer(Modifier.height(12.dp))

                    Text("Memory Usage (GB)")
                    SparklineFloat(data = memoryTrend)
                }
            }
        }
    }
}

@Composable
fun SparklineInt(data: List<Int>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        if (data.size < 2) return@Canvas

        val max = data.maxOrNull() ?: return@Canvas
        val min = data.minOrNull() ?: return@Canvas

        val points = data.mapIndexed { i, value ->
            val x = size.width * i / (data.size - 1)
            val y = size.height * (1f - (value - min).toFloat() / (max - min + 1))
            Offset(x, y)
        }

        for (i in 0 until points.lastIndex) {
            drawLine(
                color = Color(0xFF4CAF50),
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f
            )
        }
    }
}

@Composable
fun SparklineFloat(data: List<Float>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        if (data.size < 2) return@Canvas

        val max = data.maxOrNull() ?: return@Canvas
        val min = data.minOrNull() ?: return@Canvas

        val points = data.mapIndexed { i, value ->
            val x = size.width * i / (data.size - 1)
            val y = size.height * (1f - (value - min) / (max - min + 0.01f))
            Offset(x, y)
        }

        for (i in 0 until points.lastIndex) {
            drawLine(
                color = Color(0xFF2196F3),
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f
            )
        }
    }
}

