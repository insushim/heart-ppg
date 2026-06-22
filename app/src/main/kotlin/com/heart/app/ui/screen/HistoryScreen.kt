package com.heart.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heart.app.HeartApp
import com.heart.app.data.MeasurementEntity
import com.heart.app.ui.Lights
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { (context.applicationContext as HeartApp).database.measurementDao() }
    val flow = remember { dao.observeAll() }
    val items by flow.collectAsState(initial = emptyList())

    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("측정 기록", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("아직 측정 기록이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            BpmSparkline(items.map { it.bpm }.reversed())
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items) { e ->
                    HistoryRow(e, fmt.format(Date(e.timestamp)))
                    Divider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("뒤로")
        }
    }
}

@Composable
private fun HistoryRow(e: MeasurementEntity, dateText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("${e.bpm.toInt()} bpm", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(dateText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = when (e.autonomic) {
                "BALANCED" -> Lights.Good
                "MODERATE" -> Lights.Moderate
                "ELEVATED" -> Lights.Elevated
                else -> Lights.Unknown
            }
            Text("HRV ${e.rmssdMs.toInt()}ms", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(0.dp))
            Box(Modifier.padding(start = 10.dp).size(14.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun BpmSparkline(values: List<Double>) {
    if (values.size < 2) return
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(80.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 1e-3 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range).toFloat() * (size.height * 0.8f) - size.height * 0.1f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Lights.Good,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}
