package com.heart.core.dsp

import kotlin.math.sqrt

/**
 * Adaptive peak detection on a band-passed (zero-mean) PPG signal.
 * Returns sample indices of systolic peaks.
 */
object PeakDetector {

    /**
     * @param signal zero-phase band-passed signal (zero-mean)
     * @param fs sampling rate (Hz)
     * @param maxBpm reject peaks closer than 60/maxBpm seconds apart (refractory)
     */
    fun detect(signal: DoubleArray, fs: Double, maxBpm: Double = 220.0): IntArray {
        val n = signal.size
        if (n < 3) return IntArray(0)

        val safeMaxBpm = maxBpm.coerceIn(30.0, 400.0)
        val minDist = (fs * 60.0 / safeMaxBpm).toInt().coerceAtLeast(1)

        // Adaptive amplitude threshold: a fraction of the RMS of the positive envelope.
        var sumSq = 0.0
        for (v in signal) sumSq += v * v
        val rms = sqrt(sumSq / n)
        val threshold = 0.3 * rms

        val peaks = ArrayList<Int>()
        var i = 1
        while (i < n - 1) {
            val v = signal[i]
            if (v > threshold && v >= signal[i - 1] && v > signal[i + 1]) {
                if (peaks.isEmpty() || i - peaks.last() >= minDist) {
                    peaks.add(i)
                } else if (v > signal[peaks.last()]) {
                    // Keep the taller of two peaks inside the refractory window.
                    peaks[peaks.size - 1] = i
                }
            }
            i++
        }
        return peaks.toIntArray()
    }

    /** Convert peak indices to inter-beat intervals (ms) given the sampling rate. */
    fun intervalsMs(peaks: IntArray, fs: Double): DoubleArray {
        if (peaks.size < 2) return DoubleArray(0)
        val out = DoubleArray(peaks.size - 1)
        for (i in 1 until peaks.size) {
            out[i - 1] = (peaks[i] - peaks[i - 1]) * 1000.0 / fs
        }
        return out
    }
}
