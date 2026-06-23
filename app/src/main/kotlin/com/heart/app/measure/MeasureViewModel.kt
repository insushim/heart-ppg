package com.heart.app.measure

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heart.app.HeartApp
import com.heart.app.data.toEntity
import com.heart.core.PpgAnalyzer
import com.heart.core.model.DualPpgSample
import com.heart.core.model.MeasurementResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** Live provisional heart rate shown during measurement (null until enough data). */
    val liveBpm: Int? = null,
    // Diagnostics (visible on screen) — frames seen, latest red intensity, camera error.
    val frames: Int = 0,
    val lastRed: Double = 0.0,
    val fingerPresent: Boolean = false,
    val error: String? = null,
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

    // One-shot navigation event: emitted once when a measurement finishes. Using an
    // event (not the RESULT state) prevents re-entering the measure screen from
    // immediately bouncing back to a stale previous result.
    private val _completed = MutableSharedFlow<MeasurementResult>(extraBufferCapacity = 1)
    val completed: SharedFlow<MeasurementResult> = _completed.asSharedFlow()

    private val lock = Any()
    private val samples = ArrayList<DualPpgSample>(2048)
    private val wave = ArrayDeque<Float>(WAVE_LEN)
    private var phaseStartMs = 0L
    private var measureStartMs = 0L
    private var analysisJob: Job? = null
    private var frameCount = 0
    private var lastRed = 0.0
    private var lastPresent = false
    private var lastLiveFrame = 0
    @Volatile private var liveBusy = false
    // Monotonic session id: bumped on reset so a late-finishing analysis from a previous
    // session cannot overwrite the new state (4-way review: reset/analysis race).
    private val session = AtomicInteger(0)

    /** Surface a camera setup failure to the UI instead of failing silently. */
    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun reset() {
        synchronized(lock) {
            session.incrementAndGet()
            analysisJob?.cancel()
            analysisJob = null
            samples.clear()
            wave.clear()
            phaseStartMs = 0L
            measureStartMs = 0L
            frameCount = 0
            lastRed = 0.0
            lastPresent = false
            lastLiveFrame = 0
            liveBusy = false
            // Assign state inside the lock so a concurrent onSample can't observe a
            // half-reset (cleared fields but stale phase) — local-validator C1.
            _state.value = MeasureUiState()
        }
    }

    /** Called from the camera analyzer thread for every frame. */
    fun onSample(tMs: Long, red: Double, green: Double, fingerPresent: Boolean) {
        var snapshot: List<DualPpgSample>? = null
        var snapshotSession = -1
        var liveSnapshot: List<DualPpgSample>? = null
        synchronized(lock) {
            val phase = _state.value.phase
            if (phase == MeasurePhase.ANALYZING || phase == MeasurePhase.RESULT ||
                phase == MeasurePhase.INSUFFICIENT
            ) return

            frameCount++
            lastRed = red
            lastPresent = fingerPresent

            when (phase) {
                MeasurePhase.WAITING_FINGER -> {
                    if (fingerPresent) {
                        samples.clear()
                        phaseStartMs = tMs
                        samples.add(DualPpgSample(tMs, red, green))
                        pushWave(red)
                        emit(MeasurePhase.WARMUP, 0f)
                    } else {
                        // Emit lightweight diagnostics so the user can see frames/brightness
                        // even before a finger is detected (remote debugging).
                        emit(MeasurePhase.WAITING_FINGER, 0f)
                    }
                }
                MeasurePhase.WARMUP -> {
                    if (!fingerPresent) {
                        samples.clear()
                        emit(MeasurePhase.WAITING_FINGER, 0f)
                    } else {
                        // Keep warmup samples: the core (PpgAnalyzer) trims its own 2 s
                        // warmup, so feeding warmup+measure yields the intended window.
                        samples.add(DualPpgSample(tMs, red, green))
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
                        samples.add(DualPpgSample(tMs, red, green))
                        pushWave(red)
                        val elapsed = tMs - measureStartMs
                        val p = (elapsed.toFloat() / MEASURE_MS).coerceIn(0f, 1f)
                        if (elapsed >= MEASURE_MS) {
                            snapshot = ArrayList(samples)
                            snapshotSession = session.get()
                            emit(MeasurePhase.ANALYZING, 1f)
                        } else {
                            emit(MeasurePhase.MEASURING, p)
                            // Live provisional HR every ~3 s once enough data is collected.
                            if (!liveBusy && frameCount - lastLiveFrame >= 90 &&
                                samples.size >= (PpgAnalyzer.MIN_SECONDS * 30).toInt()
                            ) {
                                lastLiveFrame = frameCount
                                liveBusy = true
                                liveSnapshot = ArrayList(samples)
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        val data = snapshot
        if (data != null) runAnalysis(data, snapshotSession)
        val live = liveSnapshot
        if (live != null) runLiveEstimate(live)
    }

    private fun runLiveEstimate(data: List<DualPpgSample>) {
        viewModelScope.launch(Dispatchers.Default) {
            val a = PpgAnalyzer.analyzeDual(data)
            if (a is PpgAnalyzer.Analysis.Ok && _state.value.phase == MeasurePhase.MEASURING) {
                _state.value = _state.value.copy(liveBpm = a.result.bpm.toInt())
            }
            liveBusy = false
        }
    }

    private fun runAnalysis(data: List<DualPpgSample>, forSession: Int) {
        analysisJob = viewModelScope.launch(Dispatchers.Default) {
            val analysis = PpgAnalyzer.analyzeDual(data)
            if (forSession != session.get()) return@launch // superseded by a reset
            when (analysis) {
                is PpgAnalyzer.Analysis.Ok -> {
                    persist(analysis.result)
                    if (forSession == session.get()) {
                        _state.value = _state.value.copy(
                            phase = MeasurePhase.RESULT, progress = 1f, result = analysis.result,
                        )
                        _completed.tryEmit(analysis.result)
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
            frames = frameCount,
            lastRed = lastRed,
            fingerPresent = lastPresent,
        )
    }
}
