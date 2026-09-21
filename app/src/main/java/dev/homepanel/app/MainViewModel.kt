package dev.homepanel.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.homepanel.app.camera.RtspCameraServer
import dev.homepanel.app.camera.RtspCameraState
import dev.homepanel.app.data.EventHistoryRepository
import dev.homepanel.app.data.PanelEvent
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.data.SettingsRepository
import dev.homepanel.app.diagnostics.CrashLogStore
import dev.homepanel.app.network.AlarmAction
import dev.homepanel.app.network.AlarmEntitySummary
import dev.homepanel.app.network.ConnectionStatus
import dev.homepanel.app.network.DeviceLocation
import dev.homepanel.app.network.DeviceLocationResolver
import dev.homepanel.app.network.GuestWifiSummary
import dev.homepanel.app.network.HomeAssistantRestClient
import dev.homepanel.app.network.HouseSummary
import dev.homepanel.app.network.NetworkDiagnosticItem
import dev.homepanel.app.network.NetworkDiagnosticsClient
import dev.homepanel.app.network.NetworkDiagnosticsState
import dev.homepanel.app.network.HomeAssistantWebSocket
import dev.homepanel.app.network.WakeSensorSummary
import dev.homepanel.app.network.WeatherEntitySummary
import dev.homepanel.app.network.WeatherClient
import dev.homepanel.app.network.WeatherForecast
import dev.homepanel.app.network.UpdateClient
import dev.homepanel.app.network.UpdateInfo
import dev.homepanel.app.mqtt.DeviceTelemetryReader
import dev.homepanel.app.mqtt.MqttDeviceBridge
import dev.homepanel.app.mqtt.MqttDeviceState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class DiscoveryState(
    val hasRun: Boolean = false,
    val isLoading: Boolean = false,
    val alarms: List<AlarmEntitySummary> = emptyList(),
    val wakeSensors: List<WakeSensorSummary> = emptyList(),
    val guestWifi: List<GuestWifiSummary> = emptyList(),
    val weatherEntities: List<WeatherEntitySummary> = emptyList(),
    val errorMessage: String? = null
)

data class WeatherUiState(
    val isLoading: Boolean = false,
    val forecast: WeatherForecast? = null,
    val errorMessage: String? = null
)

data class LocationUiState(
    val isLoading: Boolean = false,
    val location: DeviceLocation? = null,
    val errorMessage: String? = null
)

data class GuestWifiUiState(
    val isLoadingQr: Boolean = false,
    val qrImageBytes: ByteArray? = null,
    val errorMessage: String? = null
)

data class HouseSummaryUiState(
    val isLoading: Boolean = false,
    val summary: HouseSummary? = null,
    val errorMessage: String? = null
)

data class UpdateUiState(
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val info: UpdateInfo? = null,
    val installerUri: String? = null,
    val errorMessage: String? = null
)

enum class PanelDisplayMode {
    ACTIVE,
    SCREENSAVER,
    SLEEP
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val settingsRepository = SettingsRepository(application)
    private val restClient = HomeAssistantRestClient(httpClient)
    private val socketClient = HomeAssistantWebSocket(httpClient)
    private val weatherClient = WeatherClient(httpClient)
    private val networkDiagnosticsClient = NetworkDiagnosticsClient(application, httpClient, restClient, weatherClient)
    private val locationResolver = DeviceLocationResolver(application)
    private val telemetryReader = DeviceTelemetryReader(application)
    private val historyRepository = EventHistoryRepository(application)
    private val updateClient = UpdateClient(application, httpClient)
    private val safeMode = CrashLogStore.isSafeMode()

    // v0.6.4: optional integrations are genuinely lazy. With RTSP/MQTT disabled their
    // libraries are not instantiated during normal app startup. This isolates vendor camera
    // and MQTT stacks from the core alarm panel and makes startup crashes diagnosable.
    private var rtspCameraServer: RtspCameraServer? = null
    private var rtspStateJob: Job? = null
    private val _rtspCamera = MutableStateFlow(RtspCameraState())

