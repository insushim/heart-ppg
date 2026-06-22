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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

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
    private var analysisJob: Job? = null
    // Monotonic session id: bumped on reset so a late-finishing analysis from a previous
    // session cannot overwrite the new state (4-way review: reset/analysis race).
    private val session = AtomicInteger(0)

    fun reset() {
        synchronized(lock) {
            session.incrementAndGet()
            analysisJob?.cancel()
            analysisJob = null
            samples.clear()
            wave.clear()
            phaseStartMs = 0L
            measureStartMs = 0L
            // Assign state inside the lock so a concurrent onSample can't observe a
            // half-reset (cleared fields but stale phase) — local-validator C1.
            _state.value = MeasureUiState()
        }
    }

    /** Called from the camera analyzer thread for every frame. */
    fun onSample(tMs: Long, red: Double, fingerPresent: Boolean) {
        var snapshot: List<PpgSample>? = null
        var snapshotSession = -1
        synchronized(lock) {
            val phase = _state.value.phase
            if (phase == MeasurePhase.ANALYZING || phase == MeasurePhase.RESULT ||
                phase == MeasurePhase.INSUFFICIENT
            ) return

            when (phase) {
                MeasurePhase.WAITING_FINGER -> {
                    if (fingerPresent) {
                        samples.clear()
                        phaseStartMs = tMs
                        samples.add(PpgSample(tMs, red))
                        pushWave(red)
                        emit(MeasurePhase.WARMUP, 0f)
                    }
                    // No finger: stay silent (no per-frame emit ⇒ no recomposition churn).
                }
                MeasurePhase.WARMUP -> {
                    if (!fingerPresent) {
                        samples.clear()
                        emit(MeasurePhase.WAITING_FINGER, 0f)
                    } else {
                        // Keep warmup samples: the core (PpgAnalyzer) trims its own 2 s
                        // warmup, so feeding warmup+measure yields the intended window.
                        samples.add(PpgSample(tMs, red))
                        pushWave(red)
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
                        pushWave(red)
                        val elapsed = tMs - measureStartMs
                        val p = (elapsed.toFloat() / MEASURE_MS).coerceIn(0f, 1f)
                        if (elapsed >= MEASURE_MS) {
                            snapshot = ArrayList(samples)
                            snapshotSession = session.get()
                            emit(MeasurePhase.ANALYZING, 1f)
                        } else {
                            emit(MeasurePhase.MEASURING, p)
                        }
                    }
                }
                else -> {}
            }
        }

        val data = snapshot
        if (data != null) runAnalysis(data, snapshotSession)
    }

    private fun runAnalysis(data: List<PpgSample>, forSession: Int) {
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            val analysis = PpgAnalyzer.analyze(data)
            if (forSession != session.get()) return@launch // superseded by a reset
            when (analysis) {
                is PpgAnalyzer.Analysis.Ok -> {
                    persist(analysis.result)
                    if (forSession == session.get()) {
                        _state.value = _state.value.copy(
                            phase = MeasurePhase.RESULT, progress = 1f, result = analysis.result,
                        )
                    }
                }
                is PpgAnalyzer.Analysis.Insufficient -> {
                    if (forSession == session.get()) {
                        _state.value = _state.value.copy(
                            phase = MeasurePhase.INSUFFICIENT, insufficientReason = analysis.reason,
                        )
                    }
                }
            }
        }
    }

    private suspend fun persist(result: MeasurementResult) {
        val dao = (getApplication<Application>() as HeartApp).database.measurementDao()
        withContext(Dispatchers.IO) {
            runCatching { dao.insert(result.toEntity(System.currentTimeMillis())) }
                .onFailure { android.util.Log.w("Heart", "측정 저장 실패", it) }
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
