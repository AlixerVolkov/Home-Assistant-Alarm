package dev.homepanel.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtspserver.RtspServerCamera1
import dev.homepanel.app.network.LanNetworkHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * RTSP server for the tablet front camera.
 *
 * v0.6.3 deliberately uses the Camera1 backend. Camera2 is more modern, but a subset of Android
 * devices/drivers can terminate or deadlock the process while opening Camera2 from a background
 * stream. Camera1 is deprecated at API level but is still available on our minSdk and is a much
 * safer compatibility backend for an always-on wall panel.
 *
 * Camera work is serialized off the UI thread and a small crash guard prevents an RTSP startup
 * crash from turning into an endless app-crash loop on the next launch.
 */
class RtspCameraServer(private val context: Context) : ConnectChecker {
    private val appContext = context.applicationContext
    private val lanNetworkHelper = LanNetworkHelper(appContext)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HomePanel-RTSP").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val operationGeneration = AtomicLong(0L)
    private val safetyPrefs = appContext.getSharedPreferences(SAFETY_PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    @Volatile private var desiredEnabled = false
    private var server: RtspServerCamera1? = null
    private var activePort: Int = DEFAULT_PORT
    private var advertisedHostOverride: String = ""

    private val _state = MutableStateFlow(RtspCameraState())
    val state: StateFlow<RtspCameraState> = _state.asStateFlow()

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED

    fun start(port: Int = DEFAULT_PORT, advertisedHost: String = "") {
        val safePort = port.coerceIn(1024, 65535)
        desiredEnabled = true
        advertisedHostOverride = normalizeAdvertisedHost(advertisedHost)
        activePort = safePort
        val generation = operationGeneration.incrementAndGet()
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
                backend = BACKEND_NAME,
                errorMessage = "Local network permission is required for the RTSP server"
            )
            return
        }

