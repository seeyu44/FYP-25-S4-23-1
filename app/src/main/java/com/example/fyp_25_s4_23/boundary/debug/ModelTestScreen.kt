package com.example.fyp_25_s4_23.boundary.debug

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

private const val DEEPFAKE_THRESHOLD = 0.7f

@Composable
fun ModelTestScreen(
    modelRunner: ModelRunner,
    detectionEnabled: Boolean = true
) {
    var status by remember { mutableStateOf("Idle") }
    var score by remember { mutableStateOf<Float?>(null) }
    var spectrogram by remember { mutableStateOf<Bitmap?>(null) }
    var spectrogramFrames by remember { mutableStateOf<Int?>(null) }
    var lastSelection by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val bundledClips = remember { loadBundledClips(context) }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Model Test", style = MaterialTheme.typography.headlineSmall)
        Box {
            Button(
                onClick = {
                    if (!detectionEnabled) {
                        status = "Deepfake detection is OFF. Enable it to run tests."
                    } else {
                        menuExpanded = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pick bundled audio and run") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (bundledClips.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No assets found. Add files under assets/demo_audio/") },
                        onClick = { menuExpanded = false },
                        enabled = false
                    )
                } else {
                    bundledClips.forEach { clip ->
                        DropdownMenuItem(
                            text = { Text(clip) },
                            onClick = {
                                menuExpanded = false
                                status = "Running..."
                                score = null
                                spectrogram = null
                                spectrogramFrames = null
                                lastSelection = clip
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        modelRunner.inferFromAsset("demo_audio/$clip")
                                    }
                                    if (result != null) {
                                        score = result.score
                                        spectrogramFrames = result.mel[0].size
                                        spectrogram = melToBitmap(result.mel)
                                        status = if (result.score != null) "Done" else "Done (no confidence output)"
                                    } else {
                                        status = "Failed (see logcat)"
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        lastSelection?.let { Text("Selected: $it", style = MaterialTheme.typography.bodyMedium) }
        Text("Status: $status")
        if (status.startsWith("Done")) {
            score?.let {
                val percent = it * 100f
                val verdict = if (it >= DEEPFAKE_THRESHOLD) "Likely deepfake" else "Likely human"
                Text(
                    "$verdict - Confidence: ${String.format("%.1f", percent)}% "
                )
            } ?: run {
                val selectionLabel = lastSelection ?: "selection"
                Text("Confidence: unavailable for $selectionLabel")
            }
        }
        spectrogram?.let { bmp ->
            Text("Spectrogram (mel):", style = MaterialTheme.typography.labelLarge)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Mel spectrogram",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
            spectrogramFrames?.let { frames ->
                val seconds = frames * 256f / 16_000f
                Text(
                    "Resolution: 64 mel bands x $frames frames (~${String.format("%.2f", seconds)} s, hop=256, sr=16 kHz, log-dB, normalized).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(
            "Supports common audio (WAV/MP3/MP4/M4A/FLAC via platform decoder). Preprocessing matches training: 16k, 3s pad/crop, mel (n_fft=1024, hop=256, n_mels=64), log-dB, normalize.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun loadBundledClips(context: Context): List<String> =
    runCatching {
        context.assets.list("demo_audio")
            ?.filter { it.endsWith(".wav", true) || it.endsWith(".flac", true) }
            ?: emptyList()
    }.getOrDefault(emptyList())

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
        val m = height - 1 - y
        for (x in 0 until width) {
            val norm = ((mel[m][x] - minVal) / range).coerceIn(0f, 1f)
            val c = (norm * 255).toInt()
            pixels[idx++] = 0xFF shl 24 or (c shl 16) or (c shl 8) or c
        }
    }
    bmp.setPixels(pixels, 0, width, 0, 0, width, height)
    return bmp
}
