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
    val codeFormat: String?,
    val changedBy: String? = null
) {
    fun supports(feature: Int): Boolean = supportedFeatures and feature != 0

    fun canPerform(action: AlarmAction): Boolean {
        val targetState = action.targetState
        if (targetState == state) return false

        return when (action) {
            AlarmAction.DISARM -> state != "disarmed" && state != "disarming"
            else -> state in STABLE_ARMABLE_STATES
        }
    }

    fun requiresCode(action: AlarmAction): Boolean = when (action) {
        AlarmAction.DISARM -> codeFormat != null
        else -> codeArmRequired
    }

    companion object {
        private val STABLE_ARMABLE_STATES = setOf(
            "disarmed",
            "armed_home",
            "armed_away",
            "armed_night",
            "armed_vacation",
            "armed_custom_bypass"
        )
    }
}

object AlarmFeatures {
    const val ARM_HOME = 1
    const val ARM_AWAY = 2
    const val ARM_NIGHT = 4
    const val TRIGGER = 8
    const val ARM_CUSTOM_BYPASS = 16
    const val ARM_VACATION = 32
}

enum class AlarmAction(
    val service: String,
    val targetState: String
) {
    DISARM("alarm_disarm", "disarmed"),
    ARM_HOME("alarm_arm_home", "armed_home"),
    ARM_AWAY("alarm_arm_away", "armed_away"),
    ARM_NIGHT("alarm_arm_night", "armed_night"),
    ARM_VACATION("alarm_arm_vacation", "armed_vacation"),
    ARM_CUSTOM_BYPASS("alarm_arm_custom_bypass", "armed_custom_bypass")
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
    val actionErrorMessage: String? = null,
    val pendingAction: AlarmAction? = null
)
