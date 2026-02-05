package com.example.fyp_25_s4_23.control.detection

import android.content.Context
import android.util.Log
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.entity.data.db.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Real-time deepfake detection service for WebRTC calls.
 * Buffers audio chunks, runs inference periodically, and emits detection results.
 */
class DeepfakeDetectionService(
    private val context: Context,
    private val callId: String,
    private val detectionThreshold: Float = 0.7f
) {
    private val TAG = "DeepfakeDetection"
    
    // Database for direct SQLite access (bypasses Room's transaction system)
    private val database: AppDatabase = AppDatabase.getInstance(context)
    
    // Model runner for inference
    private val modelRunner = ModelRunner(context)
    
    // Detection state
    private val _detectionState = MutableStateFlow(DetectionState())
    val detectionState: StateFlow<DetectionState> = _detectionState
    
    // Audio buffer queue
    private val audioBufferQueue = ConcurrentLinkedQueue<FloatArray>()
    private val bufferSizeSeconds = 3 // Match training data clip size
    private val sampleRate = 16000 // Match AudioRecord capture rate (16kHz)
    private val targetSamples = sampleRate * bufferSizeSeconds // 48,000 samples = 3 seconds
    private val maxBufferedSamples = targetSamples * 2
    private var queuedSamples = 0
    
    // Background processing
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null
    private var isRunning = false
    private var isPaused = false  // NEW: Pause detection during demo audio playback
    
    // Callbacks
    var onDeepfakeDetected: ((Float) -> Unit)? = null
    var onDetectionUpdate: ((DetectionResult) -> Unit)? = null
    
    data class DetectionState(
        val isMonitoring: Boolean = false,
        val lastScore: Float? = null,
        val isDeepfake: Boolean = false,
        val detectionCount: Int = 0,
        val deepfakeCount: Int = 0,
        val averageScore: Float = 0f
    )
    
    data class DetectionResult(
        val score: Float,
        val isDeepfake: Boolean,
        val timestamp: Long,
        val confidence: Float
    )
    
    init {
        // Warm up model on initialization
        scope.launch {
            try {
                modelRunner.warmUp()
                Log.d(TAG, "Model warmed up successfully for call $callId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to warm up model", e)
            }
        }
    }
    
    /**
     * Start monitoring audio for deepfake detection
     */
    fun startMonitoring() {
        if (isRunning) {
            Log.w(TAG, "⚠️ startMonitoring called but already running")
            return
        }
        
        isRunning = true
        _detectionState.value = _detectionState.value.copy(isMonitoring = true)
        
        Log.d(TAG, "✅ Started deepfake monitoring for call $callId")
        
        // Start background processing
        processingJob = scope.launch {
            Log.d(TAG, "🔄 Processing coroutine started")
            var loopCount = 0
            while (isActive && isRunning) {
                try {
                    loopCount++
                    if (loopCount % 5 == 0) {
                        Log.d(TAG, "💓 Processing loop alive (iteration $loopCount), queue size: ${audioBufferQueue.size}")
                    }
                    processAudioBuffer()
                    delay(1000) // Check buffer every second
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing audio buffer", e)
                }
            }
            Log.d(TAG, "🛑 Processing coroutine stopped")
        }
    }
    
    /**
     * Stop monitoring and cleanup
     */
    fun stopMonitoring() {
        isRunning = false
        processingJob?.cancel()
        audioBufferQueue.clear()
        
        _detectionState.value = _detectionState.value.copy(isMonitoring = false)
        
        Log.d(TAG, "Stopped deepfake monitoring for call $callId")
    }
    
    /**
     * Feed audio chunk from WebRTC stream
     * @param pcmData PCM audio data (16-bit samples)
     */
    fun feedAudioChunk(pcmData: ShortArray) {
        if (!isRunning) {
            Log.w("DEEPFAKE_DETECT", "⚠️ feedAudioChunk called but service not running")
            return
        }
        
        // Log first few chunks to verify audio is being received
        if (audioBufferQueue.size < 5) {
            Log.d("DEEPFAKE_DETECT", "📥 Received audio chunk: ${pcmData.size} samples, queue size: ${audioBufferQueue.size}")
        }
        
        // Convert PCM to float array normalized to [-1, 1]
        val floatData = FloatArray(pcmData.size) { i ->
            pcmData[i] / 32768f
        }
        
        audioBufferQueue.offer(floatData)
        queuedSamples += floatData.size
        
        // Limit buffer size to prevent memory issues
        while (queuedSamples > maxBufferedSamples) {
            val removed = audioBufferQueue.poll() ?: break
            queuedSamples -= removed.size
        }
    }
    
    /**
     * Feed audio chunk from byte buffer (alternative format)
     */
    fun feedAudioBytes(bytes: ByteArray) {
        if (!isRunning) return
        
        // Convert bytes to PCM shorts
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val pcmData = ShortArray(bytes.size / 2) { buffer.short }
        
        feedAudioChunk(pcmData)
    }
    
    /**
     * Process accumulated audio buffer and run inference
     */
    private suspend fun processAudioBuffer() {
        // Calculate how many samples we have in the queue WITHOUT draining it
        val queueSize = audioBufferQueue.size
        val estimatedSamples = queuedSamples
        
        if (estimatedSamples < targetSamples) {
            // Not enough data yet - DON'T DRAIN THE QUEUE
            Log.d("DEEPFAKE_DETECT", "⏳ Buffering audio... $estimatedSamples/$targetSamples samples (need ${targetSamples - estimatedSamples} more), queue has $queueSize chunks")
            return
        }
        
        // We have enough! Now drain the required chunks
        Log.d("DEEPFAKE_DETECT", "✅ Queue has enough samples! Draining chunks for inference...")
        
        val samples = ArrayList<Float>(targetSamples)
        var drainedChunks = 0
        while (samples.size < targetSamples) {
            val chunk = audioBufferQueue.poll() ?: break
            queuedSamples -= chunk.size
            drainedChunks++
            for (value in chunk) {
                if (samples.size >= targetSamples) break
                samples.add(value)
            }
        }
        if (samples.size < targetSamples) {
            Log.d("DEEPFAKE_DETECT", "⚠️ Drained $drainedChunks chunks but only ${samples.size}/$targetSamples samples collected")
            return
        }
        
        val audioSegment = samples.toFloatArray()
        
        Log.d("DEEPFAKE_DETECT", "🔬 Running inference on ${audioSegment.size} samples...")
        
        // Run inference
        withContext(Dispatchers.Default) {
            runInference(audioSegment)
        }
    }
    
    /**
     * Run model inference on audio segment
     */
    private suspend fun runInference(audioSegment: FloatArray) {
        try {
            // Skip inference if detection is paused (e.g., during demo audio playback)
            if (isPaused) {
                Log.d("DEEPFAKE_DETECT", "⏸️ Detection paused - skipping inference")
                return
            }
            
            Log.d("DEEPFAKE_DETECT", "🔄 Preprocessing audio to mel spectrogram...")
            
            // ✅ VALIDATION: Check input audio characteristics
            val rms = kotlin.math.sqrt(audioSegment.map { it * it }.average())
            val maxSample = audioSegment.maxOrNull() ?: 0f
            val minSample = audioSegment.minOrNull() ?: 0f
            val nonZeroCount = audioSegment.count { it != 0f }
            Log.d("DEEPFAKE_DETECT", "📊 Input audio: RMS=${"%.6f".format(rms)}, Max=${"%.6f".format(maxSample)}, Min=${"%.6f".format(minSample)}, NonZeroSamples=$nonZeroCount/${audioSegment.size}")
            
            // ✅ SILENCE DETECTION: Skip inference on silent frames to prevent false positives
            val rmsThreshold = 0.01f  // Increased from 0.001 to catch more silent frames
            val minNonZeroSamples = 1000  // Increased from 100 to require more active samples
            if (rms < rmsThreshold && nonZeroCount < minNonZeroSamples) {
                Log.d("DEEPFAKE_DETECT", "⏭️ Skipping silent frame (RMS=${"%.6f".format(rms)}, nonZero=$nonZeroCount)")
                return
            }
            
            // Preprocess audio to mel spectrogram
            val mel = modelRunner.preprocess(audioSegment)
            
            Log.d("DEEPFAKE_DETECT", "🧠 Running model inference...")
            // Run inference
            val score = modelRunner.inferMel(mel) ?: run {
                Log.e("DEEPFAKE_DETECT", "❌ Model inference returned null")
                return
            }
            
            val isDeepfake = score >= detectionThreshold
            val timestamp = System.currentTimeMillis()
            
            // ⚠️ Log with audio context for debugging false positives
            val rmsContext = when {
                rms > 0.15f -> "HIGH"
                rms > 0.05f -> "NORMAL"
                rms > 0.01f -> "QUIET"
                else -> "VERY_QUIET"
            }
            
            if (isDeepfake) {
                Log.w("DEEPFAKE_DETECT", "🚨 DEEPFAKE: score=${"%.3f".format(score)} (RMS=${"%.6f".format(rms)}/$rmsContext), threshold=$detectionThreshold")
            } else {
                Log.i("DEEPFAKE_DETECT", "✅ Normal: score=${"%.3f".format(score)} (RMS=${"%.6f".format(rms)}/$rmsContext), threshold=$detectionThreshold")
            }
            
            // Update state
            val currentState = _detectionState.value
            val newDetectionCount = currentState.detectionCount + 1
            val newDeepfakeCount = currentState.deepfakeCount + if (isDeepfake) 1 else 0
            val newAverageScore = ((currentState.averageScore * currentState.detectionCount) + score) / newDetectionCount
            
            _detectionState.value = DetectionState(
                isMonitoring = true,
                lastScore = score,
                isDeepfake = isDeepfake,
                detectionCount = newDetectionCount,
                deepfakeCount = newDeepfakeCount,
                averageScore = newAverageScore
            )
            
            Log.d(TAG, "Detection result: score=$score, isDeepfake=$isDeepfake, count=$newDetectionCount")
            
            // Save to database (async) - Use direct SQLite to avoid Room's transaction system
            scope.launch(Dispatchers.IO) {
                try {
                    val db = database.openHelper.writableDatabase
                    // Use raw SQL to completely bypass Room's transaction system
                    val sql = """
                        INSERT OR REPLACE INTO detection_results 
                        (id, call_id, probability, is_deepfake, timestamp_seconds, model_version, confidence_level)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                    val args = arrayOf(
                        "${callId}_${timestamp}",
                        callId,
                        score,
                        if (isDeepfake) 1 else 0,
                        timestamp / 1000,
                        "melcnn-0.0.1",
                        "MEDIUM"
                    )
                    db.execSQL(sql, args)
                    Log.d(TAG, "✅ Detection saved to database")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save detection to database (Ask Gemini)", e)
                }
            }
            
            // Trigger callbacks
            if (isDeepfake) {
                onDeepfakeDetected?.invoke(score)
            }
            
            val result = DetectionResult(
                score = score,
                isDeepfake = isDeepfake,
                timestamp = timestamp,
                confidence = score
            )
            onDetectionUpdate?.invoke(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
        }
    }
    
    /**
     * Get current detection statistics
     */
    fun getStatistics(): DetectionStatistics {
        val state = _detectionState.value
        return DetectionStatistics(
            totalDetections = state.detectionCount,
            deepfakeDetections = state.deepfakeCount,
            averageScore = state.averageScore,
            deepfakeRate = if (state.detectionCount > 0) {
                state.deepfakeCount.toFloat() / state.detectionCount
            } else 0f
        )
    }
    
    data class DetectionStatistics(
        val totalDetections: Int,
        val deepfakeDetections: Int,
        val averageScore: Float,
        val deepfakeRate: Float
    )
    
    /**
     * Pause detection (e.g., during demo audio playback)
     */
    fun pauseDetection() {
        isPaused = true
        Log.d(TAG, "⏸️ Detection paused (e.g., for demo audio playback)")
    }
    
    /**
     * Resume detection after pausing
     */
    fun resumeDetection() {
        isPaused = false
        Log.d(TAG, "▶️ Detection resumed")
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}
