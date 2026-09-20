package dev.homepanel.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class HomeAssistantRestClient(
    private val client: OkHttpClient
) {
    suspend fun discoverPanelEntities(
        baseUrl: String,
        accessToken: String
    ): PanelDiscoveryResult = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(
            url = HomeAssistantUrl.restUrl(baseUrl, "api/states"),
            accessToken = accessToken
        ).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Home Assistant returned HTTP ${response.code}")
            }

            val states = JSONArray(response.body.string())
            val stateByEntityId = linkedMapOf<String, JSONObject>()
            for (index in 0 until states.length()) {
                val item = states.optJSONObject(index) ?: continue
                val entityId = item.optString("entity_id")
                if (entityId.isNotBlank()) stateByEntityId[entityId] = item
            }

            val alarms = mutableListOf<AlarmEntitySummary>()
            val wakeSensors = mutableListOf<WakeSensorSummary>()

            stateByEntityId.forEach { (entityId, item) ->
                val attributes = item.optJSONObject("attributes")
                val friendlyName = friendlyName(entityId, attributes)

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

            val guestWifi = stateByEntityId.entries.mapNotNull { (entityId, item) ->
                if (!entityId.startsWith("sensor.") || !entityId.endsWith("_voucher")) return@mapNotNull null

                val configKey = entityId.removePrefix("sensor.").removeSuffix("_voucher")
                val createButton = "button.${configKey}_create"
                if (!stateByEntityId.containsKey(createButton)) return@mapNotNull null

                val deleteCandidate = "button.${configKey}_delete"
                val qrCandidate = "image.${configKey}_qr_code"
                val attributes = item.optJSONObject("attributes")
                val wlanName = attributes?.optString("wlan_name")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val name = wlanName ?: friendlyName(entityId, attributes)

                GuestWifiSummary(
                    displayName = name,
                    voucherSensorEntityId = entityId,
                    createButtonEntityId = createButton,
                    deleteButtonEntityId = deleteCandidate.takeIf(stateByEntityId::containsKey),
                    // Keep the predictable entity id even when the image entity is disabled in HA.
                    // This lets the UI explain exactly which entity needs enabling.
                    qrImageEntityId = qrCandidate,
                    wlanName = wlanName
                )
            }.sortedBy { it.displayName.lowercase() }

            PanelDiscoveryResult(
                alarms = alarms.sortedBy { it.friendlyName.lowercase() },
                wakeSensors = wakeSensors.sortedBy { it.friendlyName.lowercase() },
                guestWifi = guestWifi
            )
        }
    }


    suspend fun fetchHouseSummary(
        baseUrl: String,
        accessToken: String
    ): HouseSummary = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(
            url = HomeAssistantUrl.restUrl(baseUrl, "api/states"),
            accessToken = accessToken
        ).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Home Assistant returned HTTP ${response.code}")
            val states = JSONArray(response.body.string())
            var openDoors = 0
            var openWindows = 0
            var lightsOn = 0
            var personsHome = 0
            val openNames = mutableListOf<String>()
            val temperatures = mutableListOf<Triple<String, String, Double>>()

            for (index in 0 until states.length()) {
                val item = states.optJSONObject(index) ?: continue
                val entityId = item.optString("entity_id")
                val state = item.optString("state").lowercase()
                if (state == "unavailable" || state == "unknown") continue
                val attributes = item.optJSONObject("attributes")
                val name = friendlyName(entityId, attributes)
                val domain = entityId.substringBefore('.', "")
                val deviceClass = attributes?.optString("device_class")?.lowercase().orEmpty()

                if (domain == "light" && state == "on") lightsOn++
                if (domain == "person" && state == "home") personsHome++

                if (domain == "binary_sensor" && state in setOf("on", "open")) {
                    when (deviceClass) {
                        "door", "garage_door", "opening" -> {
                            openDoors++
                            if (openNames.size < 5) openNames += name
                        }
                        "window" -> {
                            openWindows++
                            if (openNames.size < 5) openNames += name
                        }
                    }
                }

                if (domain == "sensor" && deviceClass == "temperature") {
                    val value = item.optString("state").replace(',', '.').toDoubleOrNull() ?: continue
                    val haystack = "$entityId $name".lowercase()
                    if (EXCLUDED_TEMPERATURE_HINTS.none { hint -> haystack.contains(hint) }) {
                        temperatures += Triple(entityId, name, value)
                    }
                }
            }

            val preferredTemperature = temperatures.minByOrNull { (entityId, name, _) ->
                val haystack = "$entityId $name".lowercase()
                when {
                    PREFERRED_TEMPERATURE_HINTS.any { hint -> haystack.contains(hint) } -> 0
                    else -> 1
                }
            }

            HouseSummary(
                openDoors = openDoors,
                openWindows = openWindows,
                lightsOn = lightsOn,
                personsHome = personsHome,
                temperatureC = preferredTemperature?.third,
                temperatureName = preferredTemperature?.second,
                openEntityNames = openNames
            )
        }
    }

    suspend fun fetchImage(
        baseUrl: String,
        accessToken: String,
        imageEntityId: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(
            url = HomeAssistantUrl.restUrl(baseUrl, "api/image_proxy/${imageEntityId.trim()}"),
            accessToken = accessToken
        ).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Home Assistant image returned HTTP ${response.code}")
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.startsWith("image/", ignoreCase = true)) {
                error("Home Assistant did not return an image")
            }
            response.body.bytes()
        }
    }

    private fun authenticatedRequest(url: HttpUrl, accessToken: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .header("Content-Type", "application/json")

    private fun friendlyName(entityId: String, attributes: JSONObject?): String =
        attributes?.optString("friendly_name")
            ?.takeIf { it.isNotBlank() }
            ?: entityId.substringAfter('.').replace('_', ' ')

    companion object {
        private val WAKE_DEVICE_CLASSES = setOf("motion", "occupancy", "presence")
        private val EXCLUDED_TEMPERATURE_HINTS = setOf(
            "battery", "cpu", "processor", "tablet", "phone", "device temperature", "gpu", "ssd"
        )
        private val PREFERRED_TEMPERATURE_HINTS = setOf(
            "indoor", "inside", "living", "salon", "woonkamer", "room", "interior", "temperature"
        )
    }
}
