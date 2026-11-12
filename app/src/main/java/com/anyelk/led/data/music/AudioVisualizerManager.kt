package com.anyelk.led.data.music

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max

data class VisualizerState(
    val active: Boolean = false,
    /** Smoothed band energies in 0..1. */
    val bass: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
    /** Final smoothed RGB sent to the strip. */
    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0
)

/**
 * Reads FFT frames from the Android [Visualizer] attached to the player's
 * audio session, splits the spectrum into bass / mids / highs, maps them to
 * red / blue / green and smooths everything so colors ride the beat instead
 * of flickering chaotically.
 *
 * Smoothing pipeline:
 *  1. adaptive per-band peak normalization (auto gain, floor against noise);
 *  2. asymmetric EMA — fast attack, slow release;
 *  3. second-stage EMA on the final RGB channels.
 */
class AudioVisualizerManager {

    private val _state = MutableStateFlow(VisualizerState())
    val state: StateFlow<VisualizerState> = _state.asStateFlow()

    private var visualizer: Visualizer? = null
    private var attachedSession: Int = -1

    // Per-band smoothed levels (bass, mid, high) and their rolling peaks.
    private val smooth = FloatArray(3)
    private val peak = floatArrayOf(PEAK_FLOOR, PEAK_FLOOR, PEAK_FLOOR)

    // Final RGB smoothing.
    private var outR = 0f
    private var outG = 0f
    private var outB = 0f

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer,
            waveform: ByteArray,
            samplingRate: Int
        ) = Unit

        override fun onFftDataCapture(
            visualizer: Visualizer,
            fft: ByteArray,
            samplingRate: Int
        ) {
            val sampleRateHz = normalizeSampleRate(samplingRate)
            processFft(fft, sampleRateHz)
        }
    }

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (attachedSession == audioSessionId && visualizer != null) return
        detachInternal()

        try {
            val v = Visualizer(audioSessionId)
            v.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = range[1] // prefer the finest FFT resolution
            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            v.setDataCaptureListener(
                listener,
                Visualizer.getMaxCaptureRate(),
                false,
                true
            )
            v.enabled = true
            visualizer = v
            attachedSession = audioSessionId
            resetSmoothing()
            _state.value = _state.value.copy(active = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach Visualizer to session $audioSessionId", e)
            detachInternal()
            _state.value = _state.value.copy(active = false)
        }
    }

    @Synchronized
    fun detach() {
        detachInternal()
        _state.value = VisualizerState(active = false)
    }

    private fun detachInternal() {
        val v = visualizer ?: return
        try {
            v.enabled = false
            v.setDataCaptureListener(null, 0, false, false)
            v.release()
        } catch (_: Exception) {
        }
        visualizer = null
        attachedSession = -1
    }

    private fun resetSmoothing() {
        smooth.fill(0f)
        peak.fill(PEAK_FLOOR)
        outR = 0f
        outG = 0f
        outB = 0f
    }

    private fun processFft(fft: ByteArray, sampleRateHz: Int) {
        val size = fft.size
        if (size < 8) return

        // Android FFT layout: [0] = DC, [1] = Nyquist, then (real, imag) pairs.
        val binCount = size / 2
        val binWidthHz = sampleRateHz.toFloat() / size

        val bassRange = binsFor(20f, 250f, binCount, binWidthHz)
        val midRange = binsFor(250f, 2000f, binCount, binWidthHz)
        val highRange = binsFor(2000f, minOf(sampleRateHz / 2f, 18000f), binCount, binWidthHz)

        val rawBass = bandLevel(fft, bassRange.first, bassRange.second)
        val rawMid = bandLevel(fft, midRange.first, midRange.second)
        val rawHigh = bandLevel(fft, highRange.first, highRange.second)

        // 1 + 2: adaptive normalization + asymmetric EMA per band.
        smooth[0] = ema(normalize(rawBass, 0), smooth[0])
        smooth[1] = ema(normalize(rawMid, 1), smooth[1])
        smooth[2] = ema(normalize(rawHigh, 2), smooth[2])

        // 3: map bands to RGB and smooth the result.
        val targetR = smooth[0] * 255f
        val targetG = smooth[2] * 255f
        val targetB = smooth[1] * 255f

        outR += (targetR - outR) * RGB_ATTACK
        outG += (targetG - outG) * RGB_ATTACK
        outB += (targetB - outB) * RGB_ATTACK

        _state.value = VisualizerState(
            active = true,
            bass = smooth[0],
            mid = smooth[1],
            high = smooth[2],
            red = outR.toInt().coerceIn(0, 255),
            green = outG.toInt().coerceIn(0, 255),
            blue = outB.toInt().coerceIn(0, 255)
        )
    }

    private fun normalize(raw: Float, band: Int): Float {
        // Rolling peak keeps the colors punchy regardless of playback volume.
        val p = max(peak[band] * PEAK_DECAY, raw)
        peak[band] = max(p, PEAK_FLOOR)
        return (raw / peak[band]).coerceIn(0f, 1f)
    }

    private fun ema(target: Float, current: Float): Float {
        val alpha = if (target > current) ATTACK else RELEASE
        return current + (target - current) * alpha
    }

    /** Average magnitude of bins [from, to] over the signed byte FFT data. */
    private fun bandLevel(fft: ByteArray, from: Int, to: Int): Float {
        if (to < from) return 0f
        var sum = 0f
        var count = 0
        for (k in from..to) {
            val realIdx = 2 * k
            if (realIdx + 1 >= fft.size) break
            val re = fft[realIdx].toInt()
            val im = fft[realIdx + 1].toInt()
            sum += hypot(re.toFloat(), im.toFloat())
            count++
        }
        if (count == 0) return 0f
        // Raw magnitudes are ~0..181 with SCALING_MODE_NORMALIZED.
        return (sum / count) / 128f
    }

    private fun binsFor(
        lowHz: Float,
        highHz: Float,
        binCount: Int,
        binWidthHz: Float
    ): Pair<Int, Int> {
        if (binWidthHz <= 0f) return 1 to 0
        val from = max(1, Math.ceil(lowHz / binWidthHz.toDouble()).toInt())
        val to = (binCount - 1).coerceAtMost(Math.floor(highHz / binWidthHz.toDouble()).toInt())
        return from to to
    }

    private fun normalizeSampleRate(rate: Int): Int = when {
        rate > 1_000_000 -> rate / 1000 // milliHertz -> Hz
        rate <= 0 -> 44_100
        else -> rate
    }

    companion object {
        private const val TAG = "AudioVisualizer"
        private const val ATTACK = 0.45f
        private const val RELEASE = 0.14f
        private const val RGB_ATTACK = 0.35f
        private const val PEAK_DECAY = 0.995f
        private const val PEAK_FLOOR = 0.12f
    }
}
