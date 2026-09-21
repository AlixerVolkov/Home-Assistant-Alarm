package dev.homepanel.app.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.homepanel.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Small OpenStreetMap tile client with a persistent on-device cache.
 *
 * Online mode downloads only tiles that are actually visible and stores them in filesDir.
 * Offline mode never performs network I/O and renders only tiles that have already been viewed.
 * This intentionally avoids bulk/offline prefetching against the public OSM tile service.
 */
class OsmTileClient(context: Context) {
    private val appContext = context.applicationContext
    private val client = sharedClient()
    private val tileRoot = File(appContext.filesDir, "osm_tiles").apply { mkdirs() }

    suspend fun loadTile(
        zoom: Int,
        x: Int,
        y: Int,
        allowNetwork: Boolean = true
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = tileFile(zoom, x, y)
        if (file.isFile) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { return@withContext it }
            runCatching { file.delete() }
        }

        if (!allowNetwork) return@withContext null

        val url = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "HomePanel/${BuildConfig.VERSION_NAME} (+https://github.com/AlixerVolkov/Home-Assistant-Alarm)"
            )
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bytes = response.body.bytes()
            if (bytes.isEmpty()) return@withContext null

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            runCatching {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeBytes(bytes)
                if (!temp.renameTo(file)) {
                    file.writeBytes(bytes)
                    temp.delete()
                }
            }
            bitmap
        }
    }

    fun hasCachedTile(zoom: Int, x: Int, y: Int): Boolean = tileFile(zoom, x, y).isFile

    private fun tileFile(zoom: Int, x: Int, y: Int): File =
        File(File(File(tileRoot, zoom.toString()), x.toString()), "$y.png")

    companion object {
        @Volatile
        private var cachedClient: OkHttpClient? = null

        private fun sharedClient(): OkHttpClient =
            cachedClient ?: synchronized(this) {
                cachedClient ?: OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
                    .also { cachedClient = it }
            }
    }
}
