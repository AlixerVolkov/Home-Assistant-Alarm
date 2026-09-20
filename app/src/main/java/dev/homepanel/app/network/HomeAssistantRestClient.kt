package dev.homepanel.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class HomeAssistantRestClient(
    private val client: OkHttpClient
) {
    suspend fun discoverPanelEntities(
        baseUrl: String,
        accessToken: String
    ): PanelDiscoveryResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(HomeAssistantUrl.restUrl(baseUrl, "api/states"))
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .header("Content-Type", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Home Assistant returned HTTP ${response.code}")
            }

            val states = JSONArray(response.body.string())
            val alarms = mutableListOf<AlarmEntitySummary>()
            val wakeSensors = mutableListOf<WakeSensorSummary>()

            for (index in 0 until states.length()) {
                val item = states.getJSONObject(index)
                val entityId = item.optString("entity_id")
                val attributes = item.optJSONObject("attributes")
                val friendlyName = attributes?.optString("friendly_name")
                    ?.takeIf { it.isNotBlank() }
                    ?: entityId.substringAfter('.').replace('_', ' ')

                if (entityId.startsWith("alarm_control_panel.")) {
                    alarms += AlarmEntitySummary(
                        entityId = entityId,
                        friendlyName = friendlyName,
                        state = item.optString("state", "unknown"),
                        supportedFeatures = attributes?.optInt("supported_features", 0) ?: 0,
                        codeArmRequired = attributes?.optBoolean("code_arm_required", true) ?: true,
                        codeFormat = attributes?.optString("code_format")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                    )
                }

                if (entityId.startsWith("binary_sensor.")) {
                    val deviceClass = attributes?.optString("device_class")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                    if (deviceClass in WAKE_DEVICE_CLASSES) {
                        wakeSensors += WakeSensorSummary(
                            entityId = entityId,
                            friendlyName = friendlyName,
                            state = item.optString("state", "unknown"),
                            deviceClass = deviceClass
                        )
                    }
                }
            }

            PanelDiscoveryResult(
                alarms = alarms.sortedBy { it.friendlyName.lowercase() },
                wakeSensors = wakeSensors.sortedBy { it.friendlyName.lowercase() }
            )
        }
    }

    companion object {
        private val WAKE_DEVICE_CLASSES = setOf("motion", "occupancy", "presence")
    }
}
