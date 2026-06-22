package com.heart.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heart.app.R
import com.heart.app.ui.AutonomicLight
import com.heart.app.ui.DisclaimerCard
import com.heart.app.ui.Interpretation
import com.heart.app.ui.MetricRow
import com.heart.core.model.MeasurementResult
import com.heart.core.model.SignalQuality

@Composable
fun ResultScreen(result: MeasurementResult, onDone: () -> Unit, onRemeasure: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text("측정 결과", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Hero: heart rate.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("심박수", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
                Text(
                    "${result.bpm.toInt()}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 64.sp, fontWeight = FontWeight.Bold,
                )
                Text("bpm", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
                Text(Interpretation.qualityLabel(result.quality), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
            }
        }

        // Overall takeaway.
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                Interpretation.overall(result),
                modifier = Modifier.padding(14.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (result.quality == SignalQuality.POOR) {
            Spacer(Modifier.height(12.dp))
            WarnCard(stringResource(R.string.result_low_quality))
        }
        if (result.irregularRhythm) {
            Spacer(Modifier.height(12.dp))
            WarnCard(stringResource(R.string.result_irregular))
        }

        Spacer(Modifier.height(20.dp))
        Text("자율신경 컨디션", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        AutonomicLight(result.autonomic)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        MetricRow("심박변이도 (RMSSD)", "${result.rmssdMs.toInt()} ms")
        MetricRow("SDNN", "${result.sdnnMs.toInt()} ms")
        MetricRow("pNN50", "${(result.pnn50 * 100).toInt()} %")
        MetricRow("관류지수 (PI)", String.format("%.1f %%", result.perfusionIndex))
        result.respirationBpm?.let {
            MetricRow("호흡수", "${it.toInt()} 회/분", hint = "안정 시 추정")
        }
        MetricRow("신호 SNR", String.format("%.1f dB", result.snrDb))
        HorizontalDivider()

        // Detailed interpretation.
        Spacer(Modifier.height(20.dp))
        Text("📋 상세 해석", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        InterpretRow("심박수", Interpretation.heartRate(result.bpm))
        InterpretRow("심박변이도", Interpretation.rmssd(result.rmssdMs))
        InterpretRow("자율신경", Interpretation.autonomic(result.autonomic))
        InterpretRow("관류지수", Interpretation.perfusion(result.perfusionIndex))
        InterpretRow("호흡수", Interpretation.respiration(result.respirationBpm))

        Spacer(Modifier.height(16.dp))
        DisclaimerCard(stringResource(R.string.disclaimer_full))

        Spacer(Modifier.height(24.dp))
        Button(onClick = onRemeasure, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("다시 측정")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("완료")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WarnCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun InterpretRow(label: String, text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
