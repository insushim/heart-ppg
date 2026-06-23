package com.heart.core

import com.heart.core.dsp.Filters
import com.heart.core.dsp.Hrv
import com.heart.core.dsp.PeakDetector
import com.heart.core.dsp.Perfusion
import com.heart.core.dsp.Resampler
import com.heart.core.dsp.Respiration
import com.heart.core.dsp.Spectral
import com.heart.core.dsp.WindowedHr
import com.heart.core.model.DualPpgSample
import com.heart.core.model.MeasurementResult
import com.heart.core.model.PpgSample
import com.heart.core.model.SignalQuality
import kotlin.math.abs

/**
 * End-to-end finger-PPG analysis: raw camera intensity samples → vital signs.
 *
 * Pipeline (see DEV-PLAN.md §3 + accuracy-proposal.md):
 *   resample → trim warm-up → detrend → band-pass(0.7–4 Hz)
 *   → robust sliding-window spectral HR  &  peak HR + IBIs
 *   → cross-check → HRV → autonomic light → PI → respiration → quality gate.
 *
 * [analyzeDual] additionally picks whichever colour channel (red/green) has the stronger
 * pulsatile signal for this measurement (adaptive channel selection).
 */
object PpgAnalyzer {

    const val MIN_SECONDS = 10.0
    private const val TARGET_FS = 30.0

    sealed interface Analysis {
        data class Ok(val result: MeasurementResult) : Analysis
        data class Insufficient(val reason: String) : Analysis
    }

    /** Single-channel entry point (red). */
    fun analyze(samples: List<PpgSample>): Analysis {
        val gate = gate(samples)
        if (gate != null) return Analysis.Insufficient(gate)
        val trimmed = trimmedUniform(samples)
        if (trimmed.size < (TARGET_FS * MIN_SECONDS).toInt()) {
            return Analysis.Insufficient("유효 신호가 부족합니다")
        }
        val result = analyzeChannel(trimmed, TARGET_FS, "RED")
            ?: return Analysis.Insufficient("심박을 검출하지 못했습니다")
        return Analysis.Ok(result)
    }

    /** Dual-channel entry point: adaptively uses the better of red/green. */
    fun analyzeDual(samples: List<DualPpgSample>): Analysis {
        if (samples.size < 2) return Analysis.Insufficient("샘플이 너무 적습니다")
        val redSamples = samples.map { PpgSample(it.timeMs, it.red) }
        val greenSamples = samples.map { PpgSample(it.timeMs, it.green) }

        val gate = gate(redSamples)
        if (gate != null) return Analysis.Insufficient(gate)

        val red = trimmedUniform(redSamples)
        val green = trimmedUniform(greenSamples)
        if (red.size < (TARGET_FS * MIN_SECONDS).toInt()) {
            return Analysis.Insufficient("유효 신호가 부족합니다")
        }

        val (channelValues, label) = chooseChannel(red, green)
        val result = analyzeChannel(channelValues, TARGET_FS, label)
            ?: return Analysis.Insufficient("심박을 검출하지 못했습니다")
        return Analysis.Ok(result)
    }

    // --- internals ---

    /** Returns an Insufficient reason, or null if the stream passes basic gating. */
    private fun gate(samples: List<PpgSample>): String? {
        if (samples.size < 2) return "샘플이 너무 적습니다"
        val rawFs = Resampler.estimateFs(samples)
        if (rawFs < 10.0) return "프레임레이트가 너무 낮습니다 (${"%.1f".format(rawFs)}fps)"
        val durationSec = (samples.last().timeMs - samples.first().timeMs) / 1000.0
        if (durationSec < MIN_SECONDS) return "측정 시간이 짧습니다 (${"%.0f".format(durationSec)}s)"
        return null
    }

    private fun trimmedUniform(samples: List<PpgSample>): DoubleArray {
        val raw = Resampler.resample(samples, TARGET_FS).values
        // Drop first 2 s (camera auto-exposure settling).
        val warmup = (2 * TARGET_FS).toInt()
        return if (raw.size > warmup + (TARGET_FS * 4).toInt()) raw.copyOfRange(warmup, raw.size) else raw
    }

