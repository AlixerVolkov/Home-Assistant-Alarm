package dev.homepanel.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.data.SettingsRepository
import dev.homepanel.app.network.AlarmAction
import dev.homepanel.app.network.AlarmEntitySummary
import dev.homepanel.app.network.ConnectionStatus
import dev.homepanel.app.network.HomeAssistantRestClient
import dev.homepanel.app.network.HomeAssistantWebSocket
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
    val errorMessage: String? = null
)

data class WeatherUiState(
    val isLoading: Boolean = true,
    val forecast: WeatherForecast? = null,
    val errorMessage: String? = null
)

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

    val connection = socketClient.state

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { current ->
                _settings.value = current
                _settingsLoaded.value = true
                if (current != null && !_editing.value) {
                    connect(current)
                } else if (current == null) {
                    socketClient.disconnect()
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                loadWeather(showSpinner = _weather.value.forecast == null)
                delay(WEATHER_REFRESH_INTERVAL_MS)
            }
        }

        viewModelScope.launch {
            socketClient.state.collect { state ->
                if (state.status == ConnectionStatus.ERROR ||
                    state.status == ConnectionStatus.DISCONNECTED
                ) {
                    delay(RECONNECT_DELAY_MS)
                    val currentSettings = _settings.value
                    val currentStatus = socketClient.state.value.status
                    if (currentSettings != null &&
                        !_editing.value &&
                        (currentStatus == ConnectionStatus.ERROR ||
                            currentStatus == ConnectionStatus.DISCONNECTED)
                    ) {
                        connect(currentSettings)
                    }
                }
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
            runCatching { restClient.discoverAlarmEntities(baseUrl, token) }
                .onSuccess { alarms ->
                    _discovery.value = DiscoveryState(hasRun = true, alarms = alarms)
                }
                .onFailure { error ->
                    _discovery.value = DiscoveryState(
                        hasRun = true,
                        errorMessage = error.message ?: "Could not connect to Home Assistant"
                    )
                }
        }
    }

    fun saveConfiguration(baseUrl: String, token: String, alarmEntityId: String) {
        if (baseUrl.isBlank() || token.isBlank() || alarmEntityId.isBlank()) return

        viewModelScope.launch {
            _editing.value = false
            settingsRepository.save(
                PanelSettings(
                    baseUrl = baseUrl,
                    accessToken = token,
                    alarmEntityId = alarmEntityId
                )
            )
            _discovery.value = DiscoveryState()
        }
    }

    fun editConfiguration() {
        _editing.value = true
        socketClient.disconnect()
        _discovery.value = DiscoveryState()
    }

    fun cancelEditing() {
        _editing.value = false
        _settings.value?.let(::connect)
    }

    fun clearConfiguration() {
        viewModelScope.launch {
            _editing.value = true
            socketClient.disconnect()
            settingsRepository.clear()
            _editing.value = false
            _discovery.value = DiscoveryState()
        }
    }

    fun reconnect() {
        _settings.value?.let(::connect)
    }

    fun performAction(action: AlarmAction, code: String?) {
        socketClient.performAction(action, code)
    }

    fun refreshWeather() {
        viewModelScope.launch {
            loadWeather(showSpinner = _weather.value.forecast == null)
        }
    }

    private suspend fun loadWeather(showSpinner: Boolean) {
        if (showSpinner) {
            _weather.value = _weather.value.copy(isLoading = true, errorMessage = null)
        }

        runCatching { weatherClient.fetchEkerenForecast() }
            .onSuccess { forecast ->
                _weather.value = WeatherUiState(
                    isLoading = false,
                    forecast = forecast,
                    errorMessage = null
                )
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
            entityId = settings.alarmEntityId
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
