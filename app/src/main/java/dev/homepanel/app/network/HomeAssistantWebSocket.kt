package dev.homepanel.app.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

class HomeAssistantWebSocket(
    private val client: OkHttpClient
) {
    private val nextId = AtomicInteger(1)
    private val actionRequestIds = ConcurrentHashMap<Int, AlarmAction>()

    private val _state = MutableStateFlow(HomeAssistantConnectionState())
    val state: StateFlow<HomeAssistantConnectionState> = _state.asStateFlow()

    private val _wakeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val wakeEvents: SharedFlow<Unit> = _wakeEvents.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var accessToken: String = ""
    private var alarmEntityId: String = ""
    private var wakeEntityId: String? = null
    private var getStatesRequestId: Int? = null

    fun connect(baseUrl: String, token: String, entityId: String, wakeEntityId: String? = null) {
        disconnect()
        accessToken = token.trim()
        alarmEntityId = entityId.trim()
        this.wakeEntityId = wakeEntityId?.trim()?.takeIf { it.isNotBlank() }
        _state.value = HomeAssistantConnectionState(status = ConnectionStatus.CONNECTING)

        val request = Request.Builder()
            .url(HomeAssistantUrl.webSocketUrl(baseUrl))
            .build()

        webSocket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        val current = webSocket
        webSocket = null
        current?.close(1000, "Client disconnect")
        getStatesRequestId = null
        actionRequestIds.clear()
        _state.value = HomeAssistantConnectionState(status = ConnectionStatus.DISCONNECTED)
    }

    fun performAction(action: AlarmAction, code: String?) {
        val socket = webSocket ?: run {
            _state.value = _state.value.copy(actionErrorMessage = "Not connected to Home Assistant")
            return
        }

        if (_state.value.status != ConnectionStatus.CONNECTED) {
            _state.value = _state.value.copy(actionErrorMessage = "Home Assistant is not authenticated")
            return
        }

        val alarm = _state.value.alarm
        if (alarm != null && !alarm.canPerform(action)) {
            _state.value = _state.value.copy(
                actionErrorMessage = "This action is not available from the current alarm state"
            )
            return
        }

        if (alarm?.requiresCode(action) == true && code.isNullOrBlank()) {
            _state.value = _state.value.copy(actionErrorMessage = "A PIN/code is required for this action")
            return
        }

        val requestId = nextId.getAndIncrement()
        actionRequestIds[requestId] = action

        val payload = JSONObject()
            .put("id", requestId)
            .put("type", "call_service")
            .put("domain", "alarm_control_panel")
            .put("service", action.service)
            .put("target", JSONObject().put("entity_id", alarmEntityId))

        if (!code.isNullOrBlank()) {
            payload.put("service_data", JSONObject().put("code", code.trim()))
        }

        _state.value = _state.value.copy(actionErrorMessage = null, pendingAction = action)

        if (!socket.send(payload.toString())) {
            actionRequestIds.remove(requestId)
            _state.value = _state.value.copy(
                actionErrorMessage = "Could not send command",
                pendingAction = null
            )
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (this@HomeAssistantWebSocket.webSocket !== webSocket) return
            _state.value = _state.value.copy(status = ConnectionStatus.CONNECTING, errorMessage = null)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (this@HomeAssistantWebSocket.webSocket !== webSocket) return
            runCatching { handleMessage(webSocket, JSONObject(text)) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        errorMessage = error.message ?: "Invalid message from Home Assistant",
                        pendingAction = null
                    )
                }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (this@HomeAssistantWebSocket.webSocket !== webSocket) return
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (this@HomeAssistantWebSocket.webSocket !== webSocket) return
            this@HomeAssistantWebSocket.webSocket = null
            _state.value = _state.value.copy(
                status = ConnectionStatus.DISCONNECTED,
                pendingAction = null
            )
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (this@HomeAssistantWebSocket.webSocket !== webSocket) return
            this@HomeAssistantWebSocket.webSocket = null
            _state.value = _state.value.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = t.message ?: "WebSocket connection failed",
                pendingAction = null
            )
        }
    }

    private fun handleMessage(socket: WebSocket, message: JSONObject) {
        when (message.optString("type")) {
            "auth_required" -> authenticate(socket)
            "auth_ok" -> onAuthenticated(socket)
            "auth_invalid" -> {
                _state.value = _state.value.copy(
                    status = ConnectionStatus.ERROR,
                    errorMessage = message.optString("message", "Invalid Home Assistant token"),
                    pendingAction = null
                )
                this@HomeAssistantWebSocket.webSocket = null
                socket.close(1008, "Authentication failed")
            }
            "result" -> handleResult(message)
            "event" -> handleEvent(message)
        }
    }

    private fun authenticate(socket: WebSocket) {
        socket.send(
            JSONObject()
                .put("type", "auth")
                .put("access_token", accessToken)
                .toString()
        )
    }

    private fun onAuthenticated(socket: WebSocket) {
        _state.value = _state.value.copy(status = ConnectionStatus.CONNECTED, errorMessage = null)

        val statesId = nextId.getAndIncrement()
        getStatesRequestId = statesId
        socket.send(JSONObject().put("id", statesId).put("type", "get_states").toString())

        socket.send(
            JSONObject()
                .put("id", nextId.getAndIncrement())
                .put("type", "subscribe_events")
                .put("event_type", "state_changed")
                .toString()
        )
    }

    private fun handleResult(message: JSONObject) {
        val requestId = message.optInt("id", -1)

        if (requestId == getStatesRequestId) {
            getStatesRequestId = null
            if (!message.optBoolean("success", false)) {
                _state.value = _state.value.copy(
                    errorMessage = extractError(message, "Could not read Home Assistant states")
                )
                return
            }

            val result = message.optJSONArray("result") ?: JSONArray()
            var found = false
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                if (item.optString("entity_id") == alarmEntityId) {
                    found = true
                    _state.value = _state.value.copy(alarm = parseAlarmState(item))
                    break
                }
            }
            if (!found) {
                _state.value = _state.value.copy(errorMessage = "Selected alarm entity was not found")
            }
            return
        }

        val action = actionRequestIds.remove(requestId)
        if (action != null) {
            if (message.optBoolean("success", false)) {
                _state.value = _state.value.copy(actionErrorMessage = null, pendingAction = null)
            } else {
                _state.value = _state.value.copy(
                    actionErrorMessage = extractError(message, "Alarm command failed"),
                    pendingAction = null
                )
            }
        }
    }

    private fun handleEvent(message: JSONObject) {
        val event = message.optJSONObject("event") ?: return
        val data = event.optJSONObject("data") ?: return
        val entityId = data.optString("entity_id")
        val newState = data.optJSONObject("new_state") ?: return
        val newValue = newState.optString("state")

        if (entityId == wakeEntityId && isDetectionState(newValue)) {
            _wakeEvents.tryEmit(Unit)
        }

        if (entityId != alarmEntityId) return

        val parsed = parseAlarmState(newState)
        _state.value = _state.value.copy(
            alarm = parsed,
            actionErrorMessage = null,
            pendingAction = null
        )

        if (parsed.state in WAKE_ALARM_STATES) {
            _wakeEvents.tryEmit(Unit)
        }
    }

    private fun parseAlarmState(item: JSONObject): AlarmEntityState {
        val attributes = item.optJSONObject("attributes")
        val entityId = item.optString("entity_id", alarmEntityId)
        val friendlyName = attributes?.optString("friendly_name")
            ?.takeIf { it.isNotBlank() }
            ?: entityId.substringAfter('.').replace('_', ' ')

        return AlarmEntityState(
            entityId = entityId,
            friendlyName = friendlyName,
            state = item.optString("state", "unknown"),
            supportedFeatures = attributes?.optInt("supported_features", 0) ?: 0,
            codeArmRequired = attributes?.optBoolean("code_arm_required", true) ?: true,
            codeFormat = attributes?.optString("code_format")
                ?.takeIf { it.isNotBlank() && it != "null" },
            changedBy = attributes?.optString("changed_by")
                ?.takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun extractError(message: JSONObject, fallback: String): String {
        return message.optJSONObject("error")
            ?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun isDetectionState(state: String): Boolean {
        return state.lowercase() in DETECTION_STATES
    }

    companion object {
        private val DETECTION_STATES = setOf("on", "home", "detected", "occupied", "open", "true")
        private val WAKE_ALARM_STATES = setOf("triggered", "pending", "arming", "disarming")
    }
}
