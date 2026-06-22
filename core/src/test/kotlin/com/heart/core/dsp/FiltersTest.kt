package com.heart.core.dsp

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class FiltersTest {

    private fun rms(x: DoubleArray): Double {
        var s = 0.0; for (v in x) s += v * v; return Math.sqrt(s / x.size)
    }

    @Test
    fun bandPass_keepsCardiac_removesBaselineAndHighFreq() {
        val fs = 30.0
        val n = 900 // 30 s
        val cardiac = DoubleArray(n) { sin(2 * PI * 1.2 * it / fs) }            // 72 bpm, in band
        val baseline = DoubleArray(n) { 5.0 * sin(2 * PI * 0.1 * it / fs) }     // wander, below band
        val highFreq = DoubleArray(n) { 2.0 * sin(2 * PI * 8.0 * it / fs) }     // above band
        val mixed = DoubleArray(n) { 150.0 + cardiac[it] + baseline[it] + highFreq[it] }

        val out = Filters.bandPass(Filters.detrend(mixed, 45), fs)

        // After filtering, energy should be dominated by the cardiac component:
        // compare correlation with the pure cardiac signal vs the baseline.
        val corrCardiac = correlation(out, cardiac)
        assertTrue(corrCardiac > 0.9, "cardiac correlation too low: $corrCardiac")
    }

    @Test
    fun detrend_removesDcOffset() {
        val sig = DoubleArray(300) { 100.0 + sin(2 * PI * 1.0 * it / 30.0) }
        val out = Filters.detrend(sig, 45)
        assertTrue(Math.abs(out.average()) < 0.05, "mean after detrend should be ~0: ${out.average()}")
    }

    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) {
            val x = a[i] - ma; val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        return num / (Math.sqrt(da * db) + 1e-12)
    }
}
