package dev.homepanel.app.camera

/** Lightweight state model intentionally kept separate from the RTSP implementation.
 * This lets HomePanel run with RTSP disabled without loading the camera/encoder classes.
 */
data class RtspCameraState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val permissionRequired: Boolean = false,
    val port: Int = 8554,
    val endpoint: String? = null,
    val ipv4Address: String? = null,
    val interfaceName: String? = null,
    val lanTransport: String? = null,
    val clientCount: Int = 0,
    val bitrateBps: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val backend: String = "Not loaded",
    val safetyBlocked: Boolean = false,
    val errorMessage: String? = null
)
