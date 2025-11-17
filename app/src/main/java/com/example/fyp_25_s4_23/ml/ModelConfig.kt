package com.example.fyp_25_s4_23.ml

data class ModelConfig(
    val modelAssetPath: String = "model/model.tflite",
    val inputLength: Int = 16000,
    val threshold: Float = 0.7f,
    val version: String = "0.0.1"
)