        if (lan == null) {
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                backend = BACKEND_NAME,
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
                backend = BACKEND_NAME,
                errorMessage = "Camera permission is required for the RTSP server"
            )
            return
        }

        if (safetyPrefs.getBoolean(KEY_START_PENDING, false)) {
            _state.value = RtspCameraState(
                enabled = true,
                running = false,
                port = safePort,
                endpoint = buildEndpoint(safePort),
                ipv4Address = lan.ipv4Address,
                interfaceName = lan.interfaceName,
                lanTransport = lan.transport,
                backend = BACKEND_NAME,
                safetyBlocked = true,
                errorMessage = "RTSP safe mode blocked automatic start after an interrupted camera startup. Turn RTSP off, save, then enable it again to retry."
            )
            return
        }

        _state.value = _state.value.copy(
            enabled = true,
            running = false,
            permissionRequired = false,
            port = safePort,
            endpoint = buildEndpoint(safePort),
            ipv4Address = lan.ipv4Address,
            interfaceName = lan.interfaceName,
            lanTransport = lan.transport,
            backend = BACKEND_NAME,
            safetyBlocked = false,
            errorMessage = null
        )

        worker.execute {
            if (!desiredEnabled || generation != operationGeneration.get()) return@execute
            startInternal(safePort, generation)
        }
    }

    private fun startInternal(safePort: Int, generation: Long) {
        synchronized(lock) {
            if (!desiredEnabled || generation != operationGeneration.get()) return

            val existing = server
            if (existing?.isStreaming == true && activePort == safePort) {
                refreshStatsInternal()
                return
            }

            stopServerLocked(clearSafety = false)
            activePort = safePort

            // If the process dies while the camera/encoder is being opened, this flag survives.
            // On the next launch HomePanel will not immediately repeat the crashing path.
            safetyPrefs.edit()
                .putBoolean(KEY_START_PENDING, true)
                .putLong(KEY_START_PENDING_AT, System.currentTimeMillis())
                .apply()

            try {
                val cameraServer = RtspServerCamera1(appContext, this, safePort)
                cameraServer.streamClient.setOnlyVideo(true)
                cameraServer.setVideoCodec(VideoCodec.H264)

                val rotation = CameraHelper.getCameraOrientation(appContext)

                // Camera1 can expose a different set of supported sizes on each device. Do not
                // blindly request 1280x720: startStream may throw CameraOpenException when that
                // size is not a valid camera mode even though the H.264 encoder can encode it.
                // RootEncoder exposes the actual front-camera sizes, so select from that list.
                val supportedResolutions = runCatching {
                    cameraServer.getResolutionsFront()
                        .map { it.width to it.height }
                        .filter { (w, h) -> w > 0 && h > 0 }
                        .distinct()
                }.getOrDefault(emptyList())

                val selected = selectResolution(supportedResolutions)
                    ?: (FALLBACK_WIDTH to FALLBACK_HEIGHT)
                var width = selected.first
                var height = selected.second

                var prepared = runCatching {
                    cameraServer.prepareVideo(
                        width,
                        height,
                        FPS_FOR(width, height),
                        BITRATE_FOR(width, height),
                        DEFAULT_IFRAME_INTERVAL,
                        rotation
                    )
                }.getOrDefault(false)

                // Some older drivers report a camera size that the MediaCodec encoder cannot
                // accept. Try the remaining supported camera sizes, largest/closest first.
                if (!prepared) {
                    val alternatives = supportedResolutions
                        .filterNot { it == selected }
                        .sortedWith(resolutionComparator())
                    for ((candidateWidth, candidateHeight) in alternatives) {
                        val candidatePrepared = runCatching {
                            cameraServer.prepareVideo(
                                candidateWidth,
                                candidateHeight,
                                FPS_FOR(candidateWidth, candidateHeight),
                                BITRATE_FOR(candidateWidth, candidateHeight),
                                DEFAULT_IFRAME_INTERVAL,
                                rotation
                            )
                        }.getOrDefault(false)
                        if (candidatePrepared) {
                            width = candidateWidth
                            height = candidateHeight
                            prepared = true
                            break
                        }
                    }
                }

                check(prepared) {
                    if (supportedResolutions.isEmpty()) {
                        "The front camera did not report any supported resolutions"
                    } else {
                        "The device could not prepare H.264 at any supported front-camera resolution: " +
                            supportedResolutions.joinToString { "${it.first}x${it.second}" }
                    }
                }

                // In background mode startPreview selects the requested camera facing. Actual
                // capture begins when startStream opens the camera. The selected size is now a
                // size reported by that camera, avoiding CameraOpenException on unsupported modes.
                cameraServer.startPreview(CameraHelper.Facing.FRONT, width, height)

                if (!desiredEnabled || generation != operationGeneration.get()) {
                    runCatching { if (cameraServer.isStreaming) cameraServer.stopStream() }
                    clearStartGuard()
                    return
                }

                server = cameraServer
                cameraServer.startStream()

                val currentLan = lanNetworkHelper.select()
                _state.value = RtspCameraState(
                    enabled = true,
                    running = cameraServer.isStreaming,
                    port = safePort,
                    endpoint = buildEndpoint(safePort)
                        ?: runCatching { cameraServer.streamClient.getEndPointConnection() }.getOrNull(),
                    ipv4Address = currentLan?.ipv4Address,
                    interfaceName = currentLan?.interfaceName,
                    lanTransport = currentLan?.transport,
                    clientCount = runCatching { cameraServer.streamClient.getNumClients() }.getOrDefault(0),
                    width = width,
                    height = height,
                    backend = BACKEND_NAME,
                    errorMessage = null
                )

                // Do not clear immediately: some camera-driver failures happen just after
                // startStream returns. If the app survives this window, startup is considered safe.
                mainHandler.postDelayed({
                    if (desiredEnabled && generation == operationGeneration.get()) clearStartGuard()
                }, START_GUARD_CLEAR_DELAY_MS)
            } catch (t: Throwable) {
                runCatching { server?.let { if (it.isStreaming) it.stopStream() } }
                server = null
                clearStartGuard()
                val currentLan = lanNetworkHelper.select()
                _state.value = RtspCameraState(
                    enabled = true,
                    running = false,
                    port = safePort,
                    endpoint = buildEndpoint(safePort),
                    ipv4Address = currentLan?.ipv4Address,
                    interfaceName = currentLan?.interfaceName,
                    lanTransport = currentLan?.transport,
                    backend = BACKEND_NAME,
                    errorMessage = "RTSP camera failed: ${t.javaClass.simpleName}: ${t.message ?: "unknown error"}"
                )
            }
        }
    }

    fun stop() {
        desiredEnabled = false
        val generation = operationGeneration.incrementAndGet()
        // Clearing this flag is deliberate: toggling RTSP off is how the user acknowledges a
        // safety block and allows a later manual retry.
        clearStartGuard()
        worker.execute {
            synchronized(lock) {
                if (generation != operationGeneration.get()) return@synchronized
                stopServerLocked(clearSafety = true)
                val lan = lanNetworkHelper.select()
                _state.value = RtspCameraState(
                    enabled = false,
                    port = activePort,
                    endpoint = buildEndpoint(activePort),
                    ipv4Address = lan?.ipv4Address,
                    interfaceName = lan?.interfaceName,
                    lanTransport = lan?.transport,
                    backend = BACKEND_NAME
                )
            }
        }
    }

    private fun stopServerLocked(clearSafety: Boolean) {
        val current = server
        server = null
        if (current != null) {
            runCatching { if (current.isStreaming) current.stopStream() }
        }
        if (clearSafety) clearStartGuard()
    }

    fun refreshStats() {
        worker.execute { refreshStatsInternal() }
    }

    private fun refreshStatsInternal() {
        synchronized(lock) {
            val lan = lanNetworkHelper.select()
            val current = server
            if (current == null) {
                _state.value = _state.value.copy(
                    endpoint = buildEndpoint(activePort),
                    ipv4Address = lan?.ipv4Address,
                    interfaceName = lan?.interfaceName,
                    lanTransport = lan?.transport,
                    backend = BACKEND_NAME
                )
                return
            }
            _state.value = _state.value.copy(
                running = runCatching { current.isStreaming }.getOrDefault(false),
                endpoint = buildEndpoint(activePort)
                    ?: runCatching { current.streamClient.getEndPointConnection() }.getOrNull(),
                ipv4Address = lan?.ipv4Address,
                interfaceName = lan?.interfaceName,
                lanTransport = lan?.transport,
                clientCount = runCatching { current.streamClient.getNumClients() }.getOrDefault(0),
                backend = BACKEND_NAME
            )
        }
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

    fun shutdown() {
        desiredEnabled = false
        operationGeneration.incrementAndGet()
        clearStartGuard()
        synchronized(lock) { stopServerLocked(clearSafety = true) }
        worker.shutdownNow()
    }

    private fun clearStartGuard() {
        safetyPrefs.edit()
            .remove(KEY_START_PENDING)
            .remove(KEY_START_PENDING_AT)
            .apply()
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
        const val BACKEND_NAME = "Camera1 compatibility"
        private const val DEFAULT_FPS = 15
        private const val DEFAULT_BITRATE = 1_500_000
        private const val FALLBACK_WIDTH = 640
        private const val FALLBACK_HEIGHT = 480
        private const val FALLBACK_FPS = 12
        private const val FALLBACK_BITRATE = 800_000

        /**
         * Prefer 16:9 modes near 720p, but never invent a mode that the camera did not report.
         * This is intentionally independent of encoder capability; prepareVideo is still tested
         * for every candidate because old devices can report camera modes their H.264 encoder
         * cannot consume.
         */
        private fun selectResolution(
            resolutions: List<Pair<Int, Int>>
        ): Pair<Int, Int>? = resolutions
            .filter { (w, h) -> w <= DEFAULT_WIDTH && h <= DEFAULT_HEIGHT }
            .minWithOrNull(resolutionComparator())

        private fun resolutionComparator(): Comparator<Pair<Int, Int>> =
            compareBy<Pair<Int, Int>>(
                { aspectDistance(it) },
                { distanceFromTarget(it) }
            ).thenByDescending { it.first * it.second }

        private fun aspectDistance(resolution: Pair<Int, Int>): Double {
            val (w, h) = resolution
            return kotlin.math.abs(w.toDouble() / h.toDouble() - 16.0 / 9.0)
        }

        private fun distanceFromTarget(resolution: Pair<Int, Int>): Int =
            kotlin.math.abs(DEFAULT_WIDTH - resolution.first) +
                kotlin.math.abs(DEFAULT_HEIGHT - resolution.second)

        private fun FPS_FOR(width: Int, height: Int): Int =
            if (width * height <= FALLBACK_WIDTH * FALLBACK_HEIGHT) FALLBACK_FPS else DEFAULT_FPS

        private fun BITRATE_FOR(width: Int, height: Int): Int =
            if (width * height <= FALLBACK_WIDTH * FALLBACK_HEIGHT) FALLBACK_BITRATE else DEFAULT_BITRATE
        private const val DEFAULT_IFRAME_INTERVAL = 2
        private const val START_GUARD_CLEAR_DELAY_MS = 8_000L
        private const val SAFETY_PREFS = "rtsp_safety"
        private const val KEY_START_PENDING = "start_pending"
        private const val KEY_START_PENDING_AT = "start_pending_at"
    }
}
