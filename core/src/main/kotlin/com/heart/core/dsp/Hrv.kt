package com.heart.core.dsp

import com.heart.core.model.AutonomicBalance
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Time-domain HRV from a list of inter-beat intervals (ms).
 * Only short-term metrics that are defensible from a 60–120 s camera-PPG window.
 * Per 4-way review: LF/HF is intentionally omitted (needs 5+ min and is contested).
 */
object Hrv {

    data class Metrics(
        val meanIbiMs: Double,
        val rmssdMs: Double,
        val sdnnMs: Double,
        val pnn50: Double,
        val bpmFromIbi: Double,
    )

    /** Keep only physiologically plausible intervals (30–200 bpm). Preserves true
     *  beat-to-beat irregularity (used for rhythm screening). */
    fun plausibleIbis(ibisMs: DoubleArray): DoubleArray =
        ibisMs.filter { it in 300.0..2000.0 }.toDoubleArray()

    /**
     * Plausibility filter + artifact rejection: drops beats differing from the running
     * median by > [tol] (ectopic / motion artifact). Use for HR/HRV, NOT rhythm screening
     * (this step would mask the irregularity we screen for).
     */
    fun cleanIbis(ibisMs: DoubleArray, tol: Double = 0.3): DoubleArray {
        val plausible = plausibleIbis(ibisMs)
        if (plausible.size < 2) return plausible
        val sorted = plausible.sorted()
        val median = sorted[sorted.size / 2]
        return plausible.filter { abs(it - median) <= tol * median }.toDoubleArray()
    }

    fun metrics(ibisMs: DoubleArray): Metrics {
        if (ibisMs.isEmpty()) return Metrics(0.0, 0.0, 0.0, 0.0, 0.0)
        val mean = ibisMs.average()

        var sumSq = 0.0
        for (v in ibisMs) sumSq += (v - mean) * (v - mean)
        val sdnn = sqrt(sumSq / ibisMs.size)

        var diffSq = 0.0
        var nn50 = 0
        for (i in 1 until ibisMs.size) {
            val d = ibisMs[i] - ibisMs[i - 1]
            diffSq += d * d
            if (abs(d) > 50.0) nn50++
        }
        val rmssd = if (ibisMs.size >= 2) sqrt(diffSq / (ibisMs.size - 1)) else 0.0
        val pnn50 = if (ibisMs.size >= 2) nn50.toDouble() / (ibisMs.size - 1) else 0.0
        val bpm = if (mean > 0) 60000.0 / mean else 0.0

        return Metrics(mean, rmssd, sdnn, pnn50, bpm)
    }

    /**
     * Map short-term RMSSD to a 3-level autonomic band (traffic light).
     * Thresholds are coarse and relative — the app shows the light, never the number
     * as a "stress score". Higher RMSSD ⇒ more parasympathetic ⇒ BALANCED.
     */
    fun autonomicBalance(rmssdMs: Double, beats: Int): AutonomicBalance {
        if (beats < 20 || rmssdMs <= 0.0) return AutonomicBalance.UNKNOWN
        return when {
            rmssdMs >= 45.0 -> AutonomicBalance.BALANCED
            rmssdMs >= 20.0 -> AutonomicBalance.MODERATE
            else -> AutonomicBalance.ELEVATED
        }
    }

    /**
     * Irregular-rhythm screening hint (NOT a diagnosis). Flags when the coefficient
     * of variation of cleaned IBIs is high and there are many large successive jumps,
     * a crude AFib-screening surrogate. Requires user to confirm with a clinician.
     */
    fun irregularRhythm(ibisMs: DoubleArray): Boolean {
        if (ibisMs.size < 10) return false
        val mean = ibisMs.average()
        if (mean <= 0) return false
        var sumSq = 0.0
        for (v in ibisMs) sumSq += (v - mean) * (v - mean)
        val cv = sqrt(sumSq / ibisMs.size) / mean
        var bigJumps = 0
        for (i in 1 until ibisMs.size) {
            if (abs(ibisMs[i] - ibisMs[i - 1]) / mean > 0.20) bigJumps++
        }
        val jumpFrac = bigJumps.toDouble() / (ibisMs.size - 1)
        // Normal sinus rhythm has CV ≈ 0.03–0.10; sustained CV > 0.15 with many large
        // successive jumps is the crude AFib-screening surrogate.
        return cv > 0.15 && jumpFrac > 0.25
    }
}
