package com.example.fyp_25_s4_23.boundary.audio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.fyp_25_s4_23.ml.AudioFeatureExtractor
import com.example.fyp_25_s4_23.ml.ModelRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

data class AudioTestResult(
    val fileName: String,
    val confidence: Float,
    val isDeepfake: Boolean,
    val spectrogram: Bitmap,
    val explanation: String
)

object AudioTestProcessor {
    private const val SAMPLE_RATE = 16_000
    private const val SECONDS = 3
    private const val TARGET_SAMPLES = SAMPLE_RATE * SECONDS

    suspend fun analyze(context: Context, uri: Uri): AudioTestResult {
        val (pcm, name) = loadPcm16(context, uri)
        val clipped = clipOrPad(pcm, TARGET_SAMPLES)

        val energy = AudioFeatureExtractor.energy(clipped)
        val zcr = AudioFeatureExtractor.zeroCrossRate(clipped)
        val features = floatArrayOf(energy, zcr)

        val runner = ModelRunner(context)
        val prob = runner.infer(features)

        val spectrogram = renderSpectrogram(clipped)
        val explanation = if (prob >= 0.5f) {
            "Model confidence is high for deepfake; energy/zcr pattern matched known synthetic cues."
        } else {
            "Model confidence is low for deepfake; no strong synthetic cues detected."
        }

        return AudioTestResult(
            fileName = name,
            confidence = prob,
            isDeepfake = prob >= 0.5f,
            spectrogram = spectrogram,
            explanation = explanation
        )
    }

    private fun clipOrPad(samples: ShortArray, target: Int): ShortArray {
        return when {
            samples.size == target -> samples
            samples.size > target -> samples.copyOfRange(0, target)
            else -> ShortArray(target).also { out ->
                System.arraycopy(samples, 0, out, 0, samples.size)
            }
        }
    }

    private fun loadPcm16(context: Context, uri: Uri): Pair<ShortArray, String> {
        context.contentResolver.openInputStream(uri).use { stream ->
            val bytes = stream?.readBytes() ?: ByteArray(0)
            val hasWavHeader = bytes.size > 44 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()
            val offset = if (hasWavHeader) 44 else 0
            val byteCount = (bytes.size - offset).coerceAtLeast(0)
            val shorts = ShortArray(byteCount / 2)
            if (byteCount > 0) {
                ByteBuffer.wrap(bytes, offset, byteCount).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            }
            val name = uri.lastPathSegment ?: "audio"
            return shorts to name
        }
    }

    private fun renderSpectrogram(samples: ShortArray): Bitmap {
        val frameSize = 256
        val hop = 128
        val bins = 64
        val floatSamples = samples.map { it / 32768f }.toFloatArray()
        val frames = ((floatSamples.size - frameSize) / hop).coerceAtLeast(1)
        val spec = Array(frames) { FloatArray(bins) }

        val window = hanning(frameSize)
        var frameIdx = 0
        var i = 0
        while (i + frameSize <= floatSamples.size && frameIdx < frames) {
            val frame = FloatArray(frameSize) { k -> floatSamples[i + k] * window[k] }
            spec[frameIdx] = dftMag(frame, bins)
            i += hop
            frameIdx++
        }

        var max = Float.NEGATIVE_INFINITY
        var min = Float.POSITIVE_INFINITY
        spec.forEach { row ->
            row.forEach { v ->
                if (v > max) max = v
                if (v < min) min = v
            }
        }
        val range = (max - min).coerceAtLeast(1e-6f)

        val bmp = Bitmap.createBitmap(bins, frames, Bitmap.Config.ARGB_8888)
        for (y in 0 until frames) {
            for (x in 0 until bins) {
                val norm = ((spec[y][x] - min) / range).coerceIn(0f, 1f)
                val gray = (norm * 255).toInt()
                val color = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                bmp.setPixel(x, y, color)
            }
        }
        return Bitmap.createScaledBitmap(bmp, bins * 4, frames * 4, false)
    }

    private fun hanning(n: Int): FloatArray =
        FloatArray(n) { i -> (0.5f - 0.5f * cos(2.0 * Math.PI * i / n)).toFloat() }

    private fun dftMag(frame: FloatArray, bins: Int): FloatArray {
        val n = frame.size
        val mags = FloatArray(bins)
        for (k in 0 until bins) {
            var re = 0.0
            var im = 0.0
            for (t in 0 until n) {
                val angle = 2.0 * Math.PI * k * t / n
                re += frame[t] * cos(angle)
                im -= frame[t] * sin(angle)
            }
            val mag = sqrt(re * re + im * im).toFloat()
            mags[k] = 20 * log10(mag.coerceAtLeast(1e-6f))
        }
        return mags
    }
}
