package com.example.fyp_25_s4_23.entity.ml

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.content.Context
import android.util.Log
import android.content.res.AssetFileDescriptor
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.File
import kotlin.math.*
import org.jtransforms.fft.FloatFFT_1D

/**
 * Preprocesses audio to match training:
 * 16 kHz mono, simple VAD, pad/crop to 3s, mel spectrogram (n_fft=1024, hop=256, n_mels=64),
 * log-dB, then mean/std normalization.
 */
class AudioPreprocessor(private val context: Context) {
    private val sampleRate = 16_000
    private val clipSeconds = 3
    private val targetLen = sampleRate * clipSeconds
    private val nFft = 1024
    private val hop = 256
    private val nMels = 64
    private val fMin = 0f
    private val fMax = sampleRate / 2f

    fun loadWavFromAssets(assetName: String): FloatArray? =
        runCatching { context.assets.open(assetName).use { decodeWav16Mono(it) } }
            .onFailure { Log.e(TAG, "Failed to load wav asset $assetName", it) }
            .getOrNull()

    /** Decode common formats (mp3/mp4/m4a/flac) from assets via platform decoder. */
    fun loadAudioFromAsset(assetName: String): FloatArray? =
        runCatching {
            context.assets.openFd(assetName).use { fd -> decodeWithMediaExtractor(assetFd = fd) }
        }.recoverCatching {
            // Asset may be stored compressed; copy to a temp file then decode from file path.
            val temp = File.createTempFile("asset_audio_", assetName.substringAfterLast('.', ".tmp"), context.cacheDir)
            context.assets.open(assetName).use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            decodeWithMediaExtractor(filePath = temp.absolutePath)
        }.onFailure { Log.e(TAG, "Failed to load audio asset $assetName", it) }
            .getOrNull()

    fun loadWavFromStream(stream: InputStream): FloatArray? =
        runCatching { decodeWav16Mono(stream) }
            .onFailure { Log.e(TAG, "Failed to load wav stream", it) }
            .getOrNull()

    /** Decode common formats (mp3/mp4/m4a/flac) via platform decoder. */
    fun loadAudioFromUri(uri: Uri): FloatArray? =
        runCatching { decodeWithMediaExtractor(uri = uri) }
            .onFailure { Log.e(TAG, "Failed to decode uri=$uri", it) }
            .getOrNull()

    fun preprocess(wav: FloatArray): Array<FloatArray> {
        val trimmed = simpleVad(wav)
        val fixed = padOrCrop(trimmed, targetLen)
        val mel = melSpectrogram(fixed)
        val db = amplitudeToDb(mel)
        return normalize(db)
    }

    // WAV decode (16-bit PCM)
    private fun decodeWav16Mono(input: InputStream): FloatArray {
        val bis = BufferedInputStream(input)
        val header = ByteArray(44)
        if (bis.read(header) != 44) error("Invalid WAV header")
        val channels = toShortLE(header, 22).toInt()
        val bitsPerSample = toShortLE(header, 34).toInt()
        val sr = toIntLE(header, 24)
        require(bitsPerSample == 16) { "Only 16-bit PCM supported" }
        val raw = bis.readBytes()
        val samples = raw.size / 2 / max(1, channels)
        val out = FloatArray(samples)
        var idx = 0
        var i = 0
        while (i < raw.size) {
            var acc = 0f
            repeat(channels) { ch ->
                val lo = raw[i + ch * 2].toInt() and 0xFF
                val hi = raw[i + ch * 2 + 1].toInt()
                acc += ((hi shl 8) or lo).toShort() / 32768f
            }
            out[idx++] = acc / channels
            i += channels * 2
        }
        return if (sr != sampleRate) resampleLinear(out, sr, sampleRate) else out
    }

