package com.heart.core

import com.heart.core.model.SignalQuality
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class PpgAnalyzerTest {

    private fun ok(samples: List<com.heart.core.model.PpgSample>): com.heart.core.model.MeasurementResult {
        return when (val a = PpgAnalyzer.analyze(samples)) {
            is PpgAnalyzer.Analysis.Ok -> a.result
            is PpgAnalyzer.Analysis.Insufficient -> fail("expected Ok, got Insufficient: ${a.reason}")
        }
    }

    @Test
    fun hr_recoveredAcrossRange() {
        val targets = listOf(50.0, 60.0, 72.0, 90.0, 120.0, 150.0)
        for (bpm in targets) {
            val beats = PpgSynth.constantBeats(bpm, 30.0)
            val samples = PpgSynth.build(beats, durationSec = 30.0, noiseStd = 0.25)
            val r = ok(samples)
            assertTrue(
                abs(r.bpm - bpm) <= 2.5,
                "BPM target $bpm but got ${"%.1f".format(r.bpm)} (Δ${"%.1f".format(abs(r.bpm - bpm))})"
            )
            assertEquals(SignalQuality.GOOD, r.quality, "expected GOOD quality at $bpm bpm (snr=${"%.1f".format(r.snrDb)})")
        }
    }

    @Test
    fun hr_robustToModerateNoise() {
        val beats = PpgSynth.constantBeats(78.0, 30.0)
        val samples = PpgSynth.build(beats, durationSec = 30.0, acAmp = 2.0, noiseStd = 1.0)
        val r = ok(samples)
        assertTrue(abs(r.bpm - 78.0) <= 4.0, "noisy BPM ${"%.1f".format(r.bpm)} too far from 78")
        assertTrue(r.quality != SignalQuality.POOR, "quality should be at least FAIR under moderate noise")
    }

    @Test
    fun hrv_rmssdRecoveredFromKnownIbis() {
        // Construct IBIs around 833 ms (72 bpm) with a known successive-difference pattern.
        val base = 833.0
        val ibis = ArrayList<Double>()
        // Alternate ±30 ms → RMSSD of successive diffs ≈ 60 ms.
        for (i in 0 until 40) ibis.add(base + if (i % 2 == 0) 30.0 else -30.0)
        val beats = PpgSynth.beatsFromIbis(ibis)
        val dur = beats.last() + 1.0
        val samples = PpgSynth.build(beats, durationSec = dur, noiseStd = 0.2)
        val r = ok(samples)
        // Ground-truth RMSSD of the ±30 alternation = sqrt(mean(60^2)) = 60 ms.
        assertTrue(
            abs(r.rmssdMs - 60.0) <= 18.0,
            "RMSSD recovered ${"%.1f".format(r.rmssdMs)} ms, expected ~60 ms"
        )
        assertTrue(r.ibisMs.size >= 30, "expected ~39 IBIs, got ${r.ibisMs.size}")
    }

    @Test
    fun respiration_recoveredFromRsa() {
        val respBpm = 15.0
        val beats = PpgSynth.rsaBeats(meanBpm = 70.0, respBpm = respBpm, rsaDepth = 0.12, durationSec = 60.0)
        val samples = PpgSynth.build(beats, durationSec = 60.0, noiseStd = 0.15)
        val r = ok(samples)
        val resp = r.respirationBpm
        assertTrue(resp != null, "respiration should be derivable from clean 60 s RSA signal")
        assertTrue(abs(resp - respBpm) <= 2.5, "respiration ${"%.1f".format(resp)} bpm, expected ~$respBpm")
    }

    @Test
    fun irregularRhythm_flaggedForVariable_notForRegular() {
        // Regular.
        val regular = ok(PpgSynth.build(PpgSynth.constantBeats(72.0, 30.0), durationSec = 30.0))
        assertTrue(!regular.irregularRhythm, "regular rhythm should not be flagged")

        // Highly irregular IBIs.
        val rng = java.util.Random(7)
        val ibis = (0 until 40).map { 800.0 + rng.nextInt(500) - 250 }.map { it.coerceIn(400.0, 1400.0) }
        val beats = PpgSynth.beatsFromIbis(ibis)
        val irregular = ok(PpgSynth.build(beats, durationSec = beats.last() + 1.0, noiseStd = 0.2))
        assertTrue(irregular.irregularRhythm, "highly variable rhythm should be flagged")
    }

    @Test
    fun perfusionIndex_isPositiveForPulsatileSignal() {
        val r = ok(PpgSynth.build(PpgSynth.constantBeats(72.0, 30.0), durationSec = 30.0, dc = 150.0, acAmp = 3.0))
        assertTrue(r.perfusionIndex > 0.3, "PI should be clearly positive, was ${"%.2f".format(r.perfusionIndex)}")
    }

    @Test
    fun insufficient_whenTooShort() {
        val beats = PpgSynth.constantBeats(72.0, 5.0)
        val samples = PpgSynth.build(beats, durationSec = 5.0)
        val a = PpgAnalyzer.analyze(samples)
        assertTrue(a is PpgAnalyzer.Analysis.Insufficient, "5 s should be insufficient")
    }

    @Test
    fun insufficient_whenFrameRateTooLow() {
        val beats = PpgSynth.constantBeats(72.0, 30.0)
        val samples = PpgSynth.build(beats, fs = 6.0, durationSec = 30.0)
        val a = PpgAnalyzer.analyze(samples)
        assertTrue(a is PpgAnalyzer.Analysis.Insufficient, "6 fps should be insufficient")
    }
}
