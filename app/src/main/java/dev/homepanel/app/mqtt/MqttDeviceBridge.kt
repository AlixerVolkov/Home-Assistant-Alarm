package dev.homepanel.app.mqtt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.homepanel.app.BuildConfig
import dev.homepanel.app.data.PanelSettings
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONArray
import org.json.JSONObject

data class MqttDeviceState(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val brokerUri: String? = null,
    val deviceIdentifier: String? = null,
    val resolvedAddress: String? = null,
    val errorMessage: String? = null
)

private const val TCP_PROBE_TIMEOUT_MS = 5_000

private data class MqttConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val tls: Boolean
)

class MqttDeviceBridge(
    context: Context,
    private val onScreenCommand: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        ?.lowercase(Locale.US)
        ?.filter { it.isLetterOrDigit() }
        ?.takeIf { it.isNotBlank() }
        ?: "unknown"
    private val deviceIdentifier = "homepanel_$androidId"
    private val shortId = androidId.takeLast(8)
    private val baseTopic = "homepanel/$androidId"
    private val availabilityTopic = "$baseTopic/availability"
    private val screenCommandTopic = "$baseTopic/screen/set"

    private var client: MqttAsyncClient? = null
    private var activeConfig: MqttConfig? = null
    private var latestTelemetry: DeviceTelemetry? = null

    private val _state = MutableStateFlow(MqttDeviceState(deviceIdentifier = deviceIdentifier))
    val state: StateFlow<MqttDeviceState> = _state.asStateFlow()

    fun applySettings(settings: PanelSettings?) {
        if (settings?.mqttDiscoveryEnabled != true || settings.mqttHost.isBlank()) {
            client?.takeIf { it.isConnected }?.let { current -> runCatching { removeDiscovery(current) } }
            disconnect()
            _state.value = MqttDeviceState(enabled = false, deviceIdentifier = deviceIdentifier)
            return
        }

        val config = MqttConfig(
            host = normalizeHost(settings.mqttHost),
            port = settings.mqttPort.coerceIn(1, 65535),
            username = settings.mqttUsername.trim(),
            password = settings.mqttPassword,
            tls = settings.mqttTls
        )

        // Android 17 blocks direct LAN communication until ACCESS_LOCAL_NETWORK has been
        // granted. The v0.5.0 implementation could start MQTT before the permission dialog had
        // completed, then never retry after the user granted it. Avoid the misleading timeout.
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            disconnect()
            activeConfig = config
            _state.value = MqttDeviceState(
                enabled = true,
                connected = false,
                brokerUri = brokerUri(config, config.host),
                deviceIdentifier = deviceIdentifier,
                errorMessage = "Local network permission is required before MQTT can connect"
            )
            return
        }

        if (activeConfig == config && client?.isConnected == true) return

        scope.launch { connect(config) }
    }

    fun publishTelemetry(telemetry: DeviceTelemetry) {
        latestTelemetry = telemetry
        if (client?.isConnected != true) return
        scope.launch { publishTelemetryInternal(telemetry) }
    }

    fun disconnect() {
        val current = client
        client = null
        activeConfig = null
        if (current != null) {
            runCatching {
                if (current.isConnected) {
                    publish(current, availabilityTopic, "offline", retained = true)
                    current.disconnectForcibly(1_000L, 1_000L)
                }
                current.close()
            }
        }
        _state.value = _state.value.copy(connected = false)
    }

    fun close() {
        disconnect()
        scope.cancel()
    }

    private fun connect(config: MqttConfig) {
        disconnect()
        activeConfig = config

        runCatching {
            val resolved = resolvePreferredAddress(config.host)
            // Most Home Assistant/Mosquitto installations are LAN-only. Prefer IPv4 for plain
            // MQTT because some Android devices resolve the same host to an IPv6 ULA first even
            // when the broker is only reachable on IPv4. Keep the hostname for TLS so certificate
            // hostname validation is not broken.
            val connectionHost = if (!config.tls && resolved is Inet4Address) {
                resolved.hostAddress ?: config.host
            } else {
                config.host
            }
            val displayUri = brokerUri(config, connectionHost)
            val resolvedAddress = resolved?.hostAddress

            _state.value = MqttDeviceState(
                enabled = true,
                connected = false,
                brokerUri = displayUri,
                deviceIdentifier = deviceIdentifier,
                resolvedAddress = resolvedAddress
            )

            // Probe TCP first so a firewall/VLAN/closed-port problem is reported as such rather
            // than as the generic Paho "timed out waiting for a response" message.
            probeTcp(resolved ?: InetAddress.getByName(config.host), config.port)

            val mqttClient = MqttAsyncClient(
                displayUri,
                "homepanel-$shortId",
                MemoryPersistence()
            )
            client = mqttClient
            mqttClient.setCallback(callback)

            val options = MqttConnectOptions().apply {
                setAutomaticReconnect(true)
                setCleanSession(true)
                setConnectionTimeout(10)
                setKeepAliveInterval(30)
                if (config.username.isNotBlank()) setUserName(config.username)
                if (config.password.isNotBlank()) setPassword(config.password.toCharArray())
                setWill(availabilityTopic, "offline".toByteArray(Charsets.UTF_8), 1, true)
            }
            mqttClient.connect(options).waitForCompletion(12_000L)
            onConnected(mqttClient, displayUri)
        }.onFailure { error ->
            _state.value = _state.value.copy(
                connected = false,
                errorMessage = friendlyConnectionError(config, error)
            )
        }
    }

    private fun normalizeHost(raw: String): String {
        var value = raw.trim()
        listOf("mqtt://", "mqtts://", "tcp://", "ssl://", "http://", "https://").forEach { prefix ->
            if (value.startsWith(prefix, ignoreCase = true)) value = value.substring(prefix.length)
        }
        value = value.substringBefore('/')
        if (value.startsWith('[') && value.contains(']')) {
            return value.substringAfter('[').substringBefore(']')
        }
        // Strip an accidentally pasted port only for non-IPv6 host strings.
        if (value.count { it == ':' } == 1) {
            val maybePort = value.substringAfter(':')
            if (maybePort.all { it.isDigit() }) value = value.substringBefore(':')
        }
        return value
    }

    private fun resolvePreferredAddress(host: String): InetAddress? {
        val addresses = InetAddress.getAllByName(host).toList()
        return addresses.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?: addresses.firstOrNull { !it.isLoopbackAddress }
            ?: addresses.firstOrNull()
    }

    private fun brokerUri(config: MqttConfig, host: String): String {
        val scheme = if (config.tls) "ssl" else "tcp"
        val formattedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        return "$scheme://$formattedHost:${config.port}"
    }

    private fun probeTcp(address: InetAddress, port: Int) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), TCP_PROBE_TIMEOUT_MS)
        }
    }

    private fun friendlyConnectionError(config: MqttConfig, error: Throwable): String = when (error) {
        is UnknownHostException -> "MQTT host could not be resolved: ${config.host}"
        is SocketTimeoutException -> "TCP timeout to ${config.host}:${config.port}. Check broker port, VLAN/firewall and TLS setting."
        is SecurityException -> "Android blocked local-network access. Grant the Local network permission to HomePanel."
        else -> error.message ?: "Could not connect to MQTT broker ${config.host}:${config.port}"
    }

    private fun onConnected(mqttClient: MqttAsyncClient, brokerUri: String?) {
        if (client !== mqttClient || !mqttClient.isConnected) return
        _state.value = MqttDeviceState(
            enabled = true,
            connected = true,
            brokerUri = brokerUri ?: _state.value.brokerUri,
            deviceIdentifier = deviceIdentifier
        )
        runCatching {
            mqttClient.subscribe(screenCommandTopic, 1).waitForCompletion(5_000L)
            mqttClient.subscribe("homeassistant/status", 0).waitForCompletion(5_000L)
            publishDiscovery(mqttClient)
            publish(mqttClient, availabilityTopic, "online", retained = true)
            latestTelemetry?.let { publishTelemetryInternal(it) }
        }.onFailure { error ->
            _state.value = _state.value.copy(errorMessage = error.message)
        }
    }

    private val callback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            client?.let { current ->
                scope.launch { onConnected(current, serverURI) }
            }
        }

        override fun connectionLost(cause: Throwable?) {
            _state.value = _state.value.copy(
                connected = false,
                errorMessage = cause?.message
            )
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            val payload = message?.payload?.toString(Charsets.UTF_8)?.trim().orEmpty()
            when (topic) {
                screenCommandTopic -> when (payload.uppercase(Locale.US)) {
                    "ON" -> onScreenCommand(true)
                    "OFF" -> onScreenCommand(false)
                }
                "homeassistant/status" -> if (payload.equals("online", ignoreCase = true)) {
                    client?.let { current ->
                        scope.launch {
                            publishDiscovery(current)
                            publish(current, availabilityTopic, "online", retained = true)
                            latestTelemetry?.let { publishTelemetryInternal(it) }
                        }
                    }
                }
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    private fun publishDiscovery(mqttClient: MqttAsyncClient) {
        val device = JSONObject()
            .put("identifiers", JSONArray().put(deviceIdentifier))
            .put("name", "HomePanel ${Build.MODEL}")
            .put("manufacturer", Build.MANUFACTURER.ifBlank { "Android" })
            .put("model", Build.MODEL.ifBlank { "Android tablet" })
            .put("sw_version", BuildConfig.VERSION_NAME)

        discovery(
            mqttClient, "sensor", "battery",
            JSONObject()
                .put("name", "Battery")
                .put("unique_id", "${deviceIdentifier}_battery")
                .put("default_entity_id", "sensor.homepanel_battery")
                .put("state_topic", "$baseTopic/battery")
                .put("device_class", "battery")
                .put("unit_of_measurement", "%")
                .put("state_class", "measurement")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "sensor", "battery_temperature",
            JSONObject()
                .put("name", "Battery temperature")
                .put("unique_id", "${deviceIdentifier}_battery_temperature")
                .put("default_entity_id", "sensor.homepanel_battery_temperature")
                .put("state_topic", "$baseTopic/battery_temperature")
                .put("device_class", "temperature")
                .put("unit_of_measurement", "°C")
                .put("state_class", "measurement")
                .put("entity_category", "diagnostic")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "binary_sensor", "charging",
            JSONObject()
                .put("name", "Charging")
                .put("unique_id", "${deviceIdentifier}_charging")
                .put("default_entity_id", "binary_sensor.homepanel_charging")
                .put("state_topic", "$baseTopic/charging")
                .put("device_class", "battery_charging")
                .put("payload_on", "ON")
                .put("payload_off", "OFF")
                .put("entity_category", "diagnostic")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "binary_sensor", "proximity",
            JSONObject()
                .put("name", "Proximity")
                .put("unique_id", "${deviceIdentifier}_proximity")
                .put("default_entity_id", "binary_sensor.homepanel_proximity")
                .put("state_topic", "$baseTopic/proximity")
                .put("payload_on", "ON")
                .put("payload_off", "OFF")
                .put("icon", "mdi:motion-sensor")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "sensor", "illuminance",
            JSONObject()
                .put("name", "Ambient light")
                .put("unique_id", "${deviceIdentifier}_illuminance")
                .put("default_entity_id", "sensor.homepanel_illuminance")
                .put("state_topic", "$baseTopic/illuminance")
                .put("device_class", "illuminance")
                .put("unit_of_measurement", "lx")
                .put("state_class", "measurement")
                .put("icon", "mdi:brightness-6")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "switch", "screen",
            JSONObject()
                .put("name", "Screen")
                .put("unique_id", "${deviceIdentifier}_screen")
                .put("default_entity_id", "switch.homepanel_screen")
                .put("command_topic", screenCommandTopic)
                .put("state_topic", "$baseTopic/screen/state")
                .put("payload_on", "ON")
                .put("payload_off", "OFF")
                .put("icon", "mdi:tablet")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "sensor", "display_mode",
            JSONObject()
                .put("name", "Display mode")
                .put("unique_id", "${deviceIdentifier}_display_mode")
                .put("default_entity_id", "sensor.homepanel_display_mode")
                .put("state_topic", "$baseTopic/display_mode")
                .put("entity_category", "diagnostic")
                .put("icon", "mdi:monitor")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "binary_sensor", "rtsp",
            JSONObject()
                .put("name", "RTSP server")
                .put("unique_id", "${deviceIdentifier}_rtsp")
                .put("default_entity_id", "binary_sensor.homepanel_rtsp_server")
                .put("state_topic", "$baseTopic/rtsp/running")
                .put("device_class", "connectivity")
                .put("payload_on", "ON")
                .put("payload_off", "OFF")
                .put("entity_category", "diagnostic")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "sensor", "rtsp_clients",
            JSONObject()
                .put("name", "RTSP clients")
                .put("unique_id", "${deviceIdentifier}_rtsp_clients")
                .put("default_entity_id", "sensor.homepanel_rtsp_clients")
                .put("state_topic", "$baseTopic/rtsp/clients")
                .put("entity_category", "diagnostic")
                .put("icon", "mdi:account-multiple")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
        discovery(
            mqttClient, "sensor", "rtsp_url",
            JSONObject()
                .put("name", "Front camera RTSP URL")
                .put("unique_id", "${deviceIdentifier}_rtsp_url")
                .put("default_entity_id", "sensor.homepanel_front_camera_rtsp")
                .put("state_topic", "$baseTopic/rtsp/url")
                .put("entity_category", "diagnostic")
                .put("icon", "mdi:cctv")
                .put("availability_topic", availabilityTopic)
                .put("device", device)
        )
    }

    private fun removeDiscovery(mqttClient: MqttAsyncClient) {
        val entities = listOf(
            "sensor" to "battery",
            "sensor" to "battery_temperature",
            "binary_sensor" to "charging",
            "binary_sensor" to "proximity",
            "sensor" to "illuminance",
            "switch" to "screen",
            "sensor" to "display_mode",
            "binary_sensor" to "rtsp",
            "sensor" to "rtsp_clients",
            "sensor" to "rtsp_url"
        )
        entities.forEach { (platform, objectId) ->
            publish(mqttClient, "homeassistant/$platform/homepanel_$shortId/$objectId/config", "", retained = true)
        }
    }

    private fun discovery(
        mqttClient: MqttAsyncClient,
        platform: String,
        objectId: String,
        payload: JSONObject
    ) {
        publish(
            mqttClient,
            "homeassistant/$platform/homepanel_$shortId/$objectId/config",
            payload.toString(),
            retained = true
        )
    }

    private fun publishTelemetryInternal(telemetry: DeviceTelemetry) {
        val mqttClient = client ?: return
        if (!mqttClient.isConnected) return
        telemetry.batteryPercent?.let { publish(mqttClient, "$baseTopic/battery", it.toString(), true) }
        telemetry.batteryTemperatureC?.let {
            publish(mqttClient, "$baseTopic/battery_temperature", String.format(Locale.US, "%.1f", it), true)
        }
        telemetry.charging?.let { publish(mqttClient, "$baseTopic/charging", if (it) "ON" else "OFF", true) }
        telemetry.proximityNear?.let { publish(mqttClient, "$baseTopic/proximity", if (it) "ON" else "OFF", true) }
        telemetry.ambientLightLux?.let {
            publish(mqttClient, "$baseTopic/illuminance", String.format(Locale.US, "%.1f", it), true)
        }
        publish(mqttClient, "$baseTopic/display_mode", telemetry.displayMode, true)
        publish(mqttClient, "$baseTopic/screen/state", if (telemetry.displayMode == "sleep") "OFF" else "ON", true)
        publish(mqttClient, "$baseTopic/rtsp/running", if (telemetry.rtspRunning) "ON" else "OFF", true)
        publish(mqttClient, "$baseTopic/rtsp/clients", telemetry.rtspClients.toString(), true)
        publish(mqttClient, "$baseTopic/rtsp/url", telemetry.rtspUrl.orEmpty(), true)
        publish(mqttClient, availabilityTopic, "online", true)
    }

    private fun publish(
        mqttClient: MqttAsyncClient,
        topic: String,
        payload: String,
        retained: Boolean
    ) {
        if (!mqttClient.isConnected) return
        val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
            setQos(1)
            setRetained(retained)
        }
        mqttClient.publish(topic, message)
    }
}
