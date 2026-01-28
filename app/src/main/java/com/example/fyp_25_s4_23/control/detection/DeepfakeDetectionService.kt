package com.example.fyp_25_s4_23.control.detection

import android.content.Context
import android.util.Log
import com.example.fyp_25_s4_23.entity.ml.ModelRunner
import com.example.fyp_25_s4_23.entity.data.dao.DetectionResultDao
import com.example.fyp_25_s4_23.entity.data.entities.DetectionResultEntity
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
    private val detectionDao: DetectionResultDao? = null,
    private val detectionThreshold: Float = 0.7f 
) {
    private val TAG = "DeepfakeDetection"
    
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
    
    // Background processing
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null
    private var isRunning = false
    
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
        
        // Limit buffer size to prevent memory issues (need 15 chunks minimum for 48k samples)
        while (audioBufferQueue.size > 20) {
            audioBufferQueue.poll()
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
        val estimatedSamples = queueSize * 3200  // Each chunk is 3200 samples
        
        if (estimatedSamples < targetSamples) {
            // Not enough data yet - DON'T DRAIN THE QUEUE
            Log.d("DEEPFAKE_DETECT", "⏳ Buffering audio... $estimatedSamples/$targetSamples samples (need ${targetSamples - estimatedSamples} more), queue has $queueSize chunks")
            return
        }
        
        // We have enough! Now drain the required chunks
        Log.d("DEEPFAKE_DETECT", "✅ Queue has enough samples! Draining ${queueSize} chunks for inference...")
        
        val samples = mutableListOf<Float>()
        val chunksNeeded = (targetSamples + 3199) / 3200  // Round up: 48000/3200 = 15 chunks
        
        repeat(chunksNeeded.coerceAtMost(audioBufferQueue.size)) {
            audioBufferQueue.poll()?.let { chunk ->
                samples.addAll(chunk.toList())
            }
        }
        
        // Take exactly targetSamples (in case we got slightly more)
        val audioSegment = samples.take(targetSamples).toFloatArray()
        
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
            Log.d("DEEPFAKE_DETECT", "🔄 Preprocessing audio to mel spectrogram...")
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
            
            Log.i("DEEPFAKE_DETECT", "📊 Detection result: score=${"%.3f".format(score)}, isDeepfake=$isDeepfake (threshold=$detectionThreshold)")
            
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
            
            // Save to database
            detectionDao?.let { dao ->
                val entity = DetectionResultEntity(
                    id = "${callId}_${timestamp}",  // Unique ID: callId + timestamp
                    callId = callId,
                    probability = score,
                    isDeepfake = isDeepfake,
                    timestampSeconds = timestamp / 1000,
                    modelVersion = "melcnn-0.0.1"
                )
                dao.insert(entity)
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
     * Cleanup resources
     */
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}