    private var mqttDeviceBridge: MqttDeviceBridge? = null
    private var mqttStateJob: Job? = null
    private val _mqttDevice = MutableStateFlow(MqttDeviceState())

    private val _settings = MutableStateFlow<PanelSettings?>(null)
    val settings: StateFlow<PanelSettings?> = _settings.asStateFlow()

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _discovery = MutableStateFlow(DiscoveryState())
    val discovery: StateFlow<DiscoveryState> = _discovery.asStateFlow()

    private val _weather = MutableStateFlow(WeatherUiState())
    val weather: StateFlow<WeatherUiState> = _weather.asStateFlow()

    private val _location = MutableStateFlow(LocationUiState())
    val location: StateFlow<LocationUiState> = _location.asStateFlow()

    private val _guestWifi = MutableStateFlow(GuestWifiUiState())
    val guestWifi: StateFlow<GuestWifiUiState> = _guestWifi.asStateFlow()

    private val _houseSummary = MutableStateFlow(HouseSummaryUiState())
    val houseSummary: StateFlow<HouseSummaryUiState> = _houseSummary.asStateFlow()

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    private val _networkDiagnostics = MutableStateFlow(NetworkDiagnosticsState())
    val networkDiagnostics: StateFlow<NetworkDiagnosticsState> = _networkDiagnostics.asStateFlow()

    private val _ambientLux = MutableStateFlow<Float?>(null)
    val ambientLux: StateFlow<Float?> = _ambientLux.asStateFlow()

    val history: StateFlow<List<PanelEvent>> = historyRepository.events

    private val _displayMode = MutableStateFlow(PanelDisplayMode.ACTIVE)
    val displayMode: StateFlow<PanelDisplayMode> = _displayMode.asStateFlow()

    private val _wakePulse = MutableStateFlow(0L)
    val wakePulse: StateFlow<Long> = _wakePulse.asStateFlow()

    val connection = socketClient.state
    val rtspCamera: StateFlow<RtspCameraState> = _rtspCamera.asStateFlow()
    val mqttDevice: StateFlow<MqttDeviceState> = _mqttDevice.asStateFlow()

