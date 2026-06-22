package com.heart.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal IIR biquad filtering for PPG.
 * Uses RBJ cookbook coefficients (Q = 1/sqrt(2) → Butterworth-flat 2nd order)
 * and zero-phase forward–backward application (filtfilt) so beat timing is
 * not shifted — important for accurate IBI / HRV.
 */
object Filters {

    private data class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double,
    )

    private const val Q = 0.70710678 // Butterworth

    private fun lowPass(fs: Double, fc: Double): Biquad {
        val w0 = 2 * PI * fc / fs
        val cosw = cos(w0)
        val alpha = sin(w0) / (2 * Q)
        val a0 = 1 + alpha
        val b0 = (1 - cosw) / 2
        val b1 = 1 - cosw
        val b2 = (1 - cosw) / 2
        val a1 = -2 * cosw
        val a2 = 1 - alpha
        return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun highPass(fs: Double, fc: Double): Biquad {
        val w0 = 2 * PI * fc / fs
        val cosw = cos(w0)
        val alpha = sin(w0) / (2 * Q)
        val a0 = 1 + alpha
        val b0 = (1 + cosw) / 2
        val b1 = -(1 + cosw)
        val b2 = (1 + cosw) / 2
        val a1 = -2 * cosw
        val a2 = 1 - alpha
        return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun applyOnce(x: DoubleArray, bq: Biquad): DoubleArray {
        val y = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val xn = x[i]
            val yn = bq.b0 * xn + bq.b1 * x1 + bq.b2 * x2 - bq.a1 * y1 - bq.a2 * y2
            x2 = x1; x1 = xn; y2 = y1; y1 = yn
            y[i] = yn
        }
        return y
    }

    private fun filtfilt(x: DoubleArray, bq: Biquad): DoubleArray {
        val n = x.size
        if (n < 3) return x.copyOf()
        // Odd (point-symmetric) reflection padding suppresses the start/end transients
        // that zero initial conditions would otherwise inject (4-way review C1/W-2).
        val pad = minOf(n - 1, maxOf(15, n / 4))
        val ext = DoubleArray(n + 2 * pad)
        for (k in 0 until pad) ext[k] = 2 * x[0] - x[pad - k]
        System.arraycopy(x, 0, ext, pad, n)
        for (k in 0 until pad) ext[pad + n + k] = 2 * x[n - 1] - x[n - 2 - k]

        val fwd = applyOnce(ext, bq)
        fwd.reverse()
        val bwd = applyOnce(fwd, bq)
        bwd.reverse()
        return bwd.copyOfRange(pad, pad + n)
    }

    /**
     * Band-pass [low]..[high] Hz via cascaded zero-phase high-pass then low-pass.
     * Default band 0.7–4 Hz covers 42–240 bpm.
     */
    fun bandPass(signal: DoubleArray, fs: Double, low: Double = 0.7, high: Double = 4.0): DoubleArray {
        require(fs > 2 * high) { "sampling rate $fs too low for $high Hz cutoff" }
        val hp = highPass(fs, low)
        val lp = lowPass(fs, high)
        return filtfilt(filtfilt(signal, hp), lp)
    }

    /** Subtract a centered moving average to remove baseline wander / DC. */
    fun detrend(signal: DoubleArray, window: Int): DoubleArray {
        if (signal.isEmpty() || window <= 1) return signal.copyOf()
        val w = window.coerceAtMost(signal.size)
        val half = w / 2
        // Prefix sums for O(n) moving average.
        val prefix = DoubleArray(signal.size + 1)
        for (i in signal.indices) prefix[i + 1] = prefix[i] + signal[i]
        val out = DoubleArray(signal.size)
        for (i in signal.indices) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(signal.size - 1)
            val mean = (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
            out[i] = signal[i] - mean
        }
        return out
    }

    /** Hann window, applied in-place-safe (returns a copy). */
    fun hann(signal: DoubleArray): DoubleArray {
        val n = signal.size
        if (n < 2) return signal.copyOf()
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 * (1 - cos(2 * PI * i / (n - 1)))
            out[i] = signal[i] * w
        }
        return out
    }
}
