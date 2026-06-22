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
import androidx.compose.material3.Divider
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
                Text(qualityLabel(result.quality), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
            }
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
        Divider()
        MetricRow("심박변이도 (RMSSD)", "${result.rmssdMs.toInt()} ms")
        MetricRow("SDNN", "${result.sdnnMs.toInt()} ms")
        MetricRow("pNN50", "${(result.pnn50 * 100).toInt()} %")
        MetricRow("관류지수 (PI)", String.format("%.1f %%", result.perfusionIndex))
        result.respirationBpm?.let {
            MetricRow("호흡수", "${it.toInt()} 회/분", hint = "안정 시 추정")
        }
        MetricRow("신호 SNR", String.format("%.1f dB", result.snrDb))
        Divider()

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

private fun qualityLabel(q: SignalQuality): String = when (q) {
    SignalQuality.GOOD -> "신호 양호"
    SignalQuality.FAIR -> "신호 보통"
    SignalQuality.POOR -> "신호 미흡 — 재측정 권장"
}
