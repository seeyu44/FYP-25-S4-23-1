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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

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
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Running..."
        score = null
        spectrogram = null
        LaunchedEffect(uri) {
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
        Text(
            "Supports common audio (WAV/MP3/MP4/M4A/FLAC via platform decoder). Preprocessing matches training: 16k, 3s pad/crop, mel (n_fft=1024, hop=256, n_mels=64), log-dB, normalize.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun melToBitmap(mel: Array<FloatArray>): Bitmap {
    val height = mel.size
    val width = mel[0].size
    var minVal = Float.MAX_VALUE
    var maxVal = -Float.MAX_VALUE
    for (m in mel.indices) for (t in mel[m].indices) {
        val v = mel[m][t]
        if (v < minVal) minVal = v
        if (v > maxVal) maxVal = v
    }
    val range = max(1e-5f, maxVal - minVal)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    var idx = 0
    for (y in 0 until height) {
        val m = height - 1 - y // flip so low freqs at bottom
        for (x in 0 until width) {
            val norm = ((mel[m][x] - minVal) / range).coerceIn(0f, 1f)
            val c = (norm * 255).toInt()
            pixels[idx++] = 0xFF shl 24 or (c shl 16) or (c shl 8) or c
        }
    }
    bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    return bmp
}
