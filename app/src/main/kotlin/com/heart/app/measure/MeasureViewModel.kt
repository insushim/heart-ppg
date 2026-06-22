package com.heart.app.measure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heart.app.HeartApp
import com.heart.app.data.toEntity
import com.heart.core.PpgAnalyzer
import com.heart.core.model.MeasurementResult
import com.heart.core.model.PpgSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MeasurePhase { WAITING_FINGER, WARMUP, MEASURING, ANALYZING, RESULT, INSUFFICIENT }

data class MeasureUiState(
    val phase: MeasurePhase = MeasurePhase.WAITING_FINGER,
    val progress: Float = 0f,
    val waveform: List<Float> = emptyList(),
    val result: MeasurementResult? = null,
    val insufficientReason: String? = null,
)

/**
 * Drives a finger-PPG session: waits for a fingertip, discards a warm-up window
 * (camera auto-exposure settling), collects the measurement window, then runs the
 * pure-Kotlin [PpgAnalyzer]. All sample bookkeeping is synchronized because frames
 * arrive on the camera executor thread.
 */
class MeasureViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val WARMUP_MS = 2_000L
        const val MEASURE_MS = 30_000L
        const val WAVE_LEN = 150
    }

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    private val lock = Any()
    private val samples = ArrayList<PpgSample>(2048)
    private val wave = ArrayDeque<Float>(WAVE_LEN)
    private var phaseStartMs = 0L
    private var measureStartMs = 0L
    private var analyzing = false

    fun reset() {
        synchronized(lock) {
            samples.clear()
            wave.clear()
            analyzing = false
            phaseStartMs = 0L
            measureStartMs = 0L
        }
        _state.value = MeasureUiState()
    }

    /** Called from the camera analyzer thread for every frame. */
    fun onSample(tMs: Long, red: Double, fingerPresent: Boolean) {
        var startAnalysis = false
        var snapshot: List<PpgSample>? = null
        synchronized(lock) {
            val phase = _state.value.phase
            if (phase == MeasurePhase.ANALYZING || phase == MeasurePhase.RESULT ||
                phase == MeasurePhase.INSUFFICIENT
            ) return

            pushWave(red)

            when (phase) {
                MeasurePhase.WAITING_FINGER -> {
                    if (fingerPresent) {
                        samples.clear()
                        phaseStartMs = tMs
                        samples.add(PpgSample(tMs, red))
                        emit(MeasurePhase.WARMUP, 0f)
                    } else {
                        emit(MeasurePhase.WAITING_FINGER, 0f)
                    }
                }
                MeasurePhase.WARMUP -> {
                    if (!fingerPresent) {
                        samples.clear()
                        emit(MeasurePhase.WAITING_FINGER, 0f)
                    } else {
                        samples.add(PpgSample(tMs, red))
                        if (tMs - phaseStartMs >= WARMUP_MS) {
                            measureStartMs = tMs
                            emit(MeasurePhase.MEASURING, 0f)
                        } else {
                            emit(MeasurePhase.WARMUP, 0f)
                        }
                    }
                }
                MeasurePhase.MEASURING -> {
                    if (!fingerPresent) {
                        // Lost contact: restart from scratch to avoid corrupting the window.
                        samples.clear()
                        emit(MeasurePhase.WAITING_FINGER, 0f)
                    } else {
                        samples.add(PpgSample(tMs, red))
                        val elapsed = tMs - measureStartMs
                        val p = (elapsed.toFloat() / MEASURE_MS).coerceIn(0f, 1f)
                        if (elapsed >= MEASURE_MS) {
                            analyzing = true
                            startAnalysis = true
                            snapshot = ArrayList(samples)
                            emit(MeasurePhase.ANALYZING, 1f)
                        } else {
                            emit(MeasurePhase.MEASURING, p)
                        }
                    }
                }
                else -> {}
            }
        }

        if (startAnalysis && snapshot != null) runAnalysis(snapshot!!)
    }

    private fun runAnalysis(data: List<PpgSample>) {
        viewModelScope.launch(Dispatchers.Default) {
            when (val a = PpgAnalyzer.analyze(data)) {
                is PpgAnalyzer.Analysis.Ok -> {
                    persist(a.result)
                    _state.value = _state.value.copy(
                        phase = MeasurePhase.RESULT,
                        progress = 1f,
                        result = a.result,
                    )
                }
                is PpgAnalyzer.Analysis.Insufficient -> {
                    _state.value = _state.value.copy(
                        phase = MeasurePhase.INSUFFICIENT,
                        insufficientReason = a.reason,
                    )
                }
            }
        }
    }

    private fun persist(result: MeasurementResult) {
        val dao = (getApplication<Application>() as HeartApp).database.measurementDao()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dao.insert(result.toEntity(System.currentTimeMillis())) }
        }
    }

    // --- helpers (call only while holding [lock]) ---

    private fun pushWave(red: Double) {
        if (wave.size >= WAVE_LEN) wave.removeFirst()
        wave.addLast(red.toFloat())
    }

    private fun emit(phase: MeasurePhase, progress: Float) {
        _state.value = _state.value.copy(
            phase = phase,
            progress = progress,
            waveform = wave.toList(),
        )
    }
}
