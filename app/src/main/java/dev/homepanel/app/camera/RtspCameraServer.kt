package dev.homepanel.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtspserver.RtspServerCamera2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Small lifecycle wrapper around RootEncoder's RTSP-Server plugin.
 *
 * HomePanel uses the background Camera2 constructor because the tablet is a wall panel and we do
 * not need a local camera preview. Audio is deliberately disabled: this avoids microphone
 * permissions and makes the feature a privacy-friendlier front-camera video feed.
 */
class RtspCameraServer(private val context: Context) : ConnectChecker {
    private var server: RtspServerCamera2? = null
    private var activePort: Int = DEFAULT_PORT

    private val _state = MutableStateFlow(RtspCameraState())
    val state: StateFlow<RtspCameraState> = _state.asStateFlow()

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start(port: Int = DEFAULT_PORT) {
        val safePort = port.coerceIn(1024, 65535)
        if (!hasCameraPermission()) {
            _state.value = RtspCameraState(
                enabled = true,
                permissionRequired = true,
                port = safePort,
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

            // In Camera2 background mode startPreview does not render a preview; it selects which
            // camera will be opened by startStream().
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
            val endpoint = preferredIpv4Endpoint(safePort)
                ?: cameraServer.streamClient.getEndPointConnection()
            _state.value = RtspCameraState(
                enabled = true,
                running = true,
                port = safePort,
                endpoint = endpoint,
                clientCount = cameraServer.streamClient.getNumClients()
            )
        }.onFailure { error ->
            runCatching { server?.stopStream() }
            server = null
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                errorMessage = error.message ?: "Could not start the RTSP camera server"
            )
        }
    }

    @Synchronized
    fun stop() {
        val current = server
        server = null
        if (current != null) {
            runCatching {
                if (current.isStreaming) current.stopStream()
            }
        }
        _state.value = RtspCameraState(enabled = false, port = activePort)
    }

    fun refreshStats() {
        val current = server ?: return
        _state.value = _state.value.copy(
            running = current.isStreaming,
            endpoint = preferredIpv4Endpoint(activePort)
                ?: runCatching { current.streamClient.getEndPointConnection() }.getOrNull(),
            clientCount = runCatching { current.streamClient.getNumClients() }.getOrDefault(0)
        )
    }

    /**
     * RootEncoder can report an IPv6 ULA first on dual-stack Wi-Fi networks. The RTSP server
     * itself listens on the device, so expose an IPv4 LAN address when one exists; this is easier
     * to consume from Frigate/go2rtc and avoids malformed unbracketed IPv6 RTSP URLs.
     */
    private fun preferredIpv4Endpoint(port: Int): String? {
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
            .getOrNull()
            .orEmpty()
            .filter { network ->
                runCatching { network.isUp && !network.isLoopback }.getOrDefault(false)
            }
            .sortedBy { network ->
                when {
                    network.name.startsWith("wlan", ignoreCase = true) -> 0
                    network.name.startsWith("eth", ignoreCase = true) -> 1
                    else -> 2
                }
            }

        val candidates = interfaces.flatMap { network ->
            runCatching { Collections.list(network.inetAddresses) }.getOrDefault(emptyList())
        }.filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }

        val preferred = candidates.firstOrNull { it.isSiteLocalAddress } ?: candidates.firstOrNull()
        return preferred?.hostAddress?.let { "rtsp://$it:$port/" }
    }

    override fun onConnectionStarted(url: String) = Unit

    override fun onConnectionSuccess() {
        refreshStats()
    }

    override fun onNewBitrate(bitrate: Long) {
        _state.value = _state.value.copy(bitrateBps = bitrate)
    }

    override fun onConnectionFailed(reason: String) {
        _state.value = _state.value.copy(errorMessage = reason)
    }

    override fun onDisconnect() {
        refreshStats()
    }

    override fun onAuthError() = Unit
    override fun onAuthSuccess() = Unit

    companion object {
        const val DEFAULT_PORT = 8554
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
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
    val clientCount: Int = 0,
    val bitrateBps: Long = 0L,
    val errorMessage: String? = null
)
