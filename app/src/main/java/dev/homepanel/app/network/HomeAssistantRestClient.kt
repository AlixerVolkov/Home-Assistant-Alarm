package dev.homepanel.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

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
            val weatherEntities = mutableListOf<WeatherEntitySummary>()

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

                if (entityId.startsWith("weather.")) {
                    weatherEntities += WeatherEntitySummary(
                        entityId = entityId,
                        friendlyName = friendlyName,
                        condition = item.optString("state", "unknown")
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
                guestWifi = guestWifi,
                weatherEntities = weatherEntities.sortedBy { it.friendlyName.lowercase() }
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
            val persons = mutableListOf<PersonLocation>()
            val zones = mutableListOf<HomeZoneLocation>()
            val warnings = mutableListOf<WeatherWarning>()

            for (index in 0 until states.length()) {
                val item = states.optJSONObject(index) ?: continue
                val entityId = item.optString("entity_id")
                val rawState = item.optString("state")
                val state = rawState.lowercase()
                val attributes = item.optJSONObject("attributes")
                val name = friendlyName(entityId, attributes)
                val domain = entityId.substringBefore('.', "")
                val deviceClass = attributes?.optString("device_class")?.lowercase().orEmpty()

                if (domain == "zone") {
                    val latitude = attributes?.optDoubleOrNull("latitude")
                    val longitude = attributes?.optDoubleOrNull("longitude")
                    if (latitude != null && longitude != null) {
                        zones += HomeZoneLocation(
                            entityId = entityId,
                            friendlyName = name,
                            latitude = latitude,
                            longitude = longitude,
                            radiusMeters = attributes?.optDoubleOrNull("radius") ?: 100.0
                        )
                    }
                    continue
                }

                if (domain == "person") {
                    if (state == "home") personsHome++
                    persons += PersonLocation(
                        entityId = entityId,
                        friendlyName = name,
                        state = rawState.ifBlank { "unknown" },
                        latitude = attributes?.optDoubleOrNull("latitude"),
                        longitude = attributes?.optDoubleOrNull("longitude"),
                        gpsAccuracyMeters = attributes?.optDoubleOrNull("gps_accuracy"),
                        source = attributes?.optString("source")?.takeIf { it.isNotBlank() && it != "null" }
                    )
                }

                if (domain == "binary_sensor" && isWeatherWarningEntity(entityId, attributes)) {
                    val active = state !in INACTIVE_WARNING_STATES
                    warnings += WeatherWarning(
                        entityId = entityId,
                        friendlyName = name,
                        active = active,
                        severity = firstAttribute(attributes, "severity", "awareness_level", "level"),
                        title = firstAttribute(attributes, "headline", "event", "awareness_type", "title") ?: name,
                        description = firstAttribute(attributes, "description", "message", "instruction"),
                        expiresAt = firstAttribute(attributes, "expires", "end", "end_time")
                    )
                }

                if (state == "unavailable" || state == "unknown") continue

                if (domain == "light" && state == "on") lightsOn++

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

            val zoneByStateName = buildMap<String, HomeZoneLocation> {
                zones.forEach { zone ->
                    put(zone.friendlyName.lowercase(), zone)
                    put(zone.entityId.removePrefix("zone.").lowercase(), zone)
                    if (zone.entityId == "zone.home") put("home", zone)
                }
            }
            val resolvedPersons = persons.map { person ->
                if (person.latitude != null && person.longitude != null) return@map person
                val zone = zoneByStateName[person.state.lowercase()]
                if (zone != null) person.copy(latitude = zone.latitude, longitude = zone.longitude) else person
            }.sortedBy { it.friendlyName.lowercase() }

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
                openEntityNames = openNames,
                weatherWarnings = warnings.sortedWith(compareByDescending<WeatherWarning> { it.active }.thenBy { it.friendlyName.lowercase() }),
                persons = resolvedPersons,
                zones = zones.sortedBy { it.friendlyName.lowercase() }
            )
        }
    }

    suspend fun fetchWeatherForecast(
        baseUrl: String,
        accessToken: String,
        requestedEntityId: String?
    ): WeatherForecast = withContext(Dispatchers.IO) {
        val entityId = requestedEntityId?.trim()?.takeIf { it.isNotBlank() }
            ?: findFirstWeatherEntityId(baseUrl, accessToken)
            ?: error("No weather.* entity found in Home Assistant")

        val stateRequest = authenticatedRequest(
            url = HomeAssistantUrl.restUrl(baseUrl, "api/states/$entityId"),
            accessToken = accessToken
        ).get().build()

        val stateJson = client.newCall(stateRequest).execute().use { response ->
            if (!response.isSuccessful) error("Home Assistant weather state returned HTTP ${response.code}")
            JSONObject(response.body.string())
        }

        val attributes = stateJson.optJSONObject("attributes") ?: JSONObject()
        val friendly = friendlyName(entityId, attributes)
        val temperatureUnit = attributes.optString("temperature_unit", "°C")
        val windUnit = attributes.optString("wind_speed_unit", "km/h")
        val currentTemperatureRaw = attributes.optDoubleOrNull("temperature")
            ?: error("$entityId has no current temperature")
        val currentTemperature = convertTemperatureToC(currentTemperatureRaw, temperatureUnit)
        val apparent = convertTemperatureToC(
            attributes.optDoubleOrNull("apparent_temperature") ?: currentTemperatureRaw,
            temperatureUnit
        )
        val wind = convertWindToKmh(attributes.optDoubleOrNull("wind_speed") ?: 0.0, windUnit)
        val humidity = attributes.optDoubleOrNull("humidity")?.toInt()
        val pressure = attributes.optDoubleOrNull("pressure")
        val uvIndex = attributes.optDoubleOrNull("uv_index")
        val condition = stateJson.optString("state", "cloudy")

        val serviceUrl = HomeAssistantUrl.restUrl(baseUrl, "api/services/weather/get_forecasts")
            .newBuilder()
            .addQueryParameter("return_response", null)
            .build()
        val body = JSONObject()
            .put("entity_id", entityId)
            .put("type", "daily")
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val forecastRequest = authenticatedRequest(serviceUrl, accessToken)
            .post(body)
            .build()

        val responseJson = client.newCall(forecastRequest).execute().use { response ->
            if (!response.isSuccessful) {
                error("Home Assistant weather.get_forecasts returned HTTP ${response.code}")
            }
            JSONObject(response.body.string())
        }
        val serviceResponse = responseJson.optJSONObject("service_response") ?: JSONObject()
        val entityResponse = serviceResponse.optJSONObject(entityId)
            ?: error("Home Assistant returned no forecast for $entityId")
        val forecastArray = entityResponse.optJSONArray("forecast") ?: JSONArray()

        val days = buildList {
            val count = minOf(5, forecastArray.length())
            for (index in 0 until count) {
                val item = forecastArray.optJSONObject(index) ?: continue
                val highRaw = item.optDoubleOrNull("temperature") ?: 0.0
                val lowRaw = item.optDoubleOrNull("templow")
                    ?: item.optDoubleOrNull("temperature_low")
                    ?: highRaw
                val date = item.optString("datetime").take(10).ifBlank {
                    java.time.LocalDate.now().plusDays(index.toLong()).toString()
                }
                add(
                    DailyForecast(
                        date = date,
                        weatherCode = conditionToWeatherCode(item.optString("condition", condition)),
                        minimumC = convertTemperatureToC(lowRaw, temperatureUnit),
                        maximumC = convertTemperatureToC(highRaw, temperatureUnit),
                        precipitationProbability = item.optDoubleOrNull("precipitation_probability")?.toInt() ?: 0,
                        precipitationMm = item.optDoubleOrNull("precipitation"),
                        windSpeedKmh = item.optDoubleOrNull("wind_speed")?.let { convertWindToKmh(it, windUnit) }
                    )
                )
            }
        }

        WeatherForecast(
            current = CurrentWeather(
                temperatureC = currentTemperature,
                apparentTemperatureC = apparent,
                weatherCode = conditionToWeatherCode(condition),
                windSpeedKmh = wind,
                humidityPercent = humidity,
                pressureHpa = pressure,
                uvIndex = uvIndex
            ),
            daily = days,
            locationLabel = friendly,
            timezoneId = ZoneId.systemDefault().id,
            sourceLabel = "Home Assistant · $entityId"
        )
    }

    suspend fun probeHomeAssistant(baseUrl: String, accessToken: String): Long = withContext(Dispatchers.IO) {
        val started = android.os.SystemClock.elapsedRealtime()
        val request = authenticatedRequest(HomeAssistantUrl.restUrl(baseUrl, "api/"), accessToken).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Home Assistant returned HTTP ${response.code}")
        }
        android.os.SystemClock.elapsedRealtime() - started
    }

    private fun findFirstWeatherEntityId(baseUrl: String, accessToken: String): String? {
        val request = authenticatedRequest(HomeAssistantUrl.restUrl(baseUrl, "api/states"), accessToken).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Home Assistant returned HTTP ${response.code}")
            val states = JSONArray(response.body.string())
            buildList {
                for (index in 0 until states.length()) {
                    val entityId = states.optJSONObject(index)?.optString("entity_id").orEmpty()
                    if (entityId.startsWith("weather.")) add(entityId)
                }
            }.sorted().firstOrNull()
        }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.replace(',', '.')?.toDoubleOrNull()
    }

    private fun convertTemperatureToC(value: Double, unit: String): Double =
        if (unit.contains("F", ignoreCase = true)) (value - 32.0) * 5.0 / 9.0 else value

    private fun convertWindToKmh(value: Double, unit: String): Double = when (unit.lowercase()) {
        "mph", "mi/h" -> value * 1.609344
        "m/s" -> value * 3.6
        "kn", "kt", "kts" -> value * 1.852
        "ft/s" -> value * 1.09728
        "bft", "beaufort" -> if (value <= 0) 0.0 else 3.01 * Math.pow(value, 1.5)
        else -> value
    }

    private fun conditionToWeatherCode(condition: String): Int = when (condition.lowercase()) {
        "sunny", "clear-night" -> 0
        "partlycloudy" -> 2
        "cloudy", "windy", "windy-variant", "exceptional" -> 3
        "fog" -> 45
        "rainy" -> 61
        "pouring" -> 82
        "snowy" -> 71
        "snowy-rainy" -> 67
        "hail" -> 96
        "lightning", "lightning-rainy" -> 95
        else -> 3
    }

    private fun isWeatherWarningEntity(entityId: String, attributes: JSONObject?): Boolean {
        val id = entityId.lowercase()
        if (id.startsWith("binary_sensor.weather_warning") || id.startsWith("binary_sensor.meteoalarm")) return true
        if ("weather_warning" in id) return true
        return "meteoalarm" in id || attributes?.has("awareness_type") == true
    }

    private fun firstAttribute(attributes: JSONObject?, vararg names: String): String? {
        if (attributes == null) return null
        for (name in names) {
            val value = attributes.opt(name)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null" && value != "[]" && value != "{}") return value
        }
        return null
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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val WAKE_DEVICE_CLASSES = setOf("motion", "occupancy", "presence")
        private val INACTIVE_WARNING_STATES = setOf("off", "false", "0", "clear", "none", "unknown", "unavailable")
        private val EXCLUDED_TEMPERATURE_HINTS = setOf(
            "battery", "cpu", "processor", "tablet", "phone", "device temperature", "gpu", "ssd"
        )
        private val PREFERRED_TEMPERATURE_HINTS = setOf(
            "indoor", "inside", "living", "salon", "woonkamer", "room", "interior", "temperature"
        )
    }
}
