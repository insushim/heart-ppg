package com.heart.core.model

/** A single PPG sample: monotonic timestamp (ms) + channel intensity (e.g. mean red). */
data class PpgSample(val timeMs: Long, val value: Double)

/**
 * Overall confidence in a measurement. Drives whether numbers are shown to the user.
 * Per 4-way review: results below GOOD must be flagged, never presented as reliable.
 */
enum class SignalQuality { GOOD, FAIR, POOR }

/**
 * Autonomic balance band derived from short-term HRV (RMSSD).
 * Deliberately NOT called "stress" (GPT/codex review: the word over-claims).
 * Shown as a traffic light only — never as an absolute score.
 */
enum class AutonomicBalance { BALANCED, MODERATE, ELEVATED, UNKNOWN }

/** Result of a finger-PPG measurement session. */
data class MeasurementResult(
    val bpm: Double,
    /** Inter-beat intervals in milliseconds, in order. */
    val ibisMs: List<Double>,
    val rmssdMs: Double,
    val sdnnMs: Double,
    val pnn50: Double,
    /** Perfusion index (AC/DC * 100), a signal-quality / peripheral-flow proxy. */
    val perfusionIndex: Double,
    /** Signal-to-noise ratio of the cardiac fundamental, in dB. */
    val snrDb: Double,
    val quality: SignalQuality,
    val autonomic: AutonomicBalance,
    /** Respiration rate (breaths/min) if derivable, else null. */
    val respirationBpm: Double? = null,
    /** True when beat-to-beat intervals look irregular (AFib *screening* hint, not a diagnosis). */
    val irregularRhythm: Boolean = false,
    /** Which colour channel was used ("RED" / "GREEN") — adaptive per measurement. */
    val channel: String = "RED",
    /** How many sliding windows contributed to the final HR (higher = more robust). */
    val hrWindowsUsed: Int = 0,
)

/** A PPG sample carrying both red and green channel means (for adaptive channel choice). */
data class DualPpgSample(val timeMs: Long, val red: Double, val green: Double)
