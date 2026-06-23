package com.heart.core

import com.heart.core.model.PpgSample
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthetic PPG generator for deterministic, hardware-free algorithm verification.
 * Builds a camera-like red-channel intensity stream by placing pulse templates at
 * given beat times, with configurable DC, baseline wander, amplitude/respiration
 * modulation and Gaussian noise.
 */
object PpgSynth {

    /** Systolic + dicrotic pulse template, peak at the beat instant (tRel = 0). */
    private fun pulse(tRel: Double): Double {
        val systolic = exp(-0.5 * (tRel / 0.045).let { it * it })
        val dicrotic = 0.3 * exp(-0.5 * ((tRel - 0.20) / 0.07).let { it * it })
        return systolic + dicrotic
    }

    /**
     * @param beatTimes ascending beat instants in seconds
     * @param fs camera frame rate (Hz)
     * @param durationSec total stream length
     * @param dc baseline intensity (camera red mean)
     * @param acAmp pulsatile amplitude (intensity units)
     * @param baselineAmp slow baseline-wander amplitude
     * @param baselineHz baseline-wander frequency
     * @param respDepth amplitude-modulation depth from breathing (0..1)
     * @param respBpm breathing rate for amplitude modulation
     * @param noiseStd Gaussian noise standard deviation
     * @param jitterMs per-frame timestamp jitter (ms)
     */
    fun build(
        beatTimes: List<Double>,
        fs: Double = 30.0,
        durationSec: Double,
        dc: Double = 150.0,
        acAmp: Double = 3.0,
        baselineAmp: Double = 1.5,
        baselineHz: Double = 0.15,
        respDepth: Double = 0.0,
        respBpm: Double = 0.0,
        noiseStd: Double = 0.2,
        jitterMs: Double = 3.0,
        seed: Long = 42L,
    ): List<PpgSample> {
        val rng = Random(seed)
        val n = (durationSec * fs).toInt()
        val out = ArrayList<PpgSample>(n)
        val respHz = respBpm / 60.0
        // Window of nearby beats to sum (pulse template decays quickly).
        for (i in 0 until n) {
            val t = i / fs
            var ac = 0.0
            for (tb in beatTimes) {
                val d = t - tb
                if (d < -0.3) break
                if (d > 0.5) continue
                ac += pulse(d)
            }
            val respMod = if (respDepth > 0 && respHz > 0) 1.0 + respDepth * sin(2 * PI * respHz * t) else 1.0
            val baseline = baselineAmp * sin(2 * PI * baselineHz * t)
            val noise = noiseStd * rng.nextGaussian()
            val value = dc + baseline + acAmp * respMod * ac + noise
            val tMs = (t * 1000.0 + jitterMs * (rng.nextDouble() - 0.5)).toLong()
            out.add(PpgSample(tMs, value))
        }
        return out
    }

    /** Constant-rate beat times. */
    fun constantBeats(bpm: Double, durationSec: Double): List<Double> {
        val interval = 60.0 / bpm
        val beats = ArrayList<Double>()
        var t = 0.5
        while (t < durationSec) { beats.add(t); t += interval }
        return beats
    }

    /**
     * Beat times whose instantaneous interval is sinusoidally modulated at [respBpm]
     * (respiratory sinus arrhythmia), around [meanBpm]. Used for respiration tests.
     */
    fun rsaBeats(meanBpm: Double, respBpm: Double, rsaDepth: Double, durationSec: Double): List<Double> {
        val meanInterval = 60.0 / meanBpm
        val respHz = respBpm / 60.0
        val beats = ArrayList<Double>()
        var t = 0.5
        while (t < durationSec) {
            beats.add(t)
            val interval = meanInterval * (1.0 + rsaDepth * sin(2 * PI * respHz * t))
            t += interval
        }
        return beats
    }

    /** Dual-channel stream (shared timestamps/beats, independent noise + per-channel amplitude). */
    fun buildDual(
        beatTimes: List<Double>,
        fs: Double = 30.0,
        durationSec: Double,
        dcRed: Double = 150.0,
        acRed: Double = 3.0,
        dcGreen: Double = 150.0,
        acGreen: Double = 3.0,
        noiseStd: Double = 0.2,
        seed: Long = 42L,
    ): List<com.heart.core.model.DualPpgSample> {
        val rng = Random(seed)
        val n = (durationSec * fs).toInt()
        val out = ArrayList<com.heart.core.model.DualPpgSample>(n)
        for (i in 0 until n) {
            val t = i / fs
            var ac = 0.0
            for (tb in beatTimes) {
                val d = t - tb
                if (d < -0.3) break
                if (d > 0.5) continue
                ac += pulse(d)
            }
            val red = dcRed + acRed * ac + noiseStd * rng.nextGaussian()
            val green = dcGreen + acGreen * ac + noiseStd * rng.nextGaussian()
            val tMs = (t * 1000.0 + 3.0 * (rng.nextDouble() - 0.5)).toLong()
            out.add(com.heart.core.model.DualPpgSample(tMs, red, green))
        }
        return out
    }

    /** Corrupt a time span [fromSec,toSec) with strong additive noise (simulates motion). */
    fun corrupt(
        samples: List<PpgSample>,
        fromSec: Double,
        toSec: Double,
        noiseStd: Double = 15.0,
        seed: Long = 99L,
    ): List<PpgSample> {
        if (samples.isEmpty()) return samples
        val rng = Random(seed)
        val t0 = samples.first().timeMs
        return samples.map { s ->
            val rel = (s.timeMs - t0) / 1000.0
            if (rel in fromSec..toSec) PpgSample(s.timeMs, s.value + noiseStd * rng.nextGaussian())
            else s
        }
    }

    /** Beat times from an explicit IBI list (ms). Returns Pair(beatTimes, cumulativeStart). */
    fun beatsFromIbis(ibisMs: List<Double>, startSec: Double = 0.5): List<Double> {
        val beats = ArrayList<Double>(ibisMs.size + 1)
        var t = startSec
        beats.add(t)
        for (ibi in ibisMs) { t += ibi / 1000.0; beats.add(t) }
        return beats
    }
}
