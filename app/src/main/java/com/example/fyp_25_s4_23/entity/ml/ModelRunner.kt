package com.example.fyp_25_s4_23.entity.ml

import android.content.Context
import android.net.Uri
import android.util.Log
import com.microsoft.onnxruntime.OnnxTensor
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
import java.io.InputStream
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Runs ONNX model exported from melcnn.pt.
 * Expects input: [1, 1, 64, time] float32 mel spectrogram (normalized dB).
 */
class ModelRunner(
    private val context: Context,
    private val modelFileName: String = "melcnn.onnx",
    private val config: ModelConfig = ModelConfig()
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy { loadSession() }
    private val preprocessor = AudioPreprocessor(context)

    fun warmUp() {
        runCatching {
            val dummy = Array(1) { Array(1) { Array(64) { FloatArray(10) } } }
            inferTensor(dummy)
        }.onFailure { Log.w(TAG, "Warm-up failed (non-fatal): ${it.message}") }
    }

    data class ModelOutput(val score: Float?, val mel: Array<FloatArray>)

    fun inferFromUri(uri: Uri): ModelOutput? {
        val wav = preprocessor.loadAudioFromUri(uri) ?: return null
        val mel = preprocessor.preprocess(wav)
        val score = inferMel(mel)
        return ModelOutput(score, mel)
    }

    fun inferFromAssetWav(assetName: String): ModelOutput? {
        val wav = preprocessor.loadWavFromAssets(assetName) ?: return null
        val mel = preprocessor.preprocess(wav)
        val score = inferMel(mel)
        return ModelOutput(score, mel)
    }

    fun inferFromStream(stream: InputStream): ModelOutput? {
        val wav = preprocessor.loadWavFromStream(stream) ?: return null
        val mel = preprocessor.preprocess(wav)
        val score = inferMel(mel)
        return ModelOutput(score, mel)
    }

    fun inferMel(mel: Array<FloatArray>): Float? {
        val time = mel[0].size
        val input = Array(1) { Array(1) { Array(64) { FloatArray(time) } } }
        for (m in 0 until 64) {
            for (t in 0 until time) {
                input[0][0][m][t] = mel[m][t]
            }
        }
        return inferTensor(input)
    }

    private fun inferTensor(input: Array<Array<Array<FloatArray>>>): Float? {
        return runCatching {
            val shape = longArrayOf(1, 1, 64, input[0][0][0].size.toLong())
            val flat = FloatArray(1 * 1 * 64 * input[0][0][0].size)
            var idx = 0
            for (m in 0 until 64) {
                for (t in input[0][0][m].indices) {
                    flat[idx++] = input[0][0][m][t]
                }
            }
            env.use {
                OnnxTensor.createTensor(it, FloatBuffer.wrap(flat), shape).use { tensor ->
                    session.run(mapOf("mel" to tensor)).use { res ->
                        val output = res[0].value as FloatArray
                        val logit = output.firstOrNull() ?: return null
                        return sigmoid(logit)
                    }
                }
            }
        }.onFailure { Log.e(TAG, "Inference failed", it) }.getOrNull()
    }

    private fun loadSession(): OrtSession {
        val bytes = context.assets.open(modelFileName).use { it.readBytes() }
        return env.createSession(bytes)
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x))).coerceIn(0f, 1f)

    companion object { private const val TAG = "ModelRunner" }
}
