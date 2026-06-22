package com.heart.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FftTest {

    @Test
    fun nextPow2() {
        assertEquals(1, Fft.nextPow2(1))
        assertEquals(8, Fft.nextPow2(5))
        assertEquals(1024, Fft.nextPow2(1000))
        assertEquals(1024, Fft.nextPow2(1024))
    }

    @Test
    fun magnitudeSpectrum_locatesKnownSine() {
        val fs = 64.0
        val fftLen = 1024
        val freq = 5.0 // Hz
        val sig = DoubleArray(fftLen) { sin(2 * PI * freq * it / fs) }
        val mag = Fft.magnitudeSpectrum(sig, fftLen)
        val binHz = fs / fftLen
        var peakBin = 0
        for (b in 1 until mag.size) if (mag[b] > mag[peakBin]) peakBin = b
        val peakFreq = peakBin * binHz
        assertTrue(abs(peakFreq - freq) < binHz * 2, "peak at $peakFreq, expected ~$freq")
    }

    @Test
    fun transform_matchesNaiveDft_smallN() {
        val n = 8
        val re = DoubleArray(n) { it.toDouble() }
        val im = DoubleArray(n)
        val expectedRe = DoubleArray(n)
        val expectedIm = DoubleArray(n)
        for (k in 0 until n) {
            var sr = 0.0; var si = 0.0
            for (t in 0 until n) {
                val ang = -2 * PI * k * t / n
                sr += re[t] * kotlin.math.cos(ang)
                si += re[t] * sin(ang)
            }
            expectedRe[k] = sr; expectedIm[k] = si
        }
        Fft.transform(re, im)
        for (k in 0 until n) {
            assertTrue(abs(re[k] - expectedRe[k]) < 1e-9, "re[$k]")
            assertTrue(abs(im[k] - expectedIm[k]) < 1e-9, "im[$k]")
        }
    }
}
