package com.heart.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heart.app.R
import com.heart.app.camera.RedChannelAnalyzer
import com.heart.app.measure.MeasurePhase
import com.heart.app.measure.MeasureViewModel
import com.heart.app.ui.DisclaimerCard
import com.heart.app.ui.WaveformView
import com.heart.core.model.MeasurementResult
import java.util.concurrent.Executors

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun MeasureScreen(onResult: (MeasurementResult) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: MeasureViewModel = viewModel()
    val ui by vm.state.collectAsState()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        vm.reset()
        if (!granted) permLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(ui.phase, ui.result) {
        val r = ui.result
        if (ui.phase == MeasurePhase.RESULT && r != null) onResult(r)
    }

    val previewView = remember { PreviewView(context) }
    val cameraHolder = remember { mutableStateOf<Camera?>(null) }

    if (granted) {
        DisposableEffect(lifecycleOwner) {
            val executor = Executors.newSingleThreadExecutor()
            val future = ProcessCameraProvider.getInstance(context)
            // Captured by the listener (write) and onDispose (read) — avoids a blocking
            // future.get() on the main thread at dispose time (4-way review: ANR risk).
            var boundProvider: ProcessCameraProvider? = null
            future.addListener({
                val provider = future.get()
                boundProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, RedChannelAnalyzer(vm::onSample))

                runCatching {
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                    )
                    camera.cameraControl.enableTorch(true)
                    cameraHolder.value = camera
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                boundProvider?.let { runCatching { it.unbindAll() } }
                cameraHolder.value = null
                executor.shutdown()
            }
        }

        // Lock exposure & white balance only AFTER warm-up, once auto-exposure has
        // converged on the torch-lit fingertip (4-way review: don't lock too early).
        LaunchedEffect(ui.phase, cameraHolder.value) {
            val cam = cameraHolder.value
            if (cam != null && ui.phase == MeasurePhase.MEASURING) {
                runCatching {
                    Camera2CameraControl.from(cam.cameraControl).captureRequestOptions =
                        CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                            .build()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("측정", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (!granted) {
            Spacer(Modifier.height(40.dp))
            Text("측정을 위해 카메라 권한이 필요합니다.", fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("카메라 권한 허용")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCancel) { Text("뒤로") }
            return@Column
        }

        Text(stringResource(R.string.measure_guide), fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            val progress = ui.progress
            if (ui.phase == MeasurePhase.MEASURING || ui.phase == MeasurePhase.ANALYZING) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 10.dp,
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(160.dp), strokeWidth = 6.dp)
            }
            Text(
                text = when (ui.phase) {
                    MeasurePhase.MEASURING -> "${(progress * 100).toInt()}%"
                    MeasurePhase.ANALYZING -> "분석"
                    else -> "—"
                },
                fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = when (ui.phase) {
                MeasurePhase.WAITING_FINGER -> stringResource(R.string.measure_finger_missing)
                MeasurePhase.WARMUP -> stringResource(R.string.measure_warmup)
                MeasurePhase.MEASURING -> stringResource(R.string.measure_measuring)
                MeasurePhase.ANALYZING -> "분석 중…"
                MeasurePhase.INSUFFICIENT -> ui.insufficientReason ?: stringResource(R.string.result_low_quality)
                MeasurePhase.RESULT -> "완료"
            },
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (ui.phase == MeasurePhase.INSUFFICIENT) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(20.dp))
        WaveformView(values = ui.waveform)

        Spacer(Modifier.height(24.dp))
        if (ui.phase == MeasurePhase.INSUFFICIENT) {
            Button(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("다시 측정")
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("취소")
        }

        Spacer(Modifier.height(16.dp))
        DisclaimerCard(stringResource(R.string.disclaimer_short))
    }
}
