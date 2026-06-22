package com.heart.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heart.app.BuildConfig
import kotlinx.coroutines.launch

/**
 * Checks for a newer published version on launch and offers an in-app update.
 * Renders nothing when the app is already up to date.
 */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val latest = Updater.fetchLatest() ?: return@LaunchedEffect
        if (latest.versionCode > BuildConfig.VERSION_CODE) update = latest
    }

    val info = update ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("새 버전 ${info.versionName} 사용 가능", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                "현재 ${BuildConfig.VERSION_NAME}. 업데이트하면 개선/수정이 적용됩니다.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            message?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(8.dp))
            }
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("다운로드 중… $progress%", fontSize = 12.sp)
            } else {
                Button(
                    onClick = {
                        downloading = true
                        message = null
                        progress = 0
                        scope.launch {
                            when (val r = Updater.downloadAndInstall(context, info.apkUrl) { progress = it }) {
                                is InstallResult.Launched ->
                                    message = "설치 화면이 열렸습니다. ‘설치’를 눌러 완료하세요."
                                is InstallResult.NeedsPermission ->
                                    message = "‘이 출처 허용’을 켠 뒤 다시 ‘지금 업데이트’를 눌러주세요."
                                is InstallResult.Failed ->
                                    message = r.message
                            }
                            downloading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { Text(if (message == null) "지금 업데이트" else "다시 시도") }
            }
        }
    }
}
