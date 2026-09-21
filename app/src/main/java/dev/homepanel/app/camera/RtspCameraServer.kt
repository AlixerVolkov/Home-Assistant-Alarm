package dev.homepanel.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtspserver.RtspServerCamera2
import dev.homepanel.app.network.LanNetworkHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RtspCameraServer(private val context: Context) : ConnectChecker {
    private val lanNetworkHelper = LanNetworkHelper(context)
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
        advertisedHostOverride = normalizeAdvertisedHost(advertisedHost)
        val lan = lanNetworkHelper.select()

        if (!hasLocalNetworkPermission()) {
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                endpoint = buildEndpoint(safePort),
                ipv4Address = lan?.ipv4Address,
                interfaceName = lan?.interfaceName,
                lanTransport = lan?.transport,
                errorMessage = "Local network permission is required for the RTSP server"
            )
            return
        }

        if (lan == null) {
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                errorMessage = "No Wi-Fi/Ethernet LAN network detected. RTSP needs a local interface reachable from Frigate."
            )
            return
        }

        if (!hasCameraPermission()) {
            _state.value = RtspCameraState(
                enabled = true,
                permissionRequired = true,
                port = safePort,
                endpoint = buildEndpoint(safePort),
                ipv4Address = lan.ipv4Address,
                interfaceName = lan.interfaceName,
                lanTransport = lan.transport,
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

            // RootEncoder requires prepareVideo before startStream. The old implementation
            // selected preview first and only then prepared the encoder, which is unreliable on
            // some Camera2 devices. Prepare first, then select the FRONT camera, then start.
            val rotation = CameraHelper.getCameraOrientation(context)
            var width = DEFAULT_WIDTH
            var height = DEFAULT_HEIGHT
            var prepared = cameraServer.prepareVideo(
                width,
                height,
                DEFAULT_FPS,
                DEFAULT_BITRATE,
                DEFAULT_IFRAME_INTERVAL,
                rotation
            )
            if (!prepared) {
                width = FALLBACK_WIDTH
                height = FALLBACK_HEIGHT
                prepared = cameraServer.prepareVideo(
                    width,
                    height,
                    FALLBACK_FPS,
                    FALLBACK_BITRATE,
                    DEFAULT_IFRAME_INTERVAL,
                    rotation
                )
            }
            check(prepared) { "The device could not prepare an H.264 encoder at 1280x720 or 640x480" }

            // With the Context/background constructor startPreview(FRONT) selects the camera;
            // startStream opens it and starts the encoder/server.
            cameraServer.startPreview(CameraHelper.Facing.FRONT)
            server = cameraServer
            cameraServer.startStream()

            val currentLan = lanNetworkHelper.select() ?: lan
            _state.value = RtspCameraState(
                enabled = true,
                running = cameraServer.isStreaming,
                port = safePort,
                endpoint = buildEndpoint(safePort) ?: cameraServer.streamClient.getEndPointConnection(),
                ipv4Address = currentLan.ipv4Address,
                interfaceName = currentLan.interfaceName,
                lanTransport = currentLan.transport,
                clientCount = cameraServer.streamClient.getNumClients(),
                width = width,
                height = height,
                errorMessage = null
            )
        }.onFailure { error ->
            runCatching { server?.let { if (it.isStreaming) it.stopStream() } }
            server = null
            val currentLan = lanNetworkHelper.select()
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                endpoint = buildEndpoint(safePort),
                ipv4Address = currentLan?.ipv4Address,
                interfaceName = currentLan?.interfaceName,
                lanTransport = currentLan?.transport,
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
            // Camera2Base in background mode closes the camera from stopStream. Keeping teardown
            // here centralized prevents stale encoder/camera instances across reconnect attempts.
        }
        val lan = lanNetworkHelper.select()
        _state.value = RtspCameraState(
            enabled = false,
            port = activePort,
            endpoint = buildEndpoint(activePort),
            ipv4Address = lan?.ipv4Address,
            interfaceName = lan?.interfaceName,
            lanTransport = lan?.transport
        )
    }

    fun refreshStats() {
        val lan = lanNetworkHelper.select()
        val current = server ?: run {
            _state.value = _state.value.copy(
                endpoint = buildEndpoint(activePort),
                ipv4Address = lan?.ipv4Address,
                interfaceName = lan?.interfaceName,
                lanTransport = lan?.transport
            )
            return
        }
        _state.value = _state.value.copy(
            running = current.isStreaming,
            endpoint = buildEndpoint(activePort)
                ?: runCatching { current.streamClient.getEndPointConnection() }.getOrNull(),
            ipv4Address = lan?.ipv4Address,
            interfaceName = lan?.interfaceName,
            lanTransport = lan?.transport,
            clientCount = runCatching { current.streamClient.getNumClients() }.getOrDefault(0)
        )
    }

    fun frigateConfig(cameraName: String = "homepanel_front"): String? {
        val endpoint = _state.value.endpoint ?: return null
        val width = _state.value.width.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val height = _state.value.height.takeIf { it > 0 } ?: DEFAULT_HEIGHT
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
      width: $width
      height: $height
""".trim()
    }

    private fun buildEndpoint(port: Int): String? {
        advertisedHostOverride.takeIf { it.isNotBlank() }?.let { return "rtsp://$it:$port/" }
        return lanNetworkHelper.select()?.ipv4Address?.let { "rtsp://$it:$port/" }
    }

    private fun normalizeAdvertisedHost(raw: String): String {
        var value = raw.trim().removePrefix("rtsp://")
        value = value.substringBefore('/')
        if (value.startsWith('[') && value.contains(']')) {
            return value.substringAfter('[').substringBefore(']')
        }
        if (value.count { it == ':' } == 1) {
            val maybePort = value.substringAfter(':')
            if (maybePort.all { it.isDigit() }) value = value.substringBefore(':')
        }
        return value.trim('/')
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
        private const val FALLBACK_WIDTH = 640
        private const val FALLBACK_HEIGHT = 480
        private const val FALLBACK_FPS = 12
        private const val FALLBACK_BITRATE = 800_000
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
    val lanTransport: String? = null,
    val clientCount: Int = 0,
    val bitrateBps: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val errorMessage: String? = null
)
