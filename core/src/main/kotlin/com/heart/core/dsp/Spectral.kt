package com.heart.core.dsp

import kotlin.math.log10
import kotlin.math.max

/**
 * Spectral heart-rate estimation with a parabolic-interpolated peak and an SNR estimate.
 */
object Spectral {

    data class HrEstimate(val bpm: Double, val snrDb: Double)

    /**
     * @param bandPassed zero-mean band-passed signal (uniform fs)
     * @param fs sampling rate (Hz)
     * @param lowHz / highHz cardiac search band
     */
    fun estimateHr(
        bandPassed: DoubleArray,
        fs: Double,
        lowHz: Double = 0.7,
        highHz: Double = 4.0,
    ): HrEstimate {
        if (bandPassed.size < 8 || fs <= 0) return HrEstimate(0.0, 0.0)

        val windowed = Filters.hann(bandPassed)
        // Zero-pad ×4 for finer frequency resolution.
        val fftLen = Fft.nextPow2(bandPassed.size) * 4
        val mag = Fft.magnitudeSpectrum(windowed, fftLen)
        val binHz = fs / fftLen

        val loBin = max(1, (lowHz / binHz).toInt())
        val hiBin = (highHz / binHz).toInt().coerceAtMost(mag.size - 1)
        if (hiBin <= loBin) return HrEstimate(0.0, 0.0)

        // Dominant bin in the cardiac band.
        var peakBin = loBin
        var peakVal = mag[loBin]
        for (b in loBin..hiBin) {
            if (mag[b] > peakVal) { peakVal = mag[b]; peakBin = b }
        }

        // Parabolic interpolation around the peak for sub-bin frequency accuracy.
        // Only interpolate when both neighbours lie inside the search band, else a
        // boundary bin would reference baseline energy just outside the band.
        val refinedBin = if (peakBin in (loBin + 1) until hiBin) {
            val a = mag[peakBin - 1]
            val b = mag[peakBin]
            val c = mag[peakBin + 1]
            val denom = (a - 2 * b + c)
            val delta = if (denom != 0.0) 0.5 * (a - c) / denom else 0.0
            peakBin + delta.coerceIn(-0.5, 0.5)
        } else peakBin.toDouble()

        val freq = refinedBin * binHz
        val bpm = freq * 60.0

        // SNR: power at the cardiac fundamental AND its harmonics (these are signal,
        // not noise — a flat SNR that counts harmonics as noise wrongly penalises low
        // heart rates whose harmonics fall inside the band) vs the residual band power.
        val halfWidthBins = (0.12 / binHz).toInt().coerceIn(1, mag.size)
        val isSignal = BooleanArray(mag.size)
        if (refinedBin >= 0.5) {
            var harmonic = 1
            while (harmonic <= 12) {
                val hBin = Math.round(refinedBin * harmonic).toInt()
                if (hBin > hiBin) break
                for (b in (hBin - halfWidthBins)..(hBin + halfWidthBins)) {
                    if (b in loBin..hiBin) isSignal[b] = true
                }
                harmonic++
            }
        }
        var signalPow = 0.0
        var noisePow = 0.0
        for (b in loBin..hiBin) {
            val p = mag[b] * mag[b]
            if (isSignal[b]) signalPow += p else noisePow += p
        }
        val snrDb = if (noisePow > 1e-12) 10.0 * log10(signalPow / noisePow) else 60.0

        return HrEstimate(bpm, snrDb)
    }
}
