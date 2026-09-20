package dev.homepanel.app.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.homepanel.app.DiscoveryState
import dev.homepanel.app.R
import dev.homepanel.app.data.PanelSettings

@Composable
fun SetupScreen(
    initialSettings: PanelSettings?,
    discoveryState: DiscoveryState,
    canCancel: Boolean,
    onDiscover: (String, String) -> Unit,
    onSave: (String, String, String, String?, Int, Int) -> Unit,
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) 16.dp else 32.dp

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
                        if (discoveryState.isLoading) {
                            stringResource(R.string.discovering)
                        } else {
                            stringResource(R.string.discover_alarms)
                        }
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

                Text(stringResource(R.string.wake_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wake_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

                Text(stringResource(R.string.display_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.display_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (maxWidth < 520.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = saverMinutes,
                            onValueChange = { saverMinutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.screensaver_minutes)) },
                            supportingText = { Text(stringResource(R.string.zero_disables)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sleepMinutes,
                            onValueChange = { sleepMinutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.sleep_minutes)) },
                            supportingText = { Text(stringResource(R.string.zero_disables)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = saverMinutes,
                            onValueChange = { saverMinutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.screensaver_minutes)) },
                            supportingText = { Text(stringResource(R.string.zero_disables)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = sleepMinutes,
                            onValueChange = { sleepMinutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.sleep_minutes)) },
                            supportingText = { Text(stringResource(R.string.zero_disables)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
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
                                baseUrl,
                                token,
                                entityId,
                                wakeEntityId.takeIf { it.isNotBlank() },
                                saverMinutes.toIntOrNull() ?: 0,
                                sleepMinutes.toIntOrNull() ?: 0
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
