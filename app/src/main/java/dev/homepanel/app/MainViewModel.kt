package dev.homepanel.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.data.SettingsRepository
import dev.homepanel.app.network.AlarmAction
import dev.homepanel.app.network.AlarmEntitySummary
import dev.homepanel.app.network.ConnectionStatus
import dev.homepanel.app.network.DeviceLocation
import dev.homepanel.app.network.DeviceLocationResolver
import dev.homepanel.app.network.HomeAssistantRestClient
import dev.homepanel.app.network.HomeAssistantWebSocket
import dev.homepanel.app.network.WakeSensorSummary
import dev.homepanel.app.network.WeatherClient
import dev.homepanel.app.network.WeatherForecast
import java.util.concurrent.TimeUnit
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
    private val locationResolver = DeviceLocationResolver(application)

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

    private val _displayMode = MutableStateFlow(PanelDisplayMode.ACTIVE)
    val displayMode: StateFlow<PanelDisplayMode> = _displayMode.asStateFlow()

    private val _wakePulse = MutableStateFlow(0L)
    val wakePulse: StateFlow<Long> = _wakePulse.asStateFlow()

    val connection = socketClient.state

    private var lastActivityElapsed = SystemClock.elapsedRealtime()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                _settings.value = current
                _settingsLoaded.value = true
                userActivity()
                if (current != null && !_editing.value) {
                    connect(current)
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
                updateDisplayMode()
                delay(1_000L)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                if (_location.value.location != null) {
                    loadWeather(showSpinner = _weather.value.forecast == null)
                }
                delay(WEATHER_REFRESH_INTERVAL_MS)
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
    }

    fun hasLocationPermission(): Boolean = locationResolver.hasLocationPermission()

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
                        wakeSensors = result.wakeSensors
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

    fun saveConfiguration(
        baseUrl: String,
        token: String,
        alarmEntityId: String,
        wakeEntityId: String?,
        screensaverMinutes: Int,
        sleepMinutes: Int
    ) {
        if (baseUrl.isBlank() || token.isBlank() || alarmEntityId.isBlank()) return

        val normalizedSaver = screensaverMinutes.coerceAtLeast(0)
        val normalizedSleep = sleepMinutes.coerceAtLeast(0).let { value ->
            if (value > 0 && normalizedSaver > 0 && value <= normalizedSaver) normalizedSaver + 1 else value
        }

        viewModelScope.launch {
            _editing.value = false
            settingsRepository.save(
                PanelSettings(
                    baseUrl = baseUrl,
                    accessToken = token,
                    alarmEntityId = alarmEntityId,
                    wakeEntityId = wakeEntityId?.takeIf { it.isNotBlank() },
                    screensaverTimeoutMinutes = normalizedSaver,
                    sleepTimeoutMinutes = normalizedSleep
                )
            )
            _discovery.value = DiscoveryState()
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
            settingsRepository.clear()
            _editing.value = false
            _discovery.value = DiscoveryState()
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

    fun refreshWeather() {
        userActivity()
        viewModelScope.launch {
            if (_location.value.location == null) {
                refreshDeviceLocation()
            } else {
                loadWeather(showSpinner = _weather.value.forecast == null)
            }
        }
    }

    fun userActivity() {
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

    private fun wakeDisplay() {
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

        _displayMode.value = when {
            settings.sleepTimeoutMinutes > 0 && idleMs >= sleepMs -> PanelDisplayMode.SLEEP
            settings.screensaverTimeoutMinutes > 0 && idleMs >= saverMs -> PanelDisplayMode.SCREENSAVER
            else -> PanelDisplayMode.ACTIVE
        }
    }

    private suspend fun loadWeather(showSpinner: Boolean) {
        val currentLocation = _location.value.location ?: return
        if (showSpinner) {
            _weather.value = _weather.value.copy(isLoading = true, errorMessage = null)
        }

        runCatching { weatherClient.fetchForecast(currentLocation) }
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
        socketClient.connect(
            baseUrl = settings.baseUrl,
            token = settings.accessToken,
            entityId = settings.alarmEntityId,
            wakeEntityId = settings.wakeEntityId
        )
    }

    override fun onCleared() {
        socketClient.disconnect()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        super.onCleared()
    }

    companion object {
        private const val WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1000L
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