    private var lastActivityElapsed = SystemClock.elapsedRealtime()
    private var lastGuestVoucherCode: String? = null
    private var historyGuestVoucherCode: String? = null
    private var lastAlarmHistoryState: String? = null
    private var lastConnectionHistoryStatus: ConnectionStatus? = null
    private var localProximityNear: Boolean? = null
    private var mqttForcedSleep = false
    private var lastAutomaticUpdateCheckElapsed = 0L
    private var lastActiveWeatherWarningIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                _settings.value = current
                _settingsLoaded.value = true
                userActivity()
                applyRtspSettings(current)
                applyMqttSettings(current)
                if (current != null && !_editing.value) {
                    connect(current)
                    loadHouseSummary(showSpinner = _houseSummary.value.summary == null)
                    if (current.weatherSource == WEATHER_SOURCE_HOME_ASSISTANT) {
                        viewModelScope.launch { loadWeather(showSpinner = _weather.value.forecast == null) }
                    }
                    if (!safeMode && current.updateChecksEnabled &&
                        (lastAutomaticUpdateCheckElapsed == 0L ||
                            SystemClock.elapsedRealtime() - lastAutomaticUpdateCheckElapsed > UPDATE_CHECK_INTERVAL_MS)
                    ) {
                        lastAutomaticUpdateCheckElapsed = SystemClock.elapsedRealtime()
                        checkForUpdates(showSpinner = false)
                    }
                } else if (current == null) {
                    socketClient.disconnect()
                }
            }
        }

        viewModelScope.launch {
            socketClient.wakeEvents.collect {
                wakeDisplay()
            }
        }

        viewModelScope.launch {
            while (isActive) {
                runCatching { updateDisplayMode() }
                val server = rtspCameraServer
                if (!safeMode && _settings.value?.rtspEnabled == true && server != null) {
                    runCatching { server.refreshStats() }
                }
                delay(1_000L)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                runCatching { publishDeviceTelemetry() }
                delay(5_000L)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                val current = _settings.value
                if (current?.weatherSource == WEATHER_SOURCE_HOME_ASSISTANT || _location.value.location != null) {
                    loadWeather(showSpinner = _weather.value.forecast == null)
                }
                delay(WEATHER_REFRESH_INTERVAL_MS)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                if (_settings.value != null && !_editing.value) {
                    loadHouseSummary(showSpinner = _houseSummary.value.summary == null)
                }
                delay(HOUSE_SUMMARY_REFRESH_INTERVAL_MS)
            }
        }

        viewModelScope.launch {
            socketClient.state.collect { state ->
                if (state.status == ConnectionStatus.ERROR || state.status == ConnectionStatus.DISCONNECTED) {
                    delay(RECONNECT_DELAY_MS)
                    val currentSettings = _settings.value
                    val currentStatus = socketClient.state.value.status
                    if (currentSettings != null &&
                        !_editing.value &&
                        (currentStatus == ConnectionStatus.ERROR || currentStatus == ConnectionStatus.DISCONNECTED)
                    ) {
                        connect(currentSettings)
                    }
                }
            }
        }

        viewModelScope.launch {
            socketClient.entityEvents.collect { event ->
                val normalized = event.state.lowercase()
                val message = when {
                    event.entityId.startsWith("light.") -> "${event.friendlyName}: ${if (normalized == "on") "on" else "off"}"
                    event.entityId.startsWith("person.") -> "${event.friendlyName}: $normalized"
                    event.deviceClass in setOf("door", "garage_door", "opening") ->
                        "${event.friendlyName}: ${if (normalized in setOf("on", "open")) "open" else "closed"}"
                    event.deviceClass == "window" ->
                        "${event.friendlyName}: ${if (normalized in setOf("on", "open")) "open" else "closed"}"
                    event.deviceClass in setOf("motion", "occupancy") ->
                        "${event.friendlyName}: ${if (normalized == "on") "detected" else "clear"}"
                    else -> "${event.friendlyName}: $normalized"
                }
                historyRepository.add("entity", message)
                loadHouseSummary(showSpinner = false)
            }
        }

        viewModelScope.launch {
            socketClient.state.collect { state ->
                if (state.status != lastConnectionHistoryStatus) {
                    if (lastConnectionHistoryStatus != null) {
                        historyRepository.add("connection", "Home Assistant: ${state.status.name.lowercase()}")
                    }
                    lastConnectionHistoryStatus = state.status
                }

                val alarmState = state.alarm?.state
                if (!alarmState.isNullOrBlank() && alarmState != lastAlarmHistoryState) {
                    historyRepository.add("alarm", "Alarm: ${alarmState.replace('_', ' ')}")
                    lastAlarmHistoryState = alarmState
                }

                val code = state.guestVoucher?.code?.takeIf { it.isNotBlank() }
                if (code != historyGuestVoucherCode) {
                    when {
                        code != null -> historyRepository.add("guest", "Guest Wi-Fi voucher created")
                        historyGuestVoucherCode != null -> historyRepository.add("guest", "Guest Wi-Fi voucher removed")
                    }
                    historyGuestVoucherCode = code
                }
            }
        }

        viewModelScope.launch {
            socketClient.state.collect { state ->
                val code = state.guestVoucher?.code?.takeIf { it.isNotBlank() }
                if (code != null && code != lastGuestVoucherCode) {
                    lastGuestVoucherCode = code
                    // Give the image entity a moment to regenerate its QR after the voucher changes.
                    delay(700L)
                    refreshGuestQr(showSpinner = false)
                }
            }
        }
    }

    fun hasLocationPermission(): Boolean = locationResolver.hasLocationPermission()
    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication<Application>(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun isSafeMode(): Boolean = safeMode

    fun onCameraPermissionResult(granted: Boolean) {
        val current = _settings.value ?: return
        if (!safeMode && granted && current.rtspEnabled) {
            runCatching { ensureRtspServer().start(current.rtspPort, current.rtspAdvertisedHost) }
                .onFailure { setRtspStartupError(it) }
        }
    }

    fun onLocalNetworkPermissionResult(granted: Boolean) {
        val current = _settings.value ?: return
        if (!granted) {
            if (current.mqttDiscoveryEnabled) {
                _mqttDevice.value = MqttDeviceState(
                    enabled = true,
                    errorMessage = "Local network permission is required before MQTT can connect"
                )
            }
            if (current.rtspEnabled) {
                _rtspCamera.value = _rtspCamera.value.copy(
                    enabled = true,
                    running = false,
                    errorMessage = "Local network permission is required for the RTSP server"
                )
            }
            return
        }
        applyMqttSettings(current)
        connect(current)
        if (!safeMode && current.rtspEnabled) {
            runCatching {
                val server = ensureRtspServer()
                server.stop()
                server.start(current.rtspPort, current.rtspAdvertisedHost)
            }.onFailure { setRtspStartupError(it) }
        }
    }

    fun onAmbientLightChanged(lux: Float) {
        // Light sensors can emit dozens of callbacks per second. Keep only the latest reading;
        // the normal 5-second telemetry loop publishes it to MQTT without flooding Paho.
        _ambientLux.value = lux.coerceAtLeast(0f)
    }

    fun checkForUpdates(showSpinner: Boolean = true) {
        if (_update.value.isLoading || _update.value.isDownloading) return
        viewModelScope.launch {
            if (showSpinner) _update.value = _update.value.copy(isLoading = true, errorMessage = null)
            runCatching { updateClient.checkLatest() }
                .onSuccess { info -> _update.value = UpdateUiState(info = info) }
                .onFailure { error ->
                    _update.value = _update.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Could not check for updates"
                    )
                }
        }
    }

    fun downloadUpdate() {
        val url = _update.value.info?.apkDownloadUrl ?: run {
            _update.value = _update.value.copy(errorMessage = "The GitHub release has no APK asset")
            return
        }
        if (_update.value.isDownloading) return
        viewModelScope.launch {
            _update.value = _update.value.copy(isDownloading = true, errorMessage = null, installerUri = null)
            runCatching { updateClient.downloadApk(url) }
                .onSuccess { uri ->
                    _update.value = _update.value.copy(isDownloading = false, installerUri = uri)
                    historyRepository.add("update", "HomePanel update downloaded")
                }
                .onFailure { error ->
                    _update.value = _update.value.copy(
                        isDownloading = false,
                        errorMessage = error.message ?: "Could not download update"
                    )
                }
        }
    }

    fun consumeInstallerUri() {
        _update.value = _update.value.copy(installerUri = null)
    }

    fun clearHistory() = historyRepository.clear()

    fun refreshHouseSummary() {
        userActivity()
        loadHouseSummary(showSpinner = _houseSummary.value.summary == null)
    }

    fun refreshDeviceLocation() {
        if (!locationResolver.hasLocationPermission()) {
            _location.value = LocationUiState(errorMessage = "Location permission is required for automatic weather")
            return
        }
        if (_location.value.isLoading) return

        viewModelScope.launch {
            _location.value = _location.value.copy(isLoading = true, errorMessage = null)
            try {
                val resolved = locationResolver.resolve()
                _location.value = LocationUiState(location = resolved)
                loadWeather(showSpinner = true)
            } catch (error: Throwable) {
                _location.value = LocationUiState(
                    errorMessage = error.message ?: "Could not determine device location"
                )
                _weather.value = WeatherUiState(
                    isLoading = false,
                    errorMessage = "Weather location unavailable"
                )
            }
        }
    }

    fun discoverAlarms(baseUrl: String, token: String) {
        if (baseUrl.isBlank() || token.isBlank()) {
            _discovery.value = DiscoveryState(
                hasRun = true,
                errorMessage = "URL and access token are required"
            )
            return
        }

        viewModelScope.launch {
            _discovery.value = DiscoveryState(hasRun = true, isLoading = true)
            runCatching { restClient.discoverPanelEntities(baseUrl, token) }
                .onSuccess { result ->
                    _discovery.value = DiscoveryState(
                        hasRun = true,
                        alarms = result.alarms,
                        wakeSensors = result.wakeSensors,
                        guestWifi = result.guestWifi,
                        weatherEntities = result.weatherEntities
                    )
                }
                .onFailure { error ->
                    _discovery.value = DiscoveryState(
                        hasRun = true,
                        errorMessage = error.message ?: "Could not connect to Home Assistant"
                    )
                }
        }
    }

    fun saveConfiguration(draft: PanelSettings) {
        if (draft.baseUrl.isBlank() || draft.accessToken.isBlank() || draft.alarmEntityId.isBlank()) return

        val normalizedSaver = draft.screensaverTimeoutMinutes.coerceAtLeast(0)
        val normalizedSleep = draft.sleepTimeoutMinutes.coerceAtLeast(0).let { value ->
            if (value > 0 && normalizedSaver > 0 && value <= normalizedSaver) normalizedSaver + 1 else value
        }
        val normalized = draft.copy(
            baseUrl = draft.baseUrl.trim(),
            accessToken = draft.accessToken.trim(),
            alarmEntityId = draft.alarmEntityId.trim(),
            wakeEntityId = draft.wakeEntityId?.trim()?.takeIf { it.isNotBlank() },
            screensaverTimeoutMinutes = normalizedSaver,
            sleepTimeoutMinutes = normalizedSleep,
            settingsPin = draft.settingsPin.trim().filter(Char::isDigit).take(8),
            weatherSource = draft.weatherSource.takeIf {
                it == WEATHER_SOURCE_HOME_ASSISTANT || it == WEATHER_SOURCE_OPEN_METEO
            } ?: WEATHER_SOURCE_HOME_ASSISTANT,
            weatherEntityId = draft.weatherEntityId?.trim()?.takeIf { it.isNotBlank() },
            rtspPort = draft.rtspPort.coerceIn(1024, 65535),
            rtspAdvertisedHost = draft.rtspAdvertisedHost.trim()
                .removePrefix("rtsp://")
                .substringBefore(':')
                .trim('/'),
            guestVoucherSensorEntityId = draft.guestVoucherSensorEntityId?.trim()?.takeIf { it.isNotBlank() },
            guestCreateButtonEntityId = draft.guestCreateButtonEntityId?.trim()?.takeIf { it.isNotBlank() },
            guestDeleteButtonEntityId = draft.guestDeleteButtonEntityId?.trim()?.takeIf { it.isNotBlank() },
            guestQrImageEntityId = draft.guestQrImageEntityId?.trim()?.takeIf { it.isNotBlank() },
            mqttHost = draft.mqttHost.trim(),
            mqttPort = draft.mqttPort.coerceIn(1, 65535),
            mqttUsername = draft.mqttUsername.trim()
        )

        viewModelScope.launch {
            _editing.value = false
            settingsRepository.save(normalized)
            _discovery.value = DiscoveryState()
            _guestWifi.value = GuestWifiUiState()
            userActivity()
        }
    }

    fun editConfiguration() {
        _editing.value = true
        socketClient.disconnect()
        _discovery.value = DiscoveryState()
        userActivity()
    }

    fun cancelEditing() {
        _editing.value = false
        _settings.value?.let(::connect)
        userActivity()
    }

    fun clearConfiguration() {
        viewModelScope.launch {
            _editing.value = true
            socketClient.disconnect()
            rtspCameraServer?.stop()
            mqttDeviceBridge?.disconnect()
            settingsRepository.clear()
            _editing.value = false
            _discovery.value = DiscoveryState()
            _guestWifi.value = GuestWifiUiState()
            userActivity()
        }
    }

    fun reconnect() {
        _settings.value?.let(::connect)
        userActivity()
    }

    fun performAction(action: AlarmAction, code: String?) {
        userActivity()
        socketClient.performAction(action, code)
    }

    fun createGuestVoucher() {
        userActivity()
        socketClient.createGuestVoucher()
        viewModelScope.launch {
            delay(1_500L)
            refreshGuestQr(showSpinner = false)
        }
    }

    fun deleteGuestVoucher() {
        userActivity()
        socketClient.deleteGuestVoucher()
        viewModelScope.launch {
            delay(1_500L)
            refreshGuestQr(showSpinner = false)
        }
    }

    fun refreshGuestQr(showSpinner: Boolean = true) {
        val current = _settings.value ?: return
        val entityId = current.guestQrImageEntityId
        if (entityId.isNullOrBlank()) {
            _guestWifi.value = GuestWifiUiState(errorMessage = "No UniFi QR image entity is configured")
            return
        }

        viewModelScope.launch {
            if (showSpinner) _guestWifi.value = _guestWifi.value.copy(isLoadingQr = true, errorMessage = null)
            runCatching { restClient.fetchImage(current.baseUrl, current.accessToken, entityId) }
                .onSuccess { bytes ->
                    _guestWifi.value = GuestWifiUiState(qrImageBytes = bytes)
                }
                .onFailure { error ->
                    _guestWifi.value = _guestWifi.value.copy(
                        isLoadingQr = false,
                        errorMessage = error.message ?: "Could not load the guest Wi-Fi QR code"
                    )
                }
        }
    }

    fun refreshWeather() {
        userActivity()
        val current = _settings.value ?: return
        viewModelScope.launch {
            if (current.weatherSource == WEATHER_SOURCE_HOME_ASSISTANT) {
                loadWeather(showSpinner = _weather.value.forecast == null)
            } else if (_location.value.location == null) {
                refreshDeviceLocation()
            } else {
                loadWeather(showSpinner = _weather.value.forecast == null)
            }
        }
    }

    fun usesDeviceLocationForWeather(): Boolean =
        _settings.value?.weatherSource != WEATHER_SOURCE_HOME_ASSISTANT

    fun runNetworkDiagnostics(draft: PanelSettings) {
        if (_networkDiagnostics.value.isRunning) return
        viewModelScope.launch {
            val items = mutableListOf<NetworkDiagnosticItem>()
            _networkDiagnostics.value = NetworkDiagnosticsState(isRunning = true, items = emptyList())
            try {
                networkDiagnosticsClient.run(draft) { item ->
                    items.removeAll { it.key == item.key }
                    items += item
                    _networkDiagnostics.value = NetworkDiagnosticsState(isRunning = true, items = items.toList())
                }
            } catch (error: Throwable) {
                items += NetworkDiagnosticItem(
                    key = "lan",
                    status = dev.homepanel.app.network.DiagnosticStatus.FAILED,
                    detail = "Diagnostics failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                )
            } finally {
                _networkDiagnostics.value = NetworkDiagnosticsState(isRunning = false, items = items.toList())
            }
        }
    }

    fun userActivity() {
        mqttForcedSleep = false
        lastActivityElapsed = SystemClock.elapsedRealtime()
        if (_displayMode.value != PanelDisplayMode.ACTIVE) {
            _displayMode.value = PanelDisplayMode.ACTIVE
        }
    }

    fun onLocalDetection() {
        if (_displayMode.value != PanelDisplayMode.ACTIVE) {
            wakeDisplay()
        }
    }

    fun onProximityChanged(isNear: Boolean) {
        localProximityNear = isNear
        publishDeviceTelemetry()
    }

    private fun wakeDisplay() {
        mqttForcedSleep = false
        lastActivityElapsed = SystemClock.elapsedRealtime()
        _displayMode.value = PanelDisplayMode.ACTIVE
        _wakePulse.value = _wakePulse.value + 1L
    }

    private fun updateDisplayMode() {
        if (_editing.value || _settings.value == null) {
            _displayMode.value = PanelDisplayMode.ACTIVE
            return
        }

        val settings = _settings.value ?: return
        val idleMs = SystemClock.elapsedRealtime() - lastActivityElapsed
        val saverMs = settings.screensaverTimeoutMinutes.toLong() * 60_000L
        val sleepMs = settings.sleepTimeoutMinutes.toLong() * 60_000L

        val nextMode = when {
            mqttForcedSleep -> PanelDisplayMode.SLEEP
            settings.sleepTimeoutMinutes > 0 && idleMs >= sleepMs -> PanelDisplayMode.SLEEP
            settings.screensaverTimeoutMinutes > 0 && idleMs >= saverMs -> PanelDisplayMode.SCREENSAVER
            else -> PanelDisplayMode.ACTIVE
        }
        if (_displayMode.value != nextMode) {
            _displayMode.value = nextMode
            publishDeviceTelemetry()
        }
    }

    private fun forceSleepFromMqtt() {
        mqttForcedSleep = true
        _displayMode.value = PanelDisplayMode.SLEEP
        publishDeviceTelemetry()
    }

    private fun publishDeviceTelemetry() {
        val rtsp = _rtspCamera.value
        mqttDeviceBridge?.publishTelemetry(
            telemetryReader.read(
                proximityNear = localProximityNear,
                ambientLightLux = _ambientLux.value,
                displayMode = _displayMode.value.name.lowercase(),
                rtspRunning = rtsp.running,
                rtspClients = rtsp.clientCount,
                rtspUrl = rtsp.endpoint
            )
        )
    }

    private fun loadHouseSummary(showSpinner: Boolean) {
        val current = _settings.value ?: return
        if (_houseSummary.value.isLoading) return
        viewModelScope.launch {
            // Mark every request as in-flight so periodic refreshes and WebSocket events cannot
            // fan out into overlapping /api/states calls. The UI may decide whether to render
            // the spinner, but the concurrency guard always remains active.
            _houseSummary.value = _houseSummary.value.copy(
                isLoading = true,
                errorMessage = if (showSpinner) null else _houseSummary.value.errorMessage
            )
            runCatching { restClient.fetchHouseSummary(current.baseUrl, current.accessToken) }
                .onSuccess { summary ->
                    val activeWarnings = summary.weatherWarnings.filter { it.active }
                    val activeIds = activeWarnings.mapTo(linkedSetOf()) { it.entityId }
                    val newlyActive = activeIds - lastActiveWeatherWarningIds
                    val cleared = lastActiveWeatherWarningIds - activeIds

                    if (newlyActive.isNotEmpty()) {
                        val names = activeWarnings
                            .filter { it.entityId in newlyActive }
                            .joinToString(", ") { it.title ?: it.friendlyName }
                        historyRepository.add("weather", "Weather warning: $names")
                        wakeDisplay()
                    }
                    if (cleared.isNotEmpty() && activeIds.isEmpty()) {
                        historyRepository.add("weather", "Weather warnings cleared")
                    }
                    lastActiveWeatherWarningIds = activeIds
                    _houseSummary.value = HouseSummaryUiState(summary = summary)
                }
                .onFailure { error ->
                    _houseSummary.value = _houseSummary.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Could not load house status"
                    )
                }
        }
    }

    private suspend fun loadWeather(showSpinner: Boolean) {
        val current = _settings.value ?: return
        if (_weather.value.isLoading) return
        if (showSpinner) {
            _weather.value = _weather.value.copy(isLoading = true, errorMessage = null)
        } else {
            _weather.value = _weather.value.copy(isLoading = true)
        }

        val request: suspend () -> WeatherForecast = when (current.weatherSource) {
            WEATHER_SOURCE_HOME_ASSISTANT -> {
                {
                    restClient.fetchWeatherForecast(
                        current.baseUrl,
                        current.accessToken,
                        current.weatherEntityId
                    )
                }
            }
            else -> {
                val currentLocation = _location.value.location
                if (currentLocation == null) {
                    _weather.value = _weather.value.copy(
                        isLoading = false,
                        errorMessage = "Device location is required for Open-Meteo"
                    )
                    return
                }
                { weatherClient.fetchForecast(currentLocation) }
            }
        }

        runCatching { request() }
            .onSuccess { forecast ->
                _weather.value = WeatherUiState(isLoading = false, forecast = forecast)
            }
            .onFailure { error ->
                _weather.value = _weather.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load weather"
                )
            }
    }

    private fun connect(settings: PanelSettings) {
        runCatching {
            socketClient.connect(
                baseUrl = settings.baseUrl,
                token = settings.accessToken,
                entityId = settings.alarmEntityId,
                wakeEntityId = settings.wakeEntityId,
                guestVoucherSensorEntityId = settings.guestVoucherSensorEntityId,
                guestCreateButtonEntityId = settings.guestCreateButtonEntityId,
                guestDeleteButtonEntityId = settings.guestDeleteButtonEntityId
            )
        }.onFailure { error ->
            historyRepository.add("error", "Home Assistant connection failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
        }
    }

    private fun ensureRtspServer(): RtspCameraServer {
        rtspCameraServer?.let { return it }
        val created = RtspCameraServer(getApplication<Application>())
        rtspCameraServer = created
        rtspStateJob?.cancel()
        rtspStateJob = viewModelScope.launch {
            created.state.collect { state -> _rtspCamera.value = state }
        }
        return created
    }

    private fun ensureMqttBridge(): MqttDeviceBridge {
        mqttDeviceBridge?.let { return it }
        val created = MqttDeviceBridge(getApplication<Application>()) { screenOn ->
            viewModelScope.launch {
                if (screenOn) wakeDisplay() else forceSleepFromMqtt()
            }
        }
        mqttDeviceBridge = created
        mqttStateJob?.cancel()
        mqttStateJob = viewModelScope.launch {
            created.state.collect { state -> _mqttDevice.value = state }
        }
        return created
    }

    private fun applyMqttSettings(settings: PanelSettings?) {
        if (safeMode) {
            mqttDeviceBridge?.disconnect()
            _mqttDevice.value = MqttDeviceState(
                enabled = settings?.mqttDiscoveryEnabled == true,
                connected = false,
                errorMessage = if (settings?.mqttDiscoveryEnabled == true) "Disabled for this launch by crash safe mode" else null
            )
            return
        }
        if (settings?.mqttDiscoveryEnabled == true && settings.mqttHost.isNotBlank()) {
            runCatching { ensureMqttBridge().applySettings(settings) }
                .onFailure { error ->
                    _mqttDevice.value = MqttDeviceState(
                        enabled = true,
                        connected = false,
                        errorMessage = "MQTT startup failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                    )
                }
        } else {
            mqttDeviceBridge?.applySettings(settings)
            _mqttDevice.value = MqttDeviceState(enabled = false)
        }
    }

    private fun applyRtspSettings(settings: PanelSettings?) {
        if (safeMode) {
            rtspCameraServer?.stop()
            _rtspCamera.value = RtspCameraState(
                enabled = settings?.rtspEnabled == true,
                running = false,
                port = settings?.rtspPort ?: 8554,
                errorMessage = if (settings?.rtspEnabled == true) "Disabled for this launch by crash safe mode" else null
            )
            return
        }
        if (settings?.rtspEnabled == true) {
            runCatching { ensureRtspServer().start(settings.rtspPort, settings.rtspAdvertisedHost) }
                .onFailure { setRtspStartupError(it) }
        } else {
            rtspCameraServer?.stop()
            _rtspCamera.value = RtspCameraState(
                enabled = false,
                port = settings?.rtspPort ?: 8554
            )
        }
    }

    private fun setRtspStartupError(error: Throwable) {
        _rtspCamera.value = _rtspCamera.value.copy(
            running = false,
            errorMessage = "RTSP startup failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        )
    }

    override fun onCleared() {
        socketClient.disconnect()
        rtspStateJob?.cancel()
        mqttStateJob?.cancel()
        rtspCameraServer?.shutdown()
        mqttDeviceBridge?.close()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        super.onCleared()
    }

    companion object {
        const val WEATHER_SOURCE_HOME_ASSISTANT = "home_assistant"
        const val WEATHER_SOURCE_OPEN_METEO = "open_meteo"
        private const val WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1000L
        private const val HOUSE_SUMMARY_REFRESH_INTERVAL_MS = 20_000L
        private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
