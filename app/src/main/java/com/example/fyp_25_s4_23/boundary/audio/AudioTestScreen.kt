package com.example.fyp_25_s4_23.boundary.audio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AudioTestCard() {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var result by remember { mutableStateOf<AudioTestResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            uri = picked
            result = null
            isLoading = true
            scope.launch {
                try {
                    result = AudioTestProcessor.analyze(context, picked)
                } catch (e: Exception) {
                    result = null
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Audio Tester", style = MaterialTheme.typography.titleMedium)
            Text("Pick a WAV/PCM clip to see its spectrogram and the model's confidence.")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    picker.launch(arrayOf("audio/*"))
                }) {
                    Text("Pick audio file")
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            result?.let { res ->
                Spacer(Modifier.height(12.dp))
                Text("File: ${res.fileName}")
                Text("Prediction: ${if (res.isDeepfake) "Deepfake" else "Likely real"}")
                Text("Confidence: ${"%.2f".format(res.confidence)}")
                Text(res.explanation, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = res.spectrogram.asImageBitmap(),
                    contentDescription = "Spectrogram",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}
