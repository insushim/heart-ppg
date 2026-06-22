package com.heart.core.dsp

/**
 * Respiration rate from respiratory sinus arrhythmia (RSA): breathing modulates the
 * beat-to-beat interval series (the "tachogram"). We resample the tachogram, band-pass
 * the respiratory band (0.1–0.5 Hz = 6–30 bpm) and take the spectral peak.
 *
 * Per 4-way review this is only valid at rest, in silence, without motion; callers
 * must gate on quality and may discard the result.
 */
object Respiration {

    /** Returns breaths-per-minute, or null when not derivable / not confident. */
    fun estimate(ibisMs: DoubleArray): Double? {
        if (ibisMs.size < 12) return null // need enough beats to see a respiratory cycle

        // Build the tachogram time base (cumulative beat times in seconds).
        val times = DoubleArray(ibisMs.size)
        var acc = 0.0
        for (i in ibisMs.indices) {
            acc += ibisMs[i] / 1000.0
            times[i] = acc
        }
        val duration = times.last()
        if (duration < 20.0) return null // < 20 s of beats: unreliable

        // Resample tachogram to a uniform 4 Hz grid.
        val fs = 4.0
        val n = (duration * fs).toInt()
        if (n < 16) return null
        val grid = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val t = i / fs
            // Advance so that t falls in [times[j], times[j+1]]; tachogram value at
            // beat k is ibisMs[k] sampled at times[k] — keep time and value indices aligned.
            while (j < ibisMs.size - 2 && times[j + 1] < t) j++
            val t0 = times[j]
            val t1 = times[j + 1]
            val v0 = ibisMs[j]
            val v1 = ibisMs[j + 1]
            grid[i] = if (t1 <= t0) v0 else {
                val frac = ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
                v0 + frac * (v1 - v0)
            }
        }

        val band = Filters.bandPass(Filters.detrend(grid, n), fs, low = 0.1, high = 0.5)
        val est = Spectral.estimateHr(band, fs, lowHz = 0.1, highHz = 0.5)
        val bpm = est.bpm
        // Confidence gate: require a clear spectral peak and a plausible rate.
        if (bpm < 6.0 || bpm > 30.0) return null
        // Require the respiratory peak to stand clear of the band noise floor.
        if (est.snrDb < 2.0) return null
        return bpm
    }
}
