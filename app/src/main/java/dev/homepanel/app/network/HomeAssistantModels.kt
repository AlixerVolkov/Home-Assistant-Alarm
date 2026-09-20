package dev.homepanel.app.network

data class AlarmEntitySummary(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val supportedFeatures: Int,
    val codeArmRequired: Boolean,
    val codeFormat: String?
)

data class AlarmEntityState(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val supportedFeatures: Int,
    val codeArmRequired: Boolean,
    val codeFormat: String?
) {
    fun supports(feature: Int): Boolean = supportedFeatures and feature != 0
}

object AlarmFeatures {
    const val ARM_HOME = 1
    const val ARM_AWAY = 2
    const val ARM_NIGHT = 4
    const val TRIGGER = 8
    const val ARM_CUSTOM_BYPASS = 16
    const val ARM_VACATION = 32
}

enum class AlarmAction(val service: String) {
    DISARM("alarm_disarm"),
    ARM_HOME("alarm_arm_home"),
    ARM_AWAY("alarm_arm_away"),
    ARM_NIGHT("alarm_arm_night"),
    ARM_VACATION("alarm_arm_vacation"),
    ARM_CUSTOM_BYPASS("alarm_arm_custom_bypass")
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class HomeAssistantConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val alarm: AlarmEntityState? = null,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null
)
