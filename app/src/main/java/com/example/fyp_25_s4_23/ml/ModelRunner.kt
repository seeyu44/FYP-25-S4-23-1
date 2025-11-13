package com.example.fyp_25_s4_23.ml

import android.content.Context

/**
 * Minimal model runner stub. Replace with TFLite/ONNX runtime integration.
 */
class ModelRunner(private val context: Context, private val config: ModelConfig = ModelConfig()) {
    fun warmUp() {
        // TODO: load model from assets and prepare interpreter
    }

    /**
     * Returns probability [0.0, 1.0] that the segment is deepfake.
     */
    fun infer(features: FloatArray): Float {
        // TODO: perform real inference; this is a placeholder
        return 0.0f
    }
}

