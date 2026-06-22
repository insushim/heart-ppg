package com.heart.core.dsp

import kotlin.math.abs

/**
 * Perfusion Index (PI) = pulsatile (AC) / non-pulsatile (DC) component, as a percentage.
 * Computed on the RAW (un-detrended) intensity so the DC term is meaningful.
 * Used as a contact-quality / peripheral-flow proxy, not a clinical number.
 */
object Perfusion {

    fun perfusionIndex(rawValues: DoubleArray, acComponent: DoubleArray): Double {
        if (rawValues.isEmpty()) return 0.0
        val dc = rawValues.average()
        if (abs(dc) < 1e-9) return 0.0
        // AC magnitude: peak-to-peak of the band-passed component, robust to outliers
        // via 5th/95th percentiles.
        if (acComponent.isEmpty()) return 0.0
        val sorted = acComponent.sorted()
        val lo = sorted[(sorted.size * 0.05).toInt().coerceIn(0, sorted.size - 1)]
        val hi = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)]
        val ac = hi - lo
        return (ac / abs(dc)) * 100.0
    }
}
