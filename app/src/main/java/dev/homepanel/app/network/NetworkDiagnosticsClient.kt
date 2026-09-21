package dev.homepanel.app.network

import android.content.Context
import android.os.SystemClock
import dev.homepanel.app.data.PanelSettings
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DiagnosticStatus {
    RUNNING,
    OK,
    FAILED,
    SKIPPED
}

data class NetworkDiagnosticItem(
    val key: String,
    val status: DiagnosticStatus,
    val detail: String,
    val latencyMs: Long? = null
)

data class NetworkDiagnosticsState(
    val isRunning: Boolean = false,
    val items: List<NetworkDiagnosticItem> = emptyList()
)

class NetworkDiagnosticsClient(
    context: Context,
    private val client: OkHttpClient,
    private val restClient: HomeAssistantRestClient,
    private val weatherClient: WeatherClient
) {
    private val lanHelper = LanNetworkHelper(context)

    suspend fun run(settings: PanelSettings, onItem: (NetworkDiagnosticItem) -> Unit) {
        val lan = lanHelper.select()
        onItem(
            if (lan == null) {
                NetworkDiagnosticItem("lan", DiagnosticStatus.FAILED, "No Wi-Fi/Ethernet LAN found")
            } else {
                NetworkDiagnosticItem(
                    "lan",
                    DiagnosticStatus.OK,
                    "${lan.transport} · ${lan.interfaceName.orEmpty()} · ${lan.ipv4Address ?: "no IPv4"}"
                )
            }
        )

        onItem(probe("home_assistant") { restClient.probeHomeAssistant(settings.baseUrl, settings.accessToken) })

        onItem(probe("weather_ha") {
            val started = SystemClock.elapsedRealtime()
            restClient.fetchWeatherForecast(settings.baseUrl, settings.accessToken, settings.weatherEntityId)
            SystemClock.elapsedRealtime() - started
        })
        onItem(probe("open_meteo") { weatherClient.probe() })

        onItem(probe("github") { probeHttps("https://api.github.com/repos/AlixerVolkov/Home-Assistant-Alarm/releases/latest") })

        if (settings.mqttDiscoveryEnabled && settings.mqttHost.isNotBlank()) {
            onItem(probe("mqtt") { probeTcp(settings.mqttHost, settings.mqttPort) })
        } else {
            onItem(NetworkDiagnosticItem("mqtt", DiagnosticStatus.SKIPPED, "MQTT disabled"))
        }

        if (settings.rtspEnabled) {
            onItem(probe("rtsp") { probeLocalTcp(settings.rtspPort) })
        } else {
            onItem(NetworkDiagnosticItem("rtsp", DiagnosticStatus.SKIPPED, "RTSP disabled"))
        }
    }

    private suspend fun probe(key: String, block: suspend () -> Long): NetworkDiagnosticItem =
        runCatching {
            val elapsed = block()
            NetworkDiagnosticItem(key, DiagnosticStatus.OK, "OK", elapsed)
        }.getOrElse { error ->
            NetworkDiagnosticItem(
                key,
                DiagnosticStatus.FAILED,
                "${error.javaClass.simpleName}: ${error.message.orEmpty()}".trim()
            )
        }

    private suspend fun probeHttps(url: String): Long = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
        }
        SystemClock.elapsedRealtime() - started
    }

    private suspend fun probeTcp(host: String, port: Int): Long = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val lan = lanHelper.select()
        val addresses = lanHelper.resolve(host, lan)
            .sortedBy { if (it is Inet4Address) 0 else 1 }
        var lastError: Throwable? = null
        for (address in addresses) {
            val socket = runCatching {
                lan?.network?.socketFactory?.createSocket() ?: Socket()
            }.getOrElse { Socket() }
            try {
                socket.use { it.connect(InetSocketAddress(address, port), 4_000) }
                return@withContext SystemClock.elapsedRealtime() - started
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Could not resolve $host")
    }

    private suspend fun probeLocalTcp(port: Int): Long = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
        }
        SystemClock.elapsedRealtime() - started
    }
}
