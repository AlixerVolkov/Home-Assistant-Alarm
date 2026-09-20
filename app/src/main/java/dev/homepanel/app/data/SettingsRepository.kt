package dev.homepanel.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.homepanel.app.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.panelDataStore by preferencesDataStore(name = "home_panel_settings")

class SettingsRepository(
    private val context: Context,
    private val cryptoManager: CryptoManager = CryptoManager()
) {
    val settings: Flow<PanelSettings?> = context.panelDataStore.data
        .map { preferences ->
            val baseUrl = preferences[KEY_BASE_URL] ?: return@map null
            val encryptedToken = preferences[KEY_ACCESS_TOKEN] ?: return@map null
            val alarmEntityId = preferences[KEY_ALARM_ENTITY] ?: return@map null

            val token = runCatching { cryptoManager.decrypt(encryptedToken) }
                .getOrElse { return@map null }

            PanelSettings(
                baseUrl = baseUrl,
                accessToken = token,
                alarmEntityId = alarmEntityId,
                wakeEntityId = preferences[KEY_WAKE_ENTITY]?.takeIf { it.isNotBlank() },
                screensaverTimeoutMinutes = preferences[KEY_SCREENSAVER_MINUTES] ?: 2,
                sleepTimeoutMinutes = preferences[KEY_SLEEP_MINUTES] ?: 10,
                proximityWakeEnabled = preferences[KEY_PROXIMITY_WAKE] ?: true,
                rtspEnabled = preferences[KEY_RTSP_ENABLED] ?: false,
                rtspPort = preferences[KEY_RTSP_PORT] ?: 8554,
                guestVoucherSensorEntityId = preferences[KEY_GUEST_VOUCHER_SENSOR]?.takeIf { it.isNotBlank() },
                guestCreateButtonEntityId = preferences[KEY_GUEST_CREATE_BUTTON]?.takeIf { it.isNotBlank() },
                guestQrImageEntityId = preferences[KEY_GUEST_QR_IMAGE]?.takeIf { it.isNotBlank() }
            )
        }
        .catch { emit(null) }

    suspend fun save(settings: PanelSettings) {
        context.panelDataStore.edit { preferences ->
            preferences[KEY_BASE_URL] = settings.baseUrl.trim().trimEnd('/')
            preferences[KEY_ACCESS_TOKEN] = cryptoManager.encrypt(settings.accessToken.trim())
            preferences[KEY_ALARM_ENTITY] = settings.alarmEntityId.trim()
            preferences[KEY_WAKE_ENTITY] = settings.wakeEntityId?.trim().orEmpty()
            preferences[KEY_SCREENSAVER_MINUTES] = settings.screensaverTimeoutMinutes.coerceAtLeast(0)
            preferences[KEY_SLEEP_MINUTES] = settings.sleepTimeoutMinutes.coerceAtLeast(0)
            preferences[KEY_PROXIMITY_WAKE] = settings.proximityWakeEnabled
            preferences[KEY_RTSP_ENABLED] = settings.rtspEnabled
            preferences[KEY_RTSP_PORT] = settings.rtspPort.coerceIn(1024, 65535)
            preferences[KEY_GUEST_VOUCHER_SENSOR] = settings.guestVoucherSensorEntityId?.trim().orEmpty()
            preferences[KEY_GUEST_CREATE_BUTTON] = settings.guestCreateButtonEntityId?.trim().orEmpty()
            preferences[KEY_GUEST_QR_IMAGE] = settings.guestQrImageEntityId?.trim().orEmpty()
        }
    }

    suspend fun clear() {
        context.panelDataStore.edit { preferences ->
            preferences.remove(KEY_BASE_URL)
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_ALARM_ENTITY)
            preferences.remove(KEY_WAKE_ENTITY)
            preferences.remove(KEY_SCREENSAVER_MINUTES)
            preferences.remove(KEY_SLEEP_MINUTES)
            preferences.remove(KEY_PROXIMITY_WAKE)
            preferences.remove(KEY_RTSP_ENABLED)
            preferences.remove(KEY_RTSP_PORT)
            preferences.remove(KEY_GUEST_VOUCHER_SENSOR)
            preferences.remove(KEY_GUEST_CREATE_BUTTON)
            preferences.remove(KEY_GUEST_QR_IMAGE)
        }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_ALARM_ENTITY = stringPreferencesKey("alarm_entity_id")
        private val KEY_WAKE_ENTITY = stringPreferencesKey("wake_entity_id")
        private val KEY_SCREENSAVER_MINUTES = intPreferencesKey("screensaver_minutes")
        private val KEY_SLEEP_MINUTES = intPreferencesKey("sleep_minutes")
        private val KEY_PROXIMITY_WAKE = booleanPreferencesKey("proximity_wake")
        private val KEY_RTSP_ENABLED = booleanPreferencesKey("rtsp_enabled")
        private val KEY_RTSP_PORT = intPreferencesKey("rtsp_port")
        private val KEY_GUEST_VOUCHER_SENSOR = stringPreferencesKey("guest_voucher_sensor")
        private val KEY_GUEST_CREATE_BUTTON = stringPreferencesKey("guest_create_button")
        private val KEY_GUEST_QR_IMAGE = stringPreferencesKey("guest_qr_image")
    }
}
