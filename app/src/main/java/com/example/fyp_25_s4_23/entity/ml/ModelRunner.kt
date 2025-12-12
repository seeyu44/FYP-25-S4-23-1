package com.example.fyp_25_s4_23.entity.ml

import android.content.Context
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import android.util.Log
import java.nio.FloatBuffer
import java.io.File
import java.io.InputStream
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

    fun loadAudioFromAsset(assetName: String): FloatArray? {
        return preprocessor.loadAudioFromAsset(assetName)
    }

    fun preprocess(wav: FloatArray): Array<FloatArray> {
        return preprocessor.preprocess(wav)
    }


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

    fun inferFromAsset(assetName: String): ModelOutput? {
        val wav = preprocessor.loadAudioFromAsset(assetName) ?: return null
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
            OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), shape).use { tensor ->
                session.run(mapOf("mel" to tensor)).use { res ->
                    val output = res.firstOrNull()?.value
                    val logit = when (output) {
                        is FloatArray -> output.firstOrNull()
                        is Array<*> -> (output.firstOrNull() as? FloatArray)?.firstOrNull()
                        is OnnxTensor -> {
                            val fb = output.floatBuffer
                            fb.rewind()
                            if (fb.hasArray()) fb.array().firstOrNull()
                            else if (fb.remaining() > 0) {
                                val tmp = FloatArray(fb.remaining())
                                fb.get(tmp)
                                tmp.firstOrNull()
                            } else null
                        }
                        else -> {
                            Log.e(TAG, "Unsupported model output type: ${output?.javaClass}")
                            null
                        }
                    } ?: return null
                    return sigmoid(logit)
                }
            }
        }.onFailure { Log.e(TAG, "Inference failed", it) }.getOrNull()
    }

    private fun loadSession(): OrtSession {
        // Some ONNX models reference external data; copy model (and optional .data) to cache so ORT has a real path.
        val modelFile = File(context.cacheDir, modelFileName)
        copyAsset(context.assets, modelFileName, modelFile)
        val dataName = "$modelFileName.data"
        copyAssetIfExists(context.assets, dataName, File(context.cacheDir, dataName))
        Log.i(TAG, "Model path=${modelFile.absolutePath} size=${modelFile.length()} dataExists=${File(context.cacheDir, dataName).exists()} dataSize=${File(context.cacheDir, dataName).length()}")
        return env.createSession(modelFile.absolutePath)
    }

    private fun assetExists(assets: AssetManager, name: String): Boolean =
        runCatching { assets.open(name).close(); true }.getOrDefault(false)

    private fun copyAsset(assets: AssetManager, name: String, target: File) {
        assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun copyAssetIfExists(assets: AssetManager, name: String, target: File) {
        runCatching { copyAsset(assets, name, target) }
            .onFailure { Log.w(TAG, "Asset $name not copied: ${it.message}") }
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x))).coerceIn(0f, 1f)

    companion object { private const val TAG = "ModelRunner" }
}