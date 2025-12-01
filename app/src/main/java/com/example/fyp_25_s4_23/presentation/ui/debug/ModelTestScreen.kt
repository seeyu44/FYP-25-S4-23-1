package com.example.fyp_25_s4_23.presentation.ui.debug

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Debug UI to pick an audio file and run it through the model,
 * showing confidence and a spectrogram preview.
 */
@Composable
fun ModelTestScreen(
    modelRunner: ModelRunner
) {
    var status by remember { mutableStateOf("Idle") }
    var score by remember { mutableStateOf<Float?>(null) }
    var spectrogram by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Running..."
        score = null
        spectrogram = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                modelRunner.inferFromUri(uri)
            }
            if (result != null) {
                score = result.score
                spectrogram = melToBitmap(result.mel)
                status = "Done"
            } else {
                status = "Failed (see logcat)"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Model Test", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { launcher.launch("audio/*") }) {
            Text("Pick audio and run")
        }
        Text("Status: $status")
        score?.let { Text("Confidence: ${String.format(\"%.3f\", it)}") }
        spectrogram?.let { bmp ->
            Text("Spectrogram (mel):", style = MaterialTheme.typography.labelLarge)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Mel spectrogram",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
       
