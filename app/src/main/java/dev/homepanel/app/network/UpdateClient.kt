package dev.homepanel.app.network

import android.content.Context
import androidx.core.content.FileProvider
import dev.homepanel.app.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latestVersion: String? = null,
    val available: Boolean = false,
    val releasePageUrl: String? = null,
    val apkDownloadUrl: String? = null,
    val releaseName: String? = null
)

class UpdateClient(
    private val context: Context,
    private val client: OkHttpClient
) {
    suspend fun checkLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/AlixerVolkov/Home-Assistant-Alarm/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HomePanel/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext UpdateInfo()
            if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}")
            val json = JSONObject(response.body.string())
            val tag = json.optString("tag_name").trim().removePrefix("v")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        if (name.contains("HomePanel", ignoreCase = true)) break
                    }
                }
            }
            UpdateInfo(
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = tag.takeIf { it.isNotBlank() },
                available = tag.isNotBlank() && compareVersions(tag, BuildConfig.VERSION_NAME) > 0,
                releasePageUrl = json.optString("html_url").takeIf { it.isNotBlank() },
                apkDownloadUrl = apkUrl,
                releaseName = json.optString("name").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun downloadApk(url: String): String = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val output = File(updatesDir, "HomePanel-update.apk")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "HomePanel/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Update download returned HTTP ${response.code}")
            val body = response.body
            output.outputStream().use { stream -> body.byteStream().copyTo(stream) }
        }
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            output
        ).toString()
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(left.size, right.size)
        repeat(size) { index ->
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }
}
