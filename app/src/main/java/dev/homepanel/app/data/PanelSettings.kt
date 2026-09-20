package dev.homepanel.app.data

data class PanelSettings(
    val baseUrl: String,
    val accessToken: String,
    val alarmEntityId: String,
    val wakeEntityId: String? = null,
    val screensaverTimeoutMinutes: Int = 2,
    val sleepTimeoutMinutes: Int = 10,
    val proximityWakeEnabled: Boolean = true,
    val rtspEnabled: Boolean = false,
    val rtspPort: Int = 8554,
    val guestVoucherSensorEntityId: String? = null,
    val guestCreateButtonEntityId: String? = null,
    val guestQrImageEntityId: String? = null
)
