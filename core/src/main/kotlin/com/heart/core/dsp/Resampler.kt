package com.heart.core.dsp

import com.heart.core.model.PpgSample

/**
 * Camera frames arrive at irregular intervals. We resample the PPG stream onto a
 * uniform grid (linear interpolation) before spectral analysis.
 */
object Resampler {

    data class Uniform(val fs: Double, val values: DoubleArray, val startTimeMs: Long) {
        override fun equals(other: Any?): Boolean =
            other is Uniform && fs == other.fs && startTimeMs == other.startTimeMs &&
                values.contentEquals(other.values)
        override fun hashCode(): Int =
            (fs.hashCode() * 31 + startTimeMs.hashCode()) * 31 + values.contentHashCode()
    }

    /** Median sampling rate implied by the raw timestamps (Hz). */
    fun estimateFs(samples: List<PpgSample>): Double {
        if (samples.size < 2) return 0.0
        // Sort defensively — callers may pass unordered samples; an unsorted stream
        // would inject negative diffs and bias the median rate (4-way review).
        val sorted = if (isSortedByTime(samples)) samples else samples.sortedBy { it.timeMs }
        val diffs = ArrayList<Double>(sorted.size - 1)
        for (i in 1 until sorted.size) {
            val dt = (sorted[i].timeMs - sorted[i - 1].timeMs).toDouble()
            if (dt > 0) diffs.add(dt)
        }
        if (diffs.isEmpty()) return 0.0
        diffs.sort()
        val medianMs = diffs[diffs.size / 2]
        return if (medianMs > 0) 1000.0 / medianMs else 0.0
    }

    private fun isSortedByTime(samples: List<PpgSample>): Boolean {
        for (i in 1 until samples.size) if (samples[i].timeMs < samples[i - 1].timeMs) return false
        return true
    }

    /** Resample to [targetFs] Hz over the full span of [samples]. */
    fun resample(samples: List<PpgSample>, targetFs: Double): Uniform {
        require(targetFs > 0) { "targetFs must be > 0" }
        if (samples.size < 2) {
            return Uniform(targetFs, DoubleArray(0), samples.firstOrNull()?.timeMs ?: 0L)
        }
        val sorted = samples.sortedBy { it.timeMs }
        val t0 = sorted.first().timeMs
        val tEnd = sorted.last().timeMs
        val durSec = (tEnd - t0) / 1000.0
        val n = (durSec * targetFs).toInt() + 1
        if (n <= 0) return Uniform(targetFs, DoubleArray(0), t0)
        val out = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val tMs = t0 + (i / targetFs) * 1000.0
            while (j < sorted.size - 2 && sorted[j + 1].timeMs < tMs) j++
            val a = sorted[j]
            val b = sorted[minOf(j + 1, sorted.size - 1)]
            out[i] = if (b.timeMs == a.timeMs) {
                a.value
            } else {
                val frac = (tMs - a.timeMs) / (b.timeMs - a.timeMs)
                a.value + frac.coerceIn(0.0, 1.0) * (b.value - a.value)
            }
        }
        return Uniform(targetFs, out, t0)
    }
}
