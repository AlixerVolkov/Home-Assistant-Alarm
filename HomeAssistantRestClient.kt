package dev.homepanel.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class HomeAssistantRestClient(
    private val client: OkHttpClient
) {
    suspend fun discoverAlarmEntities(
        baseUrl: String,
        accessToken: String
    ): List<AlarmEntitySummary> = withContext(Dispatchers.IO) {
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

            val body = response.body.string()
            val states = JSONArray(body)
            buildList {
                for (index in 0 until states.length()) {
                    val item = states.getJSONObject(index)
                    val entityId = item.optString("entity_id")
                    if (!entityId.startsWith("alarm_control_panel.")) continue

                    val attributes = item.optJSONObject("attributes")
                    add(
                        AlarmEntitySummary(
                            entityId = entityId,
                            friendlyName = attributes?.optString("friendly_name")
                                ?.takeIf { it.isNotBlank() }
                                ?: entityId.substringAfter('.').replace('_', ' '),
                            state = item.optString("state", "unknown"),
                            supportedFeatures = attributes?.optInt("supported_features", 0) ?: 0,
                            codeArmRequired = attributes?.optBoolean("code_arm_required", true) ?: true,
                            codeFormat = attributes?.optString("code_format")
                                ?.takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }.sortedBy { it.friendlyName.lowercase() }
        }
    }
}
