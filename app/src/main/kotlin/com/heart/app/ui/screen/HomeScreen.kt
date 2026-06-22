package com.heart.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heart.app.R
import com.heart.app.ui.DisclaimerCard

@Composable
fun HomeScreen(onMeasure: () -> Unit, onHistory: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Heart", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("카메라로 심박·컨디션 측정", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(20.dp))
        DisclaimerCard(stringResource(R.string.disclaimer_full))

        Spacer(Modifier.height(24.dp))
        Text("측정 가능한 항목", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        listOf(
            "❤️  심박수 (bpm)",
            "📈  심박변이도 HRV (RMSSD)",
            "🚦  자율신경 컨디션 (신호등)",
            "🌬️  호흡수 (조건 충족 시)",
            "💧  관류지수 (신호 품질)",
            "⚠️  불규칙 맥박 스크리닝",
        ).forEach { Text(it, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp)) }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onMeasure,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("측정 시작", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("측정 기록 / 추세", fontSize = 16.sp)
        }
    }
}
