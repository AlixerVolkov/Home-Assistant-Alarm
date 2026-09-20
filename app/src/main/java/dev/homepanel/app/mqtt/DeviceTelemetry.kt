package dev.homepanel.app.mqtt

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class DeviceTelemetry(
    val batteryPercent: Int? = null,
    val batteryTemperatureC: Double? = null,
    val charging: Boolean? = null,
    val proximityNear: Boolean? = null,
    val displayMode: String = "active",
    val rtspRunning: Boolean = false,
    val rtspClients: Int = 0,
    val rtspUrl: String? = null
)

class DeviceTelemetryReader(private val context: Context) {
    fun read(
        proximityNear: Boolean?,
        displayMode: String,
        rtspRunning: Boolean,
        rtspClients: Int,
        rtspUrl: String?
    ): DeviceTelemetry {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        val rawTemperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temperature = rawTemperature.takeIf { it != Int.MIN_VALUE }?.div(10.0)
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status.takeIf { it >= 0 }?.let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        }

        return DeviceTelemetry(
            batteryPercent = percent,
            batteryTemperatureC = temperature,
            charging = charging,
            proximityNear = proximityNear,
            displayMode = displayMode,
            rtspRunning = rtspRunning,
            rtspClients = rtspClients,
            rtspUrl = rtspUrl
        )
    }
}
