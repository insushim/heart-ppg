package com.heart.core.dsp

import kotlin.math.abs

/**
 * Robust heart-rate estimation over sliding windows (4-way review: top accuracy win).
 *
 * The band-passed signal is split into overlapping windows; each window yields a spectral
 * HR + SNR. Motion/contact-corrupted windows have low SNR or outlier rates and are dropped;
 * the surviving windows are combined by an SNR-weighted, outlier-trimmed average.
 *
 * This gives Welch-like variance reduction (averaging across windows) while keeping good
 * frequency resolution (each window stays long + the spectral step zero-pads), so it does
 * not suffer the short-window resolution loss of naive Welch.
 */
object WindowedHr {

    private const val WINDOW_SEC = 12.0
    private const val STEP_SEC = 3.0
    private const val MIN_WINDOW_SEC = 6.0

    data class Result(val bpm: Double, val snrDb: Double, val windows: Int, val accepted: Int)

    fun estimate(band: DoubleArray, fs: Double): Result {
        val n = band.size
        val winLen = (fs * WINDOW_SEC).toInt()
        // Too short to window — fall back to a single estimate over the whole signal.
        if (fs <= 0 || winLen < (fs * MIN_WINDOW_SEC).toInt() || n < winLen) {
            val e = Spectral.estimateHr(band, fs)
            return Result(e.bpm, e.snrDb, 1, if (e.bpm in 40.0..220.0) 1 else 0)
        }

        val step = (fs * STEP_SEC).toInt().coerceAtLeast(1)
        val ests = ArrayList<Spectral.HrEstimate>()
        var start = 0
        while (start + winLen <= n) {
            val e = Spectral.estimateHr(band.copyOfRange(start, start + winLen), fs)
            if (e.bpm in 40.0..220.0) ests.add(e)
            start += step
        }
        if (ests.isEmpty()) {
            val e = Spectral.estimateHr(band, fs)
            return Result(e.bpm, e.snrDb, 0, 0)
        }

        // Drop low-SNR windows (motion/contact artifacts) but never drop everything.
        val sortedSnr = ests.map { it.snrDb }.sorted()
        val medianSnr = sortedSnr[sortedSnr.size / 2]
        val snrGate = minOf(3.0, medianSnr)
        val decent = ests.filter { it.snrDb >= snrGate }
        val pool = if (decent.size >= 2) decent else ests

        // Outlier-trim around the median rate, then SNR-weighted average.
        val sortedBpm = pool.map { it.bpm }.sorted()
        val medianBpm = sortedBpm[sortedBpm.size / 2]
        val inliers = pool.filter { abs(it.bpm - medianBpm) <= 8.0 }
        val use = if (inliers.isNotEmpty()) inliers else pool

        var weightSum = 0.0
        var bpmSum = 0.0
        var snrSum = 0.0
        for (e in use) {
            val w = maxOf(e.snrDb, 0.1)
            weightSum += w
            bpmSum += w * e.bpm
            snrSum += e.snrDb
        }
        val bpm = bpmSum / weightSum
        return Result(bpm, snrSum / use.size, ests.size, use.size)
    }
}
