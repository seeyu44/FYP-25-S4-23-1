package com.example.fyp_25_s4_23.entity.ml

import kotlin.math.abs
import kotlin.math.log10

object AudioFeatureExtractor {
    fun energy(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        var sum = 0.0
        frame.forEach { sample ->
            val norm = sample / 32768.0
            sum += norm * norm
        }
        val mean = sum / frame.size
        return log10(mean.coerceAtLeast(1e-8)).toFloat() * -10f
    }

    fun zeroCrossRate(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        var count = 0
        for (i in 1 until frame.size) {
            if (frame[i - 1] >= 0 && frame[i] < 0 || frame[i - 1] < 0 && frame[i] >= 0) {
                count++
            }
        }
        return count.toFloat() / frame.size
    }
}