    /** Pick the channel with the stronger pulsatile signal (higher cardiac-band SNR),
     *  preferring green when red is saturated (clipped) near full-scale. */
    private fun chooseChannel(red: DoubleArray, green: DoubleArray): Pair<DoubleArray, String> {
        val redSnr = channelSnr(red)
        val greenSnr = channelSnr(green)
        val redSaturated = red.average() > 250.0
        return when {
            redSaturated && greenSnr > 1.0 -> green to "GREEN"
            greenSnr > redSnr + 1.0 -> green to "GREEN"
            else -> red to "RED"
        }
    }

    private fun channelSnr(raw: DoubleArray): Double {
        if (raw.size < (TARGET_FS * 4).toInt()) return 0.0
        val band = Filters.bandPass(Filters.detrend(raw, (TARGET_FS * 1.5).toInt()), TARGET_FS)
        return Spectral.estimateHr(band, TARGET_FS).snrDb
    }

    /** Run the full vital-sign pipeline on one prepared (trimmed, uniform) channel. */
    private fun analyzeChannel(trimmed: DoubleArray, fs: Double, label: String): MeasurementResult? {
        val detrended = Filters.detrend(trimmed, (fs * 1.5).toInt())
        val band = Filters.bandPass(detrended, fs)

        // Robust sliding-window spectral HR (motion-resistant) + single-shot for cross-check.
        val windowed = WindowedHr.estimate(band, fs)
        val single = Spectral.estimateHr(band, fs)

        val peaks = PeakDetector.detect(band, fs)
        val rawIbis = PeakDetector.intervalsMs(peaks, fs)
        val plausibleIbis = Hrv.plausibleIbis(rawIbis)
        val ibis = Hrv.cleanIbis(rawIbis)
        val hrv = Hrv.metrics(ibis)

        val bpm = when {
            windowed.bpm in 40.0..220.0 && hrv.bpmFromIbi in 40.0..220.0 ->
                0.7 * windowed.bpm + 0.3 * hrv.bpmFromIbi
            windowed.bpm in 40.0..220.0 -> windowed.bpm
            hrv.bpmFromIbi in 40.0..220.0 -> hrv.bpmFromIbi
            else -> 0.0
        }
        if (bpm <= 0.0) return null

        val agreement = if (hrv.bpmFromIbi > 0) abs(windowed.bpm - hrv.bpmFromIbi) else Double.MAX_VALUE
        val pi = Perfusion.perfusionIndex(trimmed, band)
        val respiration = Respiration.estimate(ibis)
        val irregular = Hrv.irregularRhythm(plausibleIbis)
        val autonomic = Hrv.autonomicBalance(hrv.rmssdMs, ibis.size)
        val quality = quality(windowed.snrDb, agreement, ibis.size, pi, windowed.accepted)

        return MeasurementResult(
            bpm = bpm,
            ibisMs = ibis.toList(),
            rmssdMs = hrv.rmssdMs,
            sdnnMs = hrv.sdnnMs,
            pnn50 = hrv.pnn50,
            perfusionIndex = pi,
            snrDb = windowed.snrDb,
            quality = quality,
            autonomic = autonomic,
            respirationBpm = respiration,
            irregularRhythm = irregular,
            channel = label,
            hrWindowsUsed = windowed.accepted,
        )
    }

    private fun quality(snrDb: Double, hrAgreement: Double, beats: Int, pi: Double, windows: Int): SignalQuality {
        var score = 0
        if (snrDb >= 6.0) score++ else if (snrDb < 2.0) score--
        if (hrAgreement <= 5.0) score++ else if (hrAgreement > 12.0) score--
        if (beats >= 30) score++ else if (beats < 12) score--
        if (pi >= 1.0) score++ else if (pi < 0.2) score--
        if (windows >= 4) score++ // many agreeing windows ⇒ robust
        return when {
            score >= 3 -> SignalQuality.GOOD
            score >= 1 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }
    }
}