    private fun decodeWithMediaExtractor(uri: Uri? = null, assetFd: AssetFileDescriptor? = null, filePath: String? = null): FloatArray {
        require(uri != null || assetFd != null || filePath != null) { "Provide either uri, assetFd, or filePath" }
        val extractor = MediaExtractor()
        when {
            assetFd != null -> extractor.setDataSource(assetFd.fileDescriptor, assetFd.startOffset, assetFd.length)
            uri != null -> extractor.setDataSource(context, uri, null)
            filePath != null -> extractor.setDataSource(filePath)
        }
        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { trackIndex = i; break }
        }
        require(trackIndex >= 0) { "No audio track found" }
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val inputBuffers = decoder.inputBuffers
        val outputBuffers = decoder.outputBuffers
        val info = MediaCodec.BufferInfo()
        val pcm = ArrayList<Float>(targetLen * 2)
        var sawInputEOS = false
        var sawOutputEOS = false
        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = inputBuffers[inIndex]
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuf = outputBuffers[outIndex]
                        if (info.size > 0) {
                            val chunk = ByteArray(info.size)
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.get(chunk)
                            val floats = shortsToMonoFloats(chunk, channels)
                            pcm.addAll(floats.toList())
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> decoder.outputBuffers
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                }
            }
        } finally {
            decoder.stop()
            decoder.release()
            extractor.release()
        }

        val mono = pcm.toFloatArray()
        return if (srcSampleRate != sampleRate) resampleLinear(mono, srcSampleRate, sampleRate) else mono
    }

    // DSP helpers
    private fun simpleVad(wav: FloatArray, thresholdDb: Float = -40f): FloatArray {
        val energyDb = wav.map { 20f * ln(max(abs(it), 1e-5f)) / ln(10f) }
        val keep = energyDb.map { it > thresholdDb }
        val first = keep.indexOfFirst { it }
        val last = keep.indexOfLast { it }
        if (first == -1 || last == -1) return wav
        return wav.copyOfRange(first, last + 1)
    }

    private fun padOrCrop(wav: FloatArray, target: Int): FloatArray =
        when {
            wav.size == target -> wav
            wav.size < target -> wav + FloatArray(target - wav.size)
            else -> wav.copyOfRange(0, target)
        }

    private fun melSpectrogram(wav: FloatArray): Array<FloatArray> {
        val window = hannWindow(nFft)
        val fft = FloatFFT_1D(nFft.toLong())
        val numFrames = 1 + (wav.size - nFft) / hop
        val melFilter = melFilterBank()
        val melSpec = Array(nMels) { FloatArray(numFrames) }
        val buffer = FloatArray(nFft * 2)
        for (frame in 0 until numFrames) {
            val start = frame * hop
            for (i in 0 until nFft) {
                val sample = if (start + i < wav.size) wav[start + i] * window[i] else 0f
                buffer[2 * i] = sample
                buffer[2 * i + 1] = 0f
            }
            fft.complexForward(buffer)
            val mag = FloatArray(nFft / 2 + 1)
            for (k in 0 until mag.size) {
                val re = buffer[2 * k]
                val im = buffer[2 * k + 1]
                mag[k] = re * re + im * im
            }
            for (m in 0 until nMels) {
                var acc = 0f
                for (k in mag.indices) {
                    acc += melFilter[m][k] * mag[k]
                }
                melSpec[m][frame] = acc
            }
        }
        return melSpec
    }

    private fun amplitudeToDb(mel: Array<FloatArray>): Array<FloatArray> {
        val out = Array(mel.size) { FloatArray(mel[0].size) }
        for (m in mel.indices) {
            for (t in mel[m].indices) {
                val v = mel[m][t]
                out[m][t] = 10f * log10(max(v, 1e-9f))
            }
        }
        return out
    }

    private fun normalize(melDb: Array<FloatArray>): Array<FloatArray> {
        var sum = 0f
        var sumSq = 0f
        val count = melDb.size * melDb[0].size
        for (m in melDb.indices) {
            for (t in melDb[m].indices) {
                val v = melDb[m][t]
                sum += v
                sumSq += v * v
            }
        }
        val mean = sum / count
        val std = sqrt(max(sumSq / count - mean * mean, 1e-5f))
        val out = Array(melDb.size) { FloatArray(melDb[0].size) }
        for (m in melDb.indices) {
            for (t in melDb[m].indices) {
                out[m][t] = (melDb[m][t] - mean) / std
            }
        }
        return out
    }

    private fun hannWindow(size: Int): FloatArray =
        FloatArray(size) { i -> (0.5f - 0.5f * cos(2.0 * Math.PI * i / size)).toFloat() }

    private fun melFilterBank(): Array<FloatArray> {
        val nFftBins = nFft / 2 + 1
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val mels = FloatArray(nMels + 2) { i -> (melMin + (melMax - melMin) * i / (nMels + 1)) }
        val hz = FloatArray(nMels + 2) { melToHz(mels[it]) }
        val bins = IntArray(nMels + 2) { floor((nFft + 1) * hz[it] / sampleRate).toInt() }
        val filter = Array(nMels) { FloatArray(nFftBins) }
        for (m in 1..nMels) {
            val fMMinus = bins[m - 1]
            val fM = bins[m]
            val fMPlus = bins[m + 1]
            for (k in fMMinus until fM) if (k in filter[0].indices) {
                filter[m - 1][k] = (k - fMMinus).toFloat() / max(1, fM - fMMinus)
            }
            for (k in fM until fMPlus) if (k in filter[0].indices) {
                filter[m - 1][k] = (fMPlus - k).toFloat() / max(1, fMPlus - fM)
            }
        }
        return filter
    }

    private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float): Float = 700f * (10f.pow(mel / 2595f) - 1f)

    private fun resampleLinear(samples: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate) return samples
        val ratio = dstRate.toDouble() / srcRate
        val outLen = (samples.size * ratio).toInt()
        val out = FloatArray(outLen)
        for (i in out.indices) {
            val srcPos = i / ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val s0 = samples.getOrElse(idx) { 0f }
            val s1 = samples.getOrElse(idx + 1) { 0f }
            out[i] = (s0 * (1 - frac) + s1 * frac).toFloat()
        }
        return out
    }

    private fun shortsToMonoFloats(bytes: ByteArray, channels: Int): FloatArray {
        val samples = bytes.size / 2 / max(1, channels)
        val out = FloatArray(samples)
        var idx = 0
        var i = 0
        while (i < bytes.size) {
            var acc = 0f
            repeat(channels) { ch ->
                val lo = bytes[i + ch * 2].toInt() and 0xFF
                val hi = bytes[i + ch * 2 + 1].toInt()
                acc += ((hi shl 8) or lo).toShort() / 32768f
            }
            out[idx++] = acc / channels
            i += channels * 2
        }
        return out
    }

    private fun toShortLE(buf: ByteArray, offset: Int): Short {
        val lo = buf[offset].toInt() and 0xFF
        val hi = buf[offset + 1].toInt()
        return ((hi shl 8) or lo).toShort()
    }

    private fun toIntLE(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            (buf[offset + 3].toInt() shl 24)

    companion object { private const val TAG = "AudioPreprocessor" }
}
