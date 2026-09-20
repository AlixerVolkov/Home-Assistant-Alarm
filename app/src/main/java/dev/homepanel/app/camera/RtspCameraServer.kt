package dev.homepanel.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtspserver.RtspServerCamera2
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RtspCameraServer(private val context: Context) : ConnectChecker {
    private var server: RtspServerCamera2? = null
    private var activePort: Int = DEFAULT_PORT
    private var advertisedHostOverride: String = ""

    private val _state = MutableStateFlow(RtspCameraState())
    val state: StateFlow<RtspCameraState> = _state.asStateFlow()

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start(port: Int = DEFAULT_PORT, advertisedHost: String = "") {
        val safePort = port.coerceIn(1024, 65535)
        val normalizedOverride = advertisedHost.trim().removePrefix("rtsp://").substringBefore(':').trim('/')
        advertisedHostOverride = normalizedOverride

        if (!hasLocalNetworkPermission()) {
            val ipv4 = preferredIpv4Address()
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                endpoint = buildEndpoint(safePort),
                ipv4Address = ipv4?.first,
                interfaceName = ipv4?.second,
                errorMessage = "Local network permission is required for the RTSP server"
            )
            return
        }

        if (!hasCameraPermission()) {
            _state.value = RtspCameraState(
                enabled = true,
                permissionRequired = true,
                port = safePort,
                endpoint = buildEndpoint(safePort),
                ipv4Address = preferredIpv4Address()?.first,
                interfaceName = preferredIpv4Address()?.second,
                errorMessage = "Camera permission is required for the RTSP server"
            )
            return
        }

        if (server?.isStreaming == true && activePort == safePort) {
            refreshStats()
            return
        }

        stop()
        activePort = safePort

        runCatching {
            val cameraServer = RtspServerCamera2(context.applicationContext, this, safePort)
            cameraServer.streamClient.setOnlyVideo(true)
            cameraServer.setVideoCodec(VideoCodec.H264)
            cameraServer.startPreview(CameraHelper.Facing.FRONT)

            val rotation = CameraHelper.getCameraOrientation(context)
            val prepared = cameraServer.prepareVideo(
                DEFAULT_WIDTH,
                DEFAULT_HEIGHT,
                DEFAULT_FPS,
                DEFAULT_BITRATE,
                DEFAULT_IFRAME_INTERVAL,
                rotation
            )
            check(prepared) { "The device could not prepare the H.264 camera encoder" }

            server = cameraServer
            cameraServer.startStream()
            val ipv4 = preferredIpv4Address()
            _state.value = RtspCameraState(
                enabled = true,
                running = true,
                port = safePort,
                endpoint = buildEndpoint(safePort) ?: cameraServer.streamClient.getEndPointConnection(),
                ipv4Address = ipv4?.first,
                interfaceName = ipv4?.second,
                clientCount = cameraServer.streamClient.getNumClients()
            )
        }.onFailure { error ->
            runCatching { server?.stopStream() }
            server = null
            val ipv4 = preferredIpv4Address()
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                endpoint = buildEndpoint(safePort),
                ipv4Address = ipv4?.first,
                interfaceName = ipv4?.second,
                errorMessage = error.message ?: "Could not start the RTSP camera server"
            )
        }
    }

    @Synchronized
    fun stop() {
        val current = server
        server = null
        if (current != null) {
            runCatching { if (current.isStreaming) current.stopStream() }
        }
        val ipv4 = preferredIpv4Address()
        _state.value = RtspCameraState(
            enabled = false,
            port = activePort,
            endpoint = buildEndpoint(activePort),
            ipv4Address = ipv4?.first,
            interfaceName = ipv4?.second
        )
    }

    fun refreshStats() {
        val current = server ?: run {
            val ipv4 = preferredIpv4Address()
            _state.value = _state.value.copy(
                endpoint = buildEndpoint(activePort),
                ipv4Address = ipv4?.first,
                interfaceName = ipv4?.second
            )
            return
        }
        val ipv4 = preferredIpv4Address()
        _state.value = _state.value.copy(
            running = current.isStreaming,
            endpoint = buildEndpoint(activePort)
                ?: runCatching { current.streamClient.getEndPointConnection() }.getOrNull(),
            ipv4Address = ipv4?.first,
            interfaceName = ipv4?.second,
            clientCount = runCatching { current.streamClient.getNumClients() }.getOrDefault(0)
        )
    }

    fun frigateConfig(cameraName: String = "homepanel_front"): String? {
        val endpoint = _state.value.endpoint ?: return null
        return """go2rtc:
  streams:
    $cameraName: $endpoint

cameras:
  $cameraName:
    ffmpeg:
      inputs:
        - path: rtsp://127.0.0.1:8554/$cameraName
          input_args: preset-rtsp-restream
          roles:
            - detect
    detect:
      width: $DEFAULT_WIDTH
      height: $DEFAULT_HEIGHT
""".trim()
    }

    private fun buildEndpoint(port: Int): String? {
        advertisedHostOverride.takeIf { it.isNotBlank() }?.let { return "rtsp://$it:$port/" }
        return preferredIpv4Address()?.first?.let { "rtsp://$it:$port/" }
    }

    private fun preferredIpv4Address(): Pair<String, String>? {
        preferredIpv4FromActiveNetwork()?.let { return it }

        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
            .getOrNull().orEmpty()
            .filter { network -> runCatching { network.isUp && !network.isLoopback }.getOrDefault(false) }
            .sortedBy { network ->
                when {
                    network.name.startsWith("wlan", ignoreCase = true) -> 0
                    network.name.startsWith("eth", ignoreCase = true) -> 1
                    else -> 2
                }
            }

        interfaces.forEach { network ->
            val addresses = runCatching { Collections.list(network.inetAddresses) }.getOrDefault(emptyList())
            val candidate = addresses.filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                ?: addresses.filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            if (candidate != null) return candidate.hostAddress to network.name
        }
        return null
    }

    private fun preferredIpv4FromActiveNetwork(): Pair<String, String>? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        val properties = manager.getLinkProperties(network) ?: return null
        val address = properties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
            ?: properties.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?: return null
        return address.hostAddress to (properties.interfaceName ?: "active")
    }

    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() = refreshStats()
    override fun onNewBitrate(bitrate: Long) { _state.value = _state.value.copy(bitrateBps = bitrate) }
    override fun onConnectionFailed(reason: String) { _state.value = _state.value.copy(errorMessage = reason) }
    override fun onDisconnect() = refreshStats()
    override fun onAuthError() = Unit
    override fun onAuthSuccess() = Unit

    companion object {
        const val DEFAULT_PORT = 8554
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        private const val DEFAULT_FPS = 15
        private const val DEFAULT_BITRATE = 1_500_000
        private const val DEFAULT_IFRAME_INTERVAL = 2
    }
}

data class RtspCameraState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val permissionRequired: Boolean = false,
    val port: Int = RtspCameraServer.DEFAULT_PORT,
    val endpoint: String? = null,
    val ipv4Address: String? = null,
    val interfaceName: String? = null,
    val clientCount: Int = 0,
    val bitrateBps: Long = 0L,
    val errorMessage: String? = null
)
