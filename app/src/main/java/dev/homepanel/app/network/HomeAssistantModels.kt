package dev.homepanel.app.network

data class AlarmEntitySummary(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val supportedFeatures: Int,
    val codeArmRequired: Boolean,
    val codeFormat: String?
)

data class WakeSensorSummary(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val deviceClass: String?
)


data class WeatherEntitySummary(
    val entityId: String,
    val friendlyName: String,
    val condition: String
)

data class GuestWifiSummary(
    val displayName: String,
    val voucherSensorEntityId: String,
    val createButtonEntityId: String,
    val deleteButtonEntityId: String?,
    val qrImageEntityId: String?,
    val wlanName: String?
)

data class PanelDiscoveryResult(
    val alarms: List<AlarmEntitySummary>,
    val wakeSensors: List<WakeSensorSummary>,
    val guestWifi: List<GuestWifiSummary> = emptyList(),
    val weatherEntities: List<WeatherEntitySummary> = emptyList()
)

data class AlarmEntityState(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val supportedFeatures: Int,
    val codeArmRequired: Boolean,
    val codeFormat: String?,
    val changedBy: String? = null,
    val isAlarmo: Boolean = false,
    val openSensors: List<String> = emptyList()
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


data class AlarmBypassSensor(
    val entityId: String,
    val friendlyName: String
)

data class AlarmBypassRequest(
    val action: AlarmAction,
    val sensors: List<AlarmBypassSensor>,
    val preflight: Boolean = false
)

data class GuestVoucherState(
    val entityId: String,
    val code: String,
    val wlanName: String? = null,
    val duration: String? = null,
    val status: String? = null,
    val note: String? = null
)

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
    val targetState: String,
    val alarmoMode: String?
) {
    DISARM("alarm_disarm", "disarmed", null),
    ARM_HOME("alarm_arm_home", "armed_home", "home"),
    ARM_AWAY("alarm_arm_away", "armed_away", "away"),
    ARM_NIGHT("alarm_arm_night", "armed_night", "night"),
    ARM_VACATION("alarm_arm_vacation", "armed_vacation", "vacation"),
    ARM_CUSTOM_BYPASS("alarm_arm_custom_bypass", "armed_custom_bypass", "custom")
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
    val guestVoucher: GuestVoucherState? = null,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val guestErrorMessage: String? = null,
    val pendingAction: AlarmAction? = null,
    val bypassRequest: AlarmBypassRequest? = null,
    val readyToArmModes: Map<String, Boolean> = emptyMap(),
    val pendingGuestVoucher: Boolean = false,
    val pendingGuestVoucherDelete: Boolean = false
)

data class HomeEntityEvent(
    val entityId: String,
    val friendlyName: String,
    val state: String,
    val deviceClass: String? = null
)
