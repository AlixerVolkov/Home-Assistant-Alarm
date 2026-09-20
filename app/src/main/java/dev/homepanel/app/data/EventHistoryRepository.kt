package dev.homepanel.app.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PanelEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val kind: String,
    val message: String
)

class EventHistoryRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<PanelEvent>> = _events.asStateFlow()

    @Synchronized
    fun add(kind: String, message: String) {
        if (message.isBlank()) return
        val latest = PanelEvent(kind = kind, message = message)
        val updated = (listOf(latest) + _events.value)
            .distinctBy { event -> "${event.kind}|${event.message}|${event.timestampEpochMs / 5_000L}" }
            .take(MAX_EVENTS)
        _events.value = updated
        persist(updated)
    }

    @Synchronized
    fun clear() {
        _events.value = emptyList()
        preferences.edit().remove(KEY_EVENTS).apply()
    }

    private fun load(): List<PanelEvent> = runCatching {
        val raw = preferences.getString(KEY_EVENTS, null) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PanelEvent(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        timestampEpochMs = item.optLong("timestamp", System.currentTimeMillis()),
                        kind = item.optString("kind", "event"),
                        message = item.optString("message")
                    )
                )
            }
        }.take(MAX_EVENTS)
    }.getOrDefault(emptyList())

    private fun persist(events: List<PanelEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("timestamp", event.timestampEpochMs)
                    .put("kind", event.kind)
                    .put("message", event.message)
            )
        }
        preferences.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "home_panel_history"
        private const val KEY_EVENTS = "events"
        private const val MAX_EVENTS = 40
    }
}
