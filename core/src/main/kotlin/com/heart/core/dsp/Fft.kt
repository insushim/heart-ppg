package com.heart.core.dsp

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iterative radix-2 Cooley–Tukey FFT. Operates on power-of-two lengths.
 * Used for spectral heart-rate estimation; not a general-purpose DSP lib.
 */
object Fft {

    fun nextPow2(n: Int): Int {
        if (n <= 1) return 1
        val maxPow2 = 1 shl 30 // guard against left-shift overflow into negatives
        if (n >= maxPow2) return maxPow2
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /**
     * In-place complex FFT. [re] and [im] must be the same power-of-two length.
     */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size) { "re/im length mismatch" }
        require(n > 0 && (n and (n - 1)) == 0) { "length must be a power of two, was $n" }
        if (n == 1) return

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Danielson–Lanczos.
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe = re[i + k + len / 2]
                    val bIm = im[i + k + len / 2]
                    val tRe = bRe * curRe - bIm * curIm
                    val tIm = bRe * curIm + bIm * curRe
                    re[i + k] = aRe + tRe
                    im[i + k] = aIm + tIm
                    re[i + k + len / 2] = aRe - tRe
                    im[i + k + len / 2] = aIm - tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Magnitude spectrum of a real signal, zero-padded to [fftLen] (power of two). */
    fun magnitudeSpectrum(signal: DoubleArray, fftLen: Int): DoubleArray {
        require((fftLen and (fftLen - 1)) == 0) { "fftLen must be power of two" }
        val re = DoubleArray(fftLen)
        val im = DoubleArray(fftLen)
        val copyLen = minOf(signal.size, fftLen)
        System.arraycopy(signal, 0, re, 0, copyLen)
        transform(re, im)
        val half = fftLen / 2
        val mag = DoubleArray(half)
        for (i in 0 until half) {
            mag[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        }
        return mag
    }

    @Suppress("unused")
    private fun log2(x: Double): Double = ln(x) / ln(2.0)
}
