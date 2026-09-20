package dev.homepanel.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.homepanel.app.R
import dev.homepanel.app.camera.RtspCameraState
import dev.homepanel.app.data.PanelSettings

@Composable
fun SetupScreen(
    initialSettings: PanelSettings?,
    discoveryState: DiscoveryState,
    rtspCameraState: RtspCameraState,
    canCancel: Boolean,
    onDiscover: (String, String) -> Unit,
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
    var wakeEntityId by rememberSaveable(initialSettings?.wakeEntityId) {
        mutableStateOf(initialSettings?.wakeEntityId.orEmpty())
    }
    var saverMinutes by rememberSaveable(initialSettings?.screensaverTimeoutMinutes) {
        mutableStateOf((initialSettings?.screensaverTimeoutMinutes ?: 2).toString())
    }
    var sleepMinutes by rememberSaveable(initialSettings?.sleepTimeoutMinutes) {
        mutableStateOf((initialSettings?.sleepTimeoutMinutes ?: 10).toString())
    }
    var proximityWakeEnabled by rememberSaveable(initialSettings?.proximityWakeEnabled) {
        mutableStateOf(initialSettings?.proximityWakeEnabled ?: true)
    }
    var rtspEnabled by rememberSaveable(initialSettings?.rtspEnabled) {
        mutableStateOf(initialSettings?.rtspEnabled ?: false)
    }
    var rtspPort by rememberSaveable(initialSettings?.rtspPort) {
        mutableStateOf((initialSettings?.rtspPort ?: 8554).toString())
    }
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

    LaunchedEffect(discoveryState.guestWifi) {
        if (guestVoucherSensor.isBlank() && discoveryState.guestWifi.size == 1) {
            val guest = discoveryState.guestWifi.single()
            guestVoucherSensor = guest.voucherSensorEntityId
            guestCreateButton = guest.createButtonEntityId
            guestDeleteButton = guest.deleteButtonEntityId.orEmpty()
            guestQrImage = guest.qrImageEntityId.orEmpty()
        }
    }

    val context = LocalContext.current
    var rtspCopied by rememberSaveable { mutableStateOf(false) }

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
                }

                discoveryState.alarms.forEach { alarm ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { entityId = alarm.entityId }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = entityId == alarm.entityId,
                                onClick = { entityId = alarm.entityId }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(alarm.friendlyName, style = MaterialTheme.typography.titleMedium)
                                Text(alarm.entityId, style = MaterialTheme.typography.bodySmall)
                                Text(alarm.state, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { wakeEntityId = "" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = wakeEntityId.isBlank(),
                            onClick = { wakeEntityId = "" }
                        )
                        Text(stringResource(R.string.wake_none))
                    }
                }

                discoveryState.wakeSensors.forEach { sensor ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { wakeEntityId = sensor.entityId }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = wakeEntityId == sensor.entityId,
                                onClick = { wakeEntityId = sensor.entityId }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sensor.friendlyName, style = MaterialTheme.typography.titleSmall)
                                Text(sensor.entityId, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = wakeEntityId,
                    onValueChange = { wakeEntityId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wake_entity_manual)) },
                    placeholder = { Text("binary_sensor.hall_motion") },
                    singleLine = true
                )

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

                Text(stringResource(R.string.display_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.display_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onSave(
                                PanelSettings(
                                    baseUrl = baseUrl,
                                    accessToken = token,
                                    alarmEntityId = entityId,
                                    wakeEntityId = wakeEntityId.takeIf { it.isNotBlank() },
                                    screensaverTimeoutMinutes = saverMinutes.toIntOrNull() ?: 0,
                                    sleepTimeoutMinutes = sleepMinutes.toIntOrNull() ?: 0,
                                    proximityWakeEnabled = proximityWakeEnabled,
                                    rtspEnabled = rtspEnabled,
                                    rtspPort = rtspPort.toIntOrNull() ?: 8554,
                                    guestVoucherSensorEntityId = guestVoucherSensor.takeIf { it.isNotBlank() },
                                    guestCreateButtonEntityId = guestCreateButton.takeIf { it.isNotBlank() },
                                    guestDeleteButtonEntityId = guestDeleteButton.takeIf { it.isNotBlank() },
                                    guestQrImageEntityId = guestQrImage.takeIf { it.isNotBlank() }
                                )
                            )
                        },
                        enabled = baseUrl.isNotBlank() && token.isNotBlank() && entityId.isNotBlank(),
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
