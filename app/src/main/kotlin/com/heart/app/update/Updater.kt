package com.heart.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Latest-version descriptor published alongside the repo. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/** Outcome of an update attempt, surfaced to the UI. */
sealed interface InstallResult {
    /** The system installer was launched. */
    data object Launched : InstallResult
    /** "Install unknown apps" permission is missing; settings were opened. */
    data object NeedsPermission : InstallResult
    data class Failed(val message: String) : InstallResult
}

/**
 * Self-update for the side-loaded (non-Play) build: the app reads a small version.json
 * from the repo, and if a newer versionCode is available, downloads the APK and launches
 * the system installer (the final "install" tap is required by Android for sideloads).
 */
object Updater {

    private const val VERSION_URL =
        "https://raw.githubusercontent.com/insushim/heart-ppg/main/version.json"

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val o = JSONObject(text)
            UpdateInfo(o.getInt("versionCode"), o.getString("versionName"), o.getString("apkUrl"))
        }.getOrNull()
    }

    /** Download the APK to app-private external storage and launch the installer. */
    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): InstallResult {
        val file = File(context.getExternalFilesDir(null), "heart-update.apk")
        // 1) Download on IO.
        val dl = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                conn.connect()
                val length = conn.contentLength
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        var total = 0
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            total += read
                            if (length > 0) onProgress((total * 100L / length).toInt())
                        }
                    }
                }
                conn.disconnect()
            }
        }
        dl.onFailure { return InstallResult.Failed("다운로드 실패: ${it.message ?: "네트워크 오류"}") }

        // 2) Launch the installer on the MAIN thread (launching UI from a background
        //    thread is unreliable and was the cause of the "stuck at 100%" stall).
        return withContext(Dispatchers.Main) {
            // 3) Android 8+ requires per-app "install unknown apps" permission. Without it
            //    the install is silently blocked; send the user to grant it first.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return@withContext InstallResult.NeedsPermission
            }
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                InstallResult.Launched
            }.getOrElse { InstallResult.Failed("설치 실행 실패: ${it.message ?: "알 수 없음"}") }
        }
    }
}
