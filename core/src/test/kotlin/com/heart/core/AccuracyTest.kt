package com.heart.core

import com.heart.core.model.DualPpgSample
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/** Tests for the accuracy improvements: sliding-window robust HR + adaptive channel. */
class AccuracyTest {

    private fun ok(a: PpgAnalyzer.Analysis) = when (a) {
        is PpgAnalyzer.Analysis.Ok -> a.result
        is PpgAnalyzer.Analysis.Insufficient -> fail("expected Ok, got: ${a.reason}")
    }

    @Test
    fun hr_robustToMidMeasurementMotionBurst() {
        // 40 s at 75 bpm, with a heavy motion burst from 16–24 s.
        val beats = PpgSynth.constantBeats(75.0, 40.0)
        val clean = PpgSynth.build(beats, durationSec = 40.0, acAmp = 3.0, noiseStd = 0.3)
        val corrupted = PpgSynth.corrupt(clean, fromSec = 16.0, toSec = 24.0, noiseStd = 18.0)
        val r = ok(PpgAnalyzer.analyze(corrupted))
        // Sliding-window combine should reject the corrupted windows and stay near 75.
        assertTrue(
            abs(r.bpm - 75.0) <= 4.0,
            "with motion burst, BPM=${"%.1f".format(r.bpm)} should still be ~75 (windows=${r.hrWindowsUsed})"
        )
    }

    @Test
    fun dual_picksStrongerGreenChannel() {
        // Green carries a much stronger pulsatile component than red.
        val beats = PpgSynth.constantBeats(68.0, 30.0)
        val dual = PpgSynth.buildDual(
            beats, durationSec = 30.0,
            acRed = 0.6, acGreen = 4.0, noiseStd = 0.4,
        )
        val r = ok(PpgAnalyzer.analyzeDual(dual))
        assertTrue(r.channel == "GREEN", "expected GREEN channel chosen, got ${r.channel}")
        assertTrue(abs(r.bpm - 68.0) <= 3.0, "BPM=${"%.1f".format(r.bpm)} should be ~68")
    }

    @Test
    fun dual_picksStrongerRedChannel() {
        val beats = PpgSynth.constantBeats(82.0, 30.0)
        val dual = PpgSynth.buildDual(
            beats, durationSec = 30.0,
            acRed = 4.0, acGreen = 0.6, noiseStd = 0.4,
        )
        val r = ok(PpgAnalyzer.analyzeDual(dual))
        assertTrue(r.channel == "RED", "expected RED channel chosen, got ${r.channel}")
        assertTrue(abs(r.bpm - 82.0) <= 3.0, "BPM=${"%.1f".format(r.bpm)} should be ~82")
    }

    @Test
    fun dual_prefersGreenWhenRedSaturated() {
        // Red clipped near full-scale (DC 254) → green should be chosen even if both pulsate.
        val beats = PpgSynth.constantBeats(70.0, 30.0)
        val dual = PpgSynth.buildDual(
            beats, durationSec = 30.0,
            dcRed = 254.0, acRed = 3.0, dcGreen = 150.0, acGreen = 3.0, noiseStd = 0.3,
        )
        val r = ok(PpgAnalyzer.analyzeDual(dual))
        assertTrue(r.channel == "GREEN", "saturated red ⇒ expected GREEN, got ${r.channel}")
    }

    @Test
    fun windowsUsed_reportedForRobustness() {
        val beats = PpgSynth.constantBeats(72.0, 40.0)
        val r = ok(PpgAnalyzer.analyze(PpgSynth.build(beats, durationSec = 40.0)))
        assertTrue(r.hrWindowsUsed >= 3, "expected several windows used, got ${r.hrWindowsUsed}")
    }
}
