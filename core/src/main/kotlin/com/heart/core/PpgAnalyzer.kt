package com.heart.core

import com.heart.core.dsp.Filters
import com.heart.core.dsp.Hrv
import com.heart.core.dsp.PeakDetector
import com.heart.core.dsp.Perfusion
import com.heart.core.dsp.Resampler
import com.heart.core.dsp.Respiration
import com.heart.core.dsp.Spectral
import com.heart.core.model.MeasurementResult
import com.heart.core.model.PpgSample
import com.heart.core.model.SignalQuality
import kotlin.math.abs

/**
 * End-to-end finger-PPG analysis: raw camera intensity samples → vital signs.
 *
 * Pipeline (see DEV-PLAN.md §3):
 *   resample → detrend → band-pass(0.7–4 Hz) → {FFT HR + SNR} & {peak HR + IBIs}
 *   → cross-check → HRV → autonomic light → PI → respiration → quality gate.
 */
object PpgAnalyzer {

    /** Minimum seconds of signal required to even attempt an estimate. */
    const val MIN_SECONDS = 10.0

    /** Target uniform sampling rate for analysis (Hz). */
    private const val TARGET_FS = 30.0

    sealed interface Analysis {
        data class Ok(val result: MeasurementResult) : Analysis
        data class Insufficient(val reason: String) : Analysis
    }

    fun analyze(samples: List<PpgSample>): Analysis {
        if (samples.size < 2) return Analysis.Insufficient("샘플이 너무 적습니다")

        val rawFs = Resampler.estimateFs(samples)
        if (rawFs < 10.0) return Analysis.Insufficient("프레임레이트가 너무 낮습니다 (${"%.1f".format(rawFs)}fps)")

        val durationSec = (samples.last().timeMs - samples.first().timeMs) / 1000.0
        if (durationSec < MIN_SECONDS) {
            return Analysis.Insufficient("측정 시간이 짧습니다 (${"%.0f".format(durationSec)}s)")
        }

        val uniform = Resampler.resample(samples, TARGET_FS)
        val raw = uniform.values
        if (raw.size < (TARGET_FS * MIN_SECONDS).toInt()) {
            return Analysis.Insufficient("유효 신호가 부족합니다")
        }

        // Drop first 2 s (camera auto-exposure settling), per 4-way review.
        val warmup = (2 * TARGET_FS).toInt()
        val trimmed = if (raw.size > warmup + (TARGET_FS * 4).toInt()) {
            raw.copyOfRange(warmup, raw.size)
        } else raw

        val detrended = Filters.detrend(trimmed, (TARGET_FS * 1.5).toInt())
        val band = Filters.bandPass(detrended, TARGET_FS)

        // Spectral HR (robust) + SNR.
        val spec = Spectral.estimateHr(band, TARGET_FS)

        // Peak-based HR + IBIs for HRV.
        val peaks = PeakDetector.detect(band, TARGET_FS)
        val rawIbis = PeakDetector.intervalsMs(peaks, TARGET_FS)
        val plausibleIbis = Hrv.plausibleIbis(rawIbis)
        val ibis = Hrv.cleanIbis(rawIbis)
        val hrv = Hrv.metrics(ibis)

        // Cross-check spectral vs peak HR (agreement → higher confidence).
        val bpm = when {
            spec.bpm in 40.0..220.0 && hrv.bpmFromIbi in 40.0..220.0 ->
                // Weighted toward spectral estimate (less sensitive to a missed beat).
                0.6 * spec.bpm + 0.4 * hrv.bpmFromIbi
            spec.bpm in 40.0..220.0 -> spec.bpm
            hrv.bpmFromIbi in 40.0..220.0 -> hrv.bpmFromIbi
            else -> 0.0
        }
        if (bpm <= 0.0) return Analysis.Insufficient("심박을 검출하지 못했습니다")

        val agreement = if (hrv.bpmFromIbi > 0) abs(spec.bpm - hrv.bpmFromIbi) else Double.MAX_VALUE

        val pi = Perfusion.perfusionIndex(trimmed, band)
        val respiration = Respiration.estimate(ibis)
        // Rhythm screening runs on plausibility-filtered (not artifact-cleaned) intervals,
        // since the artifact-cleaning step would mask genuine irregularity.
        val irregular = Hrv.irregularRhythm(plausibleIbis)
        val autonomic = Hrv.autonomicBalance(hrv.rmssdMs, ibis.size)

        val quality = quality(spec.snrDb, agreement, ibis.size, pi)

        return Analysis.Ok(
            MeasurementResult(
                bpm = bpm,
                ibisMs = ibis.toList(),
                rmssdMs = hrv.rmssdMs,
                sdnnMs = hrv.sdnnMs,
                pnn50 = hrv.pnn50,
                perfusionIndex = pi,
                snrDb = spec.snrDb,
                quality = quality,
                autonomic = autonomic,
                respirationBpm = respiration,
                irregularRhythm = irregular,
            )
        )
    }

    private fun quality(snrDb: Double, hrAgreement: Double, beats: Int, pi: Double): SignalQuality {
        var score = 0
        if (snrDb >= 6.0) score++ else if (snrDb < 2.0) score--
        if (hrAgreement <= 5.0) score++ else if (hrAgreement > 12.0) score--
        if (beats >= 30) score++ else if (beats < 12) score--
        if (pi >= 1.0) score++ else if (pi < 0.2) score--
        return when {
            score >= 3 -> SignalQuality.GOOD
            score >= 1 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }
    }
}
