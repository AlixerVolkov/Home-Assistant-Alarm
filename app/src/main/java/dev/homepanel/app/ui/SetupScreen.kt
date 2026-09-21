package dev.homepanel.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.IntentFilter
import android.app.ActivityManager
import android.os.BatteryManager
import android.os.Build
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.homepanel.app.DiscoveryState
import dev.homepanel.app.MainViewModel
import dev.homepanel.app.R
import dev.homepanel.app.UpdateUiState
import dev.homepanel.app.camera.RtspCameraState
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.mqtt.MqttDeviceState
import dev.homepanel.app.network.DiagnosticStatus
import dev.homepanel.app.network.NetworkDiagnosticsState
import java.net.URI

@Composable
fun SetupScreen(
    initialSettings: PanelSettings?,
    discoveryState: DiscoveryState,
    rtspCameraState: RtspCameraState,
    mqttDeviceState: MqttDeviceState,
    updateState: UpdateUiState,
    networkDiagnosticsState: NetworkDiagnosticsState,
    canCancel: Boolean,
    onDiscover: (String, String) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRunNetworkDiagnostics: (PanelSettings) -> Unit,
    onSave: (PanelSettings) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit
) {
    var baseUrl by rememberSaveable(initialSettings?.baseUrl) {
        mutableStateOf(initialSettings?.baseUrl.orEmpty())
    }
    var token by rememberSaveable(initialSettings?.accessToken) {
        mutableStateOf(initialSettings?.accessToken.orEmpty())
    }
    var entityId by rememberSaveable(initialSettings?.alarmEntityId) {
        mutableStateOf(initialSettings?.alarmEntityId.orEmpty())
    }
    val initialWakeEntityIds = remember(initialSettings) {
        (initialSettings?.wakeEntityIds.orEmpty() + listOfNotNull(initialSettings?.wakeEntityId))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }
    var wakeEntityIdsEncoded by rememberSaveable(initialSettings?.wakeEntityIds, initialSettings?.wakeEntityId) {
        mutableStateOf(initialWakeEntityIds.joinToString("\n"))
    }
    var wakeManualEntity by rememberSaveable { mutableStateOf("") }
    var showHardwareDiagnostics by rememberSaveable { mutableStateOf(false) }
    var saverMinutes by rememberSaveable(initialSettings?.screensaverTimeoutMinutes) {
        mutableStateOf((initialSettings?.screensaverTimeoutMinutes ?: 2).toString())
    }
    var sleepMinutes by rememberSaveable(initialSettings?.sleepTimeoutMinutes) {
        mutableStateOf((initialSettings?.sleepTimeoutMinutes ?: 10).toString())
    }
    var proximityWakeEnabled by rememberSaveable(initialSettings?.proximityWakeEnabled) {
        mutableStateOf(initialSettings?.proximityWakeEnabled ?: true)
    }
    var lightWakeFallbackEnabled by rememberSaveable(initialSettings?.lightWakeFallbackEnabled) {
        mutableStateOf(initialSettings?.lightWakeFallbackEnabled ?: false)
    }
    var autoBrightnessEnabled by rememberSaveable(initialSettings?.autoBrightnessEnabled) {
        mutableStateOf(initialSettings?.autoBrightnessEnabled ?: true)
    }
    var kioskModeEnabled by rememberSaveable(initialSettings?.kioskModeEnabled) {
        mutableStateOf(initialSettings?.kioskModeEnabled ?: false)
    }
    var settingsPin by rememberSaveable(initialSettings?.settingsPin) {
        mutableStateOf(initialSettings?.settingsPin.orEmpty())
    }
    var updateChecksEnabled by rememberSaveable(initialSettings?.updateChecksEnabled) {
        mutableStateOf(initialSettings?.updateChecksEnabled ?: true)
    }
    var weatherSource by rememberSaveable(initialSettings?.weatherSource) {
        mutableStateOf(initialSettings?.weatherSource ?: MainViewModel.WEATHER_SOURCE_HOME_ASSISTANT)
    }
    var weatherEntityId by rememberSaveable(initialSettings?.weatherEntityId) {
        mutableStateOf(initialSettings?.weatherEntityId.orEmpty())
    }
    var rtspEnabled by rememberSaveable(initialSettings?.rtspEnabled) {
        mutableStateOf(initialSettings?.rtspEnabled ?: false)
    }
    var rtspPort by rememberSaveable(initialSettings?.rtspPort) {
        mutableStateOf((initialSettings?.rtspPort ?: 8554).toString())
    }
    var rtspAdvertisedHost by rememberSaveable(initialSettings?.rtspAdvertisedHost) {
        mutableStateOf(initialSettings?.rtspAdvertisedHost.orEmpty())
    }
    var autoDiscoveryRequested by rememberSaveable { mutableStateOf(false) }

    var guestVoucherSensor by rememberSaveable(initialSettings?.guestVoucherSensorEntityId) {
        mutableStateOf(initialSettings?.guestVoucherSensorEntityId.orEmpty())
    }
    var guestCreateButton by rememberSaveable(initialSettings?.guestCreateButtonEntityId) {
        mutableStateOf(initialSettings?.guestCreateButtonEntityId.orEmpty())
    }
    var guestDeleteButton by rememberSaveable(initialSettings?.guestDeleteButtonEntityId) {
        mutableStateOf(initialSettings?.guestDeleteButtonEntityId.orEmpty())
    }
    var guestQrImage by rememberSaveable(initialSettings?.guestQrImageEntityId) {
        mutableStateOf(initialSettings?.guestQrImageEntityId.orEmpty())
    }
    var mqttDiscoveryEnabled by rememberSaveable(initialSettings?.mqttDiscoveryEnabled) {
        mutableStateOf(initialSettings?.mqttDiscoveryEnabled ?: false)
    }
    var mqttHost by rememberSaveable(initialSettings?.mqttHost) {
        mutableStateOf(initialSettings?.mqttHost.orEmpty())
    }
    var mqttPort by rememberSaveable(initialSettings?.mqttPort) {
        mutableStateOf((initialSettings?.mqttPort ?: 1883).toString())
    }
    var mqttUsername by rememberSaveable(initialSettings?.mqttUsername) {
        mutableStateOf(initialSettings?.mqttUsername.orEmpty())
    }
    var mqttPassword by rememberSaveable(initialSettings?.mqttPassword) {
        mutableStateOf(initialSettings?.mqttPassword.orEmpty())
    }
    var mqttTls by rememberSaveable(initialSettings?.mqttTls) {
        mutableStateOf(initialSettings?.mqttTls ?: false)
    }

    LaunchedEffect(initialSettings?.baseUrl, initialSettings?.accessToken, discoveryState.hasRun) {
        val savedUrl = initialSettings?.baseUrl.orEmpty()
        val savedToken = initialSettings?.accessToken.orEmpty()
        if (!autoDiscoveryRequested &&
            !discoveryState.hasRun &&
            savedUrl.isNotBlank() &&
            savedToken.isNotBlank()
        ) {
            autoDiscoveryRequested = true
            onDiscover(savedUrl, savedToken)
        }
    }

    LaunchedEffect(discoveryState.guestWifi) {
        if (guestVoucherSensor.isBlank() && discoveryState.guestWifi.size == 1) {
            val guest = discoveryState.guestWifi.single()
            guestVoucherSensor = guest.voucherSensorEntityId
            guestCreateButton = guest.createButtonEntityId
            guestDeleteButton = guest.deleteButtonEntityId.orEmpty()
            guestQrImage = guest.qrImageEntityId.orEmpty()
        }
    }

    LaunchedEffect(discoveryState.weatherEntities, weatherSource) {
        if (weatherSource == MainViewModel.WEATHER_SOURCE_HOME_ASSISTANT &&
            weatherEntityId.isBlank() && discoveryState.weatherEntities.size == 1
        ) {
            weatherEntityId = discoveryState.weatherEntities.single().entityId
        }
    }

    val context = LocalContext.current
    var rtspCopied by rememberSaveable { mutableStateOf(false) }
    var frigateCopied by rememberSaveable { mutableStateOf(false) }
    val wakeEntityIds = wakeEntityIdsEncoded.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()

    fun draftSettings(): PanelSettings = PanelSettings(
        baseUrl = baseUrl,
        accessToken = token,
        alarmEntityId = entityId,
        wakeEntityId = wakeEntityIds.firstOrNull(),
        wakeEntityIds = wakeEntityIds,
        screensaverTimeoutMinutes = saverMinutes.toIntOrNull() ?: 0,
        sleepTimeoutMinutes = sleepMinutes.toIntOrNull() ?: 0,
        proximityWakeEnabled = proximityWakeEnabled,
        lightWakeFallbackEnabled = lightWakeFallbackEnabled,
        autoBrightnessEnabled = autoBrightnessEnabled,
        kioskModeEnabled = kioskModeEnabled,
        settingsPin = settingsPin,
        updateChecksEnabled = updateChecksEnabled,
        weatherSource = weatherSource,
        weatherEntityId = weatherEntityId.takeIf { it.isNotBlank() },
        rtspEnabled = rtspEnabled,
        rtspPort = rtspPort.toIntOrNull() ?: 8554,
        rtspAdvertisedHost = rtspAdvertisedHost,
        guestVoucherSensorEntityId = guestVoucherSensor.takeIf { it.isNotBlank() },
        guestCreateButtonEntityId = guestCreateButton.takeIf { it.isNotBlank() },
        guestDeleteButtonEntityId = guestDeleteButton.takeIf { it.isNotBlank() },
        guestQrImageEntityId = guestQrImage.takeIf { it.isNotBlank() },
        mqttDiscoveryEnabled = mqttDiscoveryEnabled,
        mqttHost = mqttHost,
        mqttPort = mqttPort.toIntOrNull() ?: if (mqttTls) 8883 else 1883,
        mqttUsername = mqttUsername,
        mqttPassword = mqttPassword,
        mqttTls = mqttTls
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) 16.dp else 32.dp
        val compactDisplaySettings = maxWidth < 520.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.system_locale_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(2.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ha_url)) },
                    placeholder = { Text("https://homeassistant.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                if (baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                    Text(
                        text = stringResource(R.string.http_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.access_token)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Button(
                    onClick = { onDiscover(baseUrl, token) },
                    enabled = !discoveryState.isLoading && baseUrl.isNotBlank() && token.isNotBlank()
                ) {
                    Text(
                        if (discoveryState.isLoading) stringResource(R.string.discovering)
                        else stringResource(R.string.discover_alarms)
                    )
                }

                discoveryState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (discoveryState.alarms.isNotEmpty()) {
                    Text(stringResource(R.string.select_alarm), style = MaterialTheme.typography.titleMedium)
                    EntityCombo(
                        selectedId = entityId,
                        choices = discoveryState.alarms.map { it.entityId to "${it.friendlyName} · ${it.state}" },
                        emptyLabel = stringResource(R.string.entity_combo_choose),
                        onSelect = { entityId = it }
                    )
                }

                OutlinedTextField(
                    value = entityId,
                    onValueChange = { entityId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("alarm_control_panel entity ID") },
                    placeholder = { Text("alarm_control_panel.home") },
                    singleLine = true
                )

                HorizontalDivider()

                Text(stringResource(R.string.weather_settings_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.weather_settings_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { weatherSource = MainViewModel.WEATHER_SOURCE_HOME_ASSISTANT }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = weatherSource == MainViewModel.WEATHER_SOURCE_HOME_ASSISTANT,
                            onClick = { weatherSource = MainViewModel.WEATHER_SOURCE_HOME_ASSISTANT }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.weather_source_ha), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.weather_source_ha_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (weatherSource == MainViewModel.WEATHER_SOURCE_HOME_ASSISTANT) {
                    if (discoveryState.weatherEntities.isNotEmpty()) {
                        EntityCombo(
                            selectedId = weatherEntityId,
                            choices = discoveryState.weatherEntities.map {
                                it.entityId to "${it.friendlyName} · ${it.condition}"
                            },
                            emptyLabel = stringResource(R.string.entity_combo_choose),
                            onSelect = { weatherEntityId = it }
                        )
                    }
                    OutlinedTextField(
                        value = weatherEntityId,
                        onValueChange = { weatherEntityId = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.weather_entity_manual)) },
                        placeholder = { Text("weather.forecast_home") },
                        supportingText = { Text(stringResource(R.string.weather_entity_hint)) },
                        singleLine = true
                    )
                    if (discoveryState.hasRun && discoveryState.weatherEntities.isEmpty()) {
                        Text(
                            stringResource(R.string.weather_entity_not_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { weatherSource = MainViewModel.WEATHER_SOURCE_OPEN_METEO }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = weatherSource == MainViewModel.WEATHER_SOURCE_OPEN_METEO,
                            onClick = { weatherSource = MainViewModel.WEATHER_SOURCE_OPEN_METEO }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.weather_source_open_meteo), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.weather_source_open_meteo_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(stringResource(R.string.guest_wifi_setup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.guest_wifi_setup_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            guestVoucherSensor = ""
                            guestCreateButton = ""
                            guestDeleteButton = ""
                            guestQrImage = ""
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = guestVoucherSensor.isBlank(),
                            onClick = {
                                guestVoucherSensor = ""
                                guestCreateButton = ""
                                guestDeleteButton = ""
                                guestQrImage = ""
                            }
                        )
                        Text(stringResource(R.string.guest_wifi_none))
                    }
                }

                discoveryState.guestWifi.forEach { guest ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                guestVoucherSensor = guest.voucherSensorEntityId
                                guestCreateButton = guest.createButtonEntityId
                                guestDeleteButton = guest.deleteButtonEntityId.orEmpty()
                                guestQrImage = guest.qrImageEntityId.orEmpty()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = guestVoucherSensor == guest.voucherSensorEntityId,
                                onClick = {
                                    guestVoucherSensor = guest.voucherSensorEntityId
                                    guestCreateButton = guest.createButtonEntityId
                                    guestDeleteButton = guest.deleteButtonEntityId.orEmpty()
                                    guestQrImage = guest.qrImageEntityId.orEmpty()
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("📶 ${guest.displayName}", style = MaterialTheme.typography.titleSmall)
                                Text(guest.voucherSensorEntityId, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    stringResource(R.string.guest_wifi_qr_hint, guest.qrImageEntityId.orEmpty()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (discoveryState.hasRun && discoveryState.guestWifi.isEmpty()) {
                    Text(
                        stringResource(R.string.guest_wifi_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                Text(stringResource(R.string.wake_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wake_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.proximity_wake_title),
                    subtitle = stringResource(R.string.proximity_wake_subtitle),
                    checked = proximityWakeEnabled,
                    onCheckedChange = { proximityWakeEnabled = it }
                )
                ProximitySensorStatus(enabled = proximityWakeEnabled)
                SettingsSwitchRow(
                    title = stringResource(R.string.light_wake_fallback_title),
                    subtitle = stringResource(R.string.light_wake_fallback_subtitle),
                    checked = lightWakeFallbackEnabled,
                    onCheckedChange = { lightWakeFallbackEnabled = it }
                )

                val wakeChoices = discoveryState.wakeSensors
                    .sortedWith(
                        compareBy(
                            { if (it.entityId.contains("frigate", true) || it.friendlyName.contains("frigate", true)) 0 else 1 },
                            { if (it.deviceClass in setOf("motion", "occupancy", "presence")) 0 else 1 },
                            { it.friendlyName.lowercase() }
                        )
                    )
                    .map { sensor ->
                        sensor.entityId to buildString {
                            if (sensor.entityId.contains("frigate", true) || sensor.friendlyName.contains("frigate", true)) append("🎥 ")
                            append(sensor.friendlyName)
                            sensor.deviceClass?.let { deviceClass -> append(" · ").append(deviceClass) }
                            append(" · ").append(sensor.state)
                        }
                    }
                EntityMultiSelect(
                    selectedIds = wakeEntityIds.toSet(),
                    choices = wakeChoices,
                    onSelectionChange = { selected -> wakeEntityIdsEncoded = selected.joinToString("\n") }
                )
                Text(
                    stringResource(R.string.wake_multi_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    discoveryState.isLoading -> Text(
                        stringResource(R.string.entity_combo_loading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    discoveryState.hasRun -> Text(
                        stringResource(R.string.entity_combo_count, discoveryState.wakeSensors.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        stringResource(R.string.entity_combo_discover_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = wakeManualEntity,
                        onValueChange = { wakeManualEntity = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.wake_entity_manual)) },
                        placeholder = { Text("binary_sensor.frigate_person") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            val entity = wakeManualEntity.trim()
                            if (entity.isNotBlank() && entity !in wakeEntityIds) {
                                wakeEntityIdsEncoded = (wakeEntityIds + entity).joinToString("\n")
                            }
                            wakeManualEntity = ""
                        },
                        enabled = wakeManualEntity.isNotBlank()
                    ) { Text(stringResource(R.string.add)) }
                }

                OutlinedButton(
                    onClick = { showHardwareDiagnostics = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hardware_diagnostics_open))
                }

                HorizontalDivider()

                Text(stringResource(R.string.rtsp_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.rtsp_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.rtsp_enable),
                    subtitle = stringResource(R.string.rtsp_privacy_hint),
                    checked = rtspEnabled,
                    onCheckedChange = { rtspEnabled = it }
                )
                if (rtspEnabled) {
                    OutlinedTextField(
                        value = rtspPort,
                        onValueChange = { rtspPort = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.rtsp_port)) },
                        supportingText = { Text(stringResource(R.string.rtsp_port_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = rtspAdvertisedHost,
                        onValueChange = { rtspAdvertisedHost = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.rtsp_advertised_host)) },
                        placeholder = { Text(rtspCameraState.ipv4Address ?: "192.168.1.50") },
                        supportingText = { Text(stringResource(R.string.rtsp_advertised_host_hint)) },
                        singleLine = true
                    )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.rtsp_endpoint), style = MaterialTheme.typography.titleSmall)
                            val endpoint = rtspCameraState.endpoint
                            when {
                                rtspCameraState.permissionRequired -> Text(
                                    stringResource(R.string.rtsp_permission_needed),
                                    color = MaterialTheme.colorScheme.error
                                )
                                !rtspCameraState.errorMessage.isNullOrBlank() -> Text(
                                    rtspCameraState.errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.error
                                )
                                !endpoint.isNullOrBlank() -> {
                                    Text(endpoint, style = MaterialTheme.typography.bodyMedium)
                                    if (!rtspCameraState.ipv4Address.isNullOrBlank()) {
                                        Text(
                                            stringResource(
                                                R.string.rtsp_ipv4_detected,
                                                rtspCameraState.ipv4Address.orEmpty(),
                                                rtspCameraState.interfaceName.orEmpty()
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        rtspCameraState.lanTransport?.let { transport ->
                                            Text(
                                                stringResource(
                                                    R.string.network_diag_transport,
                                                    transport,
                                                    rtspCameraState.interfaceName.orEmpty()
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (rtspCameraState.width > 0 && rtspCameraState.height > 0) {
                                            Text(
                                                stringResource(R.string.rtsp_resolution, rtspCameraState.width, rtspCameraState.height),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else if (rtspAdvertisedHost.isBlank()) {
                                        Text(
                                            stringResource(R.string.rtsp_no_ipv4),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.rtsp_clients, rtspCameraState.clientCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("HomePanel RTSP", endpoint))
                                            rtspCopied = true
                                        }
                                    ) {
                                        Text(if (rtspCopied) stringResource(R.string.rtsp_copied) else stringResource(R.string.rtsp_copy))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val yaml = frigateYaml(endpoint)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("HomePanel Frigate YAML", yaml))
                                            frigateCopied = true
                                        }
                                    ) {
                                        Text(if (frigateCopied) stringResource(R.string.frigate_copied) else stringResource(R.string.frigate_copy))
                                    }
                                }
                                else -> Text(
                                    stringResource(R.string.rtsp_save_to_start),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(stringResource(R.string.mqtt_device_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.mqtt_device_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.mqtt_device_enable),
                    subtitle = stringResource(R.string.mqtt_device_enable_hint),
                    checked = mqttDiscoveryEnabled,
                    onCheckedChange = { enabled ->
                        mqttDiscoveryEnabled = enabled
                        if (enabled && mqttHost.isBlank()) {
                            mqttHost = deriveHost(baseUrl)
                        }
                        if (enabled && mqttPort.isBlank()) mqttPort = if (mqttTls) "8883" else "1883"
                    }
                )
                if (mqttDiscoveryEnabled) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.mqtt_tls),
                        subtitle = stringResource(R.string.mqtt_tls_hint),
                        checked = mqttTls,
                        onCheckedChange = { enabled ->
                            mqttTls = enabled
                            if (mqttPort == "1883" || mqttPort == "8883") {
                                mqttPort = if (enabled) "8883" else "1883"
                            }
                        }
                    )
                    OutlinedTextField(
                        value = mqttHost,
                        onValueChange = { mqttHost = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.mqtt_host)) },
                        placeholder = { Text(deriveHost(baseUrl).ifBlank { "192.168.1.10" }) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mqttPort,
                        onValueChange = { mqttPort = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.mqtt_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mqttUsername,
                        onValueChange = { mqttUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.mqtt_username)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mqttPassword,
                        onValueChange = { mqttPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.mqtt_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(stringResource(R.string.mqtt_status), style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    mqttDeviceState.connected -> stringResource(R.string.mqtt_connected)
                                    !mqttDeviceState.errorMessage.isNullOrBlank() -> mqttDeviceState.errorMessage.orEmpty()
                                    else -> stringResource(R.string.mqtt_save_to_connect)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!mqttDeviceState.errorMessage.isNullOrBlank() && !mqttDeviceState.connected)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            mqttDeviceState.brokerUri?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            mqttDeviceState.lanTransport?.let { transport ->
                                Text(
                                    stringResource(
                                        R.string.network_diag_transport,
                                        transport,
                                        mqttDeviceState.localIpv4.orEmpty()
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            mqttDeviceState.localIpv4?.let {
                                Text(stringResource(R.string.network_diag_ipv4, it), style = MaterialTheme.typography.labelSmall)
                            }
                            mqttDeviceState.resolvedAddress?.let {
                                Text(stringResource(R.string.network_diag_resolved, it), style = MaterialTheme.typography.labelSmall)
                            }
                            mqttDeviceState.tcpReachable?.let { reachable ->
                                Text(
                                    stringResource(if (reachable) R.string.network_diag_tcp_ok else R.string.network_diag_tcp_fail),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(stringResource(R.string.display_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.display_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.auto_brightness_title),
                    subtitle = stringResource(R.string.auto_brightness_subtitle),
                    checked = autoBrightnessEnabled,
                    onCheckedChange = { autoBrightnessEnabled = it }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.kiosk_title),
                    subtitle = stringResource(R.string.kiosk_subtitle),
                    checked = kioskModeEnabled,
                    onCheckedChange = { kioskModeEnabled = it }
                )
                if (kioskModeEnabled) {
                    OutlinedTextField(
                        value = settingsPin,
                        onValueChange = { settingsPin = it.filter(Char::isDigit).take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.kiosk_pin)) },
                        supportingText = { Text(stringResource(R.string.kiosk_pin_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                }

                if (compactDisplaySettings) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeoutField(saverMinutes, { saverMinutes = it }, R.string.screensaver_minutes)
                        TimeoutField(sleepMinutes, { sleepMinutes = it }, R.string.sleep_minutes)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            TimeoutField(saverMinutes, { saverMinutes = it }, R.string.screensaver_minutes)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            TimeoutField(sleepMinutes, { sleepMinutes = it }, R.string.sleep_minutes)
                        }
                    }
                }

                Text(
                    stringResource(R.string.sleep_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()
                Text(stringResource(R.string.network_diagnostics_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.network_diagnostics_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { onRunNetworkDiagnostics(draftSettings()) },
                    enabled = !networkDiagnosticsState.isRunning && baseUrl.isNotBlank() && token.isNotBlank()
                ) {
                    Text(
                        if (networkDiagnosticsState.isRunning) stringResource(R.string.network_diagnostics_running)
                        else stringResource(R.string.network_diagnostics_run)
                    )
                }
                if (networkDiagnosticsState.items.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            networkDiagnosticsState.items.forEach { item ->
                                val prefix = when (item.status) {
                                    DiagnosticStatus.OK -> "✓"
                                    DiagnosticStatus.FAILED -> "✕"
                                    DiagnosticStatus.SKIPPED -> "–"
                                    DiagnosticStatus.RUNNING -> "…"
                                }
                                Column {
                                    Text(
                                        "$prefix ${diagnosticLabel(item.key)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (item.status == DiagnosticStatus.FAILED) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    val latency = item.latencyMs?.let { " · ${it} ms" }.orEmpty()
                                    Text(
                                        item.detail + latency,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
                SettingsSwitchRow(
                    title = stringResource(R.string.update_auto_check),
                    subtitle = stringResource(R.string.update_auto_check_hint),
                    checked = updateChecksEnabled,
                    onCheckedChange = { updateChecksEnabled = it }
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val info = updateState.info
                        Text(
                            when {
                                updateState.isLoading -> stringResource(R.string.update_checking)
                                updateState.isDownloading -> stringResource(R.string.update_downloading)
                                info?.available == true -> stringResource(R.string.update_available, info.latestVersion.orEmpty())
                                info?.latestVersion != null -> stringResource(R.string.update_current, info.currentVersion)
                                else -> stringResource(R.string.update_release_needed)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        updateState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCheckUpdate, enabled = !updateState.isLoading && !updateState.isDownloading) {
                                Text(stringResource(R.string.update_check))
                            }
                            if (info?.available == true && !info.apkDownloadUrl.isNullOrBlank()) {
                                Button(onClick = onDownloadUpdate, enabled = !updateState.isDownloading) {
                                    Text(stringResource(R.string.update_install))
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onSave(draftSettings())
                        },
                        enabled = baseUrl.isNotBlank() && token.isNotBlank() && entityId.isNotBlank() &&
                            (!mqttDiscoveryEnabled || mqttHost.isNotBlank()),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save_connect))
                    }

                    if (canCancel) {
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }

                if (canCancel) {
                    OutlinedButton(onClick = onClear) {
                        Text(stringResource(R.string.clear_config))
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showHardwareDiagnostics) {
        HardwareDiagnosticsDialog(onDismiss = { showHardwareDiagnostics = false })
    }
}

@Composable
private fun diagnosticLabel(key: String): String = when (key) {
    "lan" -> stringResource(R.string.network_diagnostics_lan)
    "home_assistant" -> stringResource(R.string.network_diagnostics_ha)
    "weather_ha" -> stringResource(R.string.network_diagnostics_weather_ha)
    "open_meteo" -> stringResource(R.string.network_diagnostics_open_meteo)
    "osm" -> stringResource(R.string.network_diagnostics_osm)
    "github" -> stringResource(R.string.network_diagnostics_github)
    "mqtt" -> stringResource(R.string.network_diagnostics_mqtt)
    "rtsp" -> stringResource(R.string.network_diagnostics_rtsp)
    else -> key
}

@Composable
private fun EntityCombo(
    selectedId: String,
    choices: List<Pair<String, String>>,
    emptyLabel: String,
    noneLabel: String? = null,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.first == selectedId }
    val title = when {
        selected != null -> selected.second
        selectedId.isNotBlank() -> selectedId
        noneLabel != null -> noneLabel
        else -> emptyLabel
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (selected != null) {
                    Text(
                        selected.first,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("▼")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)
        ) {
            if (noneLabel != null) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = {
                        onSelect("")
                        expanded = false
                    }
                )
            }
            choices.forEach { (entityId, label) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entityId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(entityId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EntityMultiSelect(
    selectedIds: Set<String>,
    choices: List<Pair<String, String>>,
    onSelectionChange: (Set<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                if (selectedIds.isEmpty()) stringResource(R.string.wake_none)
                else stringResource(R.string.wake_multi_selected, selectedIds.size),
                style = MaterialTheme.typography.bodyMedium
            )
            if (selectedIds.isNotEmpty()) {
                Text(
                    selectedIds.take(3).joinToString(" · ") + if (selectedIds.size > 3) " …" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("▼")
    }

    if (expanded) {
        val knownIds = choices.mapTo(hashSetOf()) { it.first }
        val allChoices = choices + selectedIds.filter { it !in knownIds }.map { it to it }
        val filtered = allChoices.filter { (id, label) ->
            query.isBlank() || id.contains(query, ignoreCase = true) || label.contains(query, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.wake_multi_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.search)) },
                        placeholder = { Text("Frigate, motion, hall…") },
                        singleLine = true
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        filtered.forEach { (entityId, label) ->
                            val checked = entityId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = LinkedHashSet(selectedIds)
                                        if (checked) next.remove(entityId) else next.add(entityId)
                                        onSelectionChange(next)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        val next = LinkedHashSet(selectedIds)
                                        if (checked) next.remove(entityId) else next.add(entityId)
                                        onSelectionChange(next)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    if (label != entityId) {
                                        Text(
                                            entityId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (filtered.isEmpty()) {
                            Text(
                                stringResource(R.string.no_results),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = { onSelectionChange(emptySet()) }) {
                        Text(stringResource(R.string.clear_selection))
                    }
                }
            }
        )
    }
}

@Composable
private fun HardwareDiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val proximity = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, false)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }
    val light = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }
    val totalSensors = remember(sensorManager) { sensorManager.getSensorList(Sensor.TYPE_ALL).size }
    val cameraSummary = remember(context) {
        runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var front = 0
            var back = 0
            manager.cameraIdList.forEach { id ->
                when (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> front++
                    CameraCharacteristics.LENS_FACING_BACK -> back++
                }
            }
            Triple(manager.cameraIdList.size, front, back)
        }.getOrDefault(Triple(0, 0, 0))
    }
    val batteryPercent = remember(context) {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    val charging = remember(context) {
        val intent = context.registerReceiver(null, IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }
    val memoryText = remember(context) {
        runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            val mb = info.availMem / (1024L * 1024L)
            "$mb MB"
        }.getOrDefault("—")
    }

    var proximityEvents by remember { mutableStateOf(0) }
    var proximityDistance by remember { mutableStateOf<Float?>(null) }
    var lightEvents by remember { mutableStateOf(0) }
    var lux by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(proximity, light) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_PROXIMITY -> {
                        proximityDistance = event.values.firstOrNull()
                        proximityEvents += 1
                    }
                    Sensor.TYPE_LIGHT -> {
                        lux = event.values.firstOrNull()
                        lightEvents += 1
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (proximity != null) runCatching {
            sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_UI)
        }
        if (light != null) runCatching {
            sensorManager.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { runCatching { sensorManager.unregisterListener(listener) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hardware_diagnostics_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.titleSmall)
                Text("Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}")
                HorizontalDivider()
                Text(stringResource(R.string.hardware_sensors_total, totalSensors))
                Text(
                    stringResource(
                        R.string.hardware_proximity_line,
                        proximity?.name ?: stringResource(R.string.not_available),
                        proximityEvents,
                        proximityDistance?.let { "%.2f cm".format(it) } ?: "—"
                    )
                )
                Text(
                    stringResource(
                        R.string.hardware_light_line,
                        light?.name ?: stringResource(R.string.not_available),
                        lightEvents,
                        lux?.let { "%.1f lx".format(it) } ?: "—"
                    )
                )
                HorizontalDivider()
                Text(stringResource(R.string.hardware_camera_line, cameraSummary.first, cameraSummary.second, cameraSummary.third))
                Text(stringResource(R.string.hardware_battery_line, batteryPercent, if (charging) stringResource(R.string.yes) else stringResource(R.string.no)))
                Text(stringResource(R.string.hardware_memory_line, memoryText))
                if (proximityEvents == 0) {
                    Text(
                        stringResource(R.string.hardware_proximity_no_events_hint),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun ProximitySensorStatus(enabled: Boolean) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensor = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, false)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }
    var registered by remember(sensor, enabled) { mutableStateOf(false) }
    var lastDistance by remember(sensor, enabled) { mutableStateOf<Float?>(null) }
    var near by remember(sensor, enabled) { mutableStateOf<Boolean?>(null) }
    var eventCount by remember(sensor, enabled) { mutableStateOf(0) }

    DisposableEffect(sensor, enabled) {
        if (!enabled || sensor == null) {
            registered = false
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values.firstOrNull() ?: return
                val maxRange = event.sensor.maximumRange.takeIf { it > 0f } ?: 5f
                lastDistance = distance
                near = distance >= 0f && distance < maxRange
                eventCount += 1
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        registered = runCatching {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }.getOrDefault(false)
        onDispose {
            runCatching { sensorManager.unregisterListener(listener) }
            registered = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.proximity_sensor_status_title), style = MaterialTheme.typography.titleSmall)
            if (sensor == null) {
                Text(
                    stringResource(R.string.proximity_sensor_missing),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "${sensor.name} · ${sensor.vendor}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(
                        R.string.proximity_sensor_details,
                        sensor.maximumRange,
                        if (sensor.isWakeUpSensor) stringResource(R.string.yes) else stringResource(R.string.no)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sensor.name.contains("Palm Proximity", ignoreCase = true) ||
                    sensor.vendor.contains("Samsung", ignoreCase = true) && sensor.name.contains("Proximity", ignoreCase = true)
                ) {
                    Text(
                        stringResource(R.string.proximity_sensor_samsung_palm_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val stateText = when {
                    !enabled -> stringResource(R.string.proximity_sensor_disabled)
                    !registered -> stringResource(R.string.proximity_sensor_registration_failed)
                    near == true -> stringResource(R.string.proximity_sensor_near)
                    near == false -> stringResource(R.string.proximity_sensor_far)
                    else -> stringResource(R.string.proximity_sensor_waiting)
                }
                Text(stateText, style = MaterialTheme.typography.bodySmall)
                lastDistance?.let {
                    Text(
                        stringResource(R.string.proximity_sensor_distance, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.proximity_sensor_events, eventCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}


private fun frigateYaml(endpoint: String): String = """go2rtc:
  streams:
    homepanel_front: $endpoint

cameras:
  homepanel_front:
    ffmpeg:
      inputs:
        - path: rtsp://127.0.0.1:8554/homepanel_front
          input_args: preset-rtsp-restream
          roles:
            - detect
    detect:
      width: 1280
      height: 720
""".trim()

private fun deriveHost(baseUrl: String): String = runCatching {
    val normalized = if (baseUrl.contains("://")) baseUrl else "http://$baseUrl"
    URI(normalized).host.orEmpty()
}.getOrDefault("")

@Composable
private fun TimeoutField(value: String, onValueChange: (String) -> Unit, labelRes: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(3)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        supportingText = { Text(stringResource(R.string.zero_disables)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}
