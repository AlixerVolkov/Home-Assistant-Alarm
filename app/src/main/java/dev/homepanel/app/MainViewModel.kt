package dev.homepanel.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.data.SettingsRepository
import dev.homepanel.app.network.AlarmAction
import dev.homepanel.app.network.AlarmEntitySummary
import dev.homepanel.app.network.HomeAssistantRestClient
import dev.homepanel.app.network.HomeAssistantWebSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class DiscoveryState(
    val hasRun: Boolean = false,
    val isLoading: Boolean = false,
    val alarms: List<AlarmEntitySummary> = emptyList(),
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

    private val _settings = MutableStateFlow<PanelSettings?>(null)
    val settings: StateFlow<PanelSettings?> = _settings.asStateFlow()

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _discovery = MutableStateFlow(DiscoveryState())
    val discovery: StateFlow<DiscoveryState> = _discovery.asStateFlow()

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
        socketClient.disconnect()
        _editing.value = true
        _discovery.value = DiscoveryState()
    }

    fun cancelEditing() {
        _editing.value = false
        _settings.value?.let(::connect)
    }

    fun clearConfiguration() {
        viewModelScope.launch {
            socketClient.disconnect()
            _editing.value = false
            settingsRepository.clear()
            _discovery.value = DiscoveryState()
        }
    }

    fun reconnect() {
        _settings.value?.let(::connect)
    }

    fun performAction(action: AlarmAction, code: String?) {
        socketClient.performAction(action, code)
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
}
