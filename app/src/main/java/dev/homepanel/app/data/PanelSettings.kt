package dev.homepanel.app.data

data class PanelSettings(
    val baseUrl: String,
    val accessToken: String,
    val alarmEntityId: String,
    val wakeEntityId: String? = null,
    val screensaverTimeoutMinutes: Int = 2,
    val sleepTimeoutMinutes: Int = 10
)
