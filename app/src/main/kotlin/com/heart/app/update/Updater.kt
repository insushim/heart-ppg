package com.heart.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Latest-version descriptor published alongside the repo. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }
            conn.connect()
            val length = conn.contentLength
            val file = File(context.getExternalFilesDir(null), "heart-update.apk")
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

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
