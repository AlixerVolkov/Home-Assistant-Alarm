package dev.homepanel.app.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.homepanel.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

class OsmTileClient(context: Context) {
    private val client = sharedClient(context.applicationContext)

    suspend fun loadTile(zoom: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
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
            BitmapFactory.decodeStream(response.body.byteStream())
        }
    }

    companion object {
        @Volatile
        private var cachedClient: OkHttpClient? = null

        private fun sharedClient(context: Context): OkHttpClient =
            cachedClient ?: synchronized(this) {
                cachedClient ?: OkHttpClient.Builder()
                    .cache(Cache(File(context.cacheDir, "osm_tiles"), 64L * 1024L * 1024L))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
                    .also { cachedClient = it }
            }
    }
}
