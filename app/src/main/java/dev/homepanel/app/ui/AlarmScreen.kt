package dev.homepanel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.homepanel.app.R
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.network.AlarmAction
import dev.homepanel.app.network.AlarmEntityState
import dev.homepanel.app.network.AlarmFeatures
import dev.homepanel.app.network.ConnectionStatus
import dev.homepanel.app.network.HomeAssistantConnectionState

@Composable
fun AlarmScreen(
    settings: PanelSettings,
    connectionState: HomeAssistantConnectionState,
    onAction: (AlarmAction, String?) -> Unit,
    onReconnect: () -> Unit,
    onSettings: () -> Unit
) {
    val alarm = connectionState.alarm
    var code by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm?.friendlyName ?: settings.alarmEntityId,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = settings.alarmEntityId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onSettings) {
                Text(stringResource(R.string.settings))
            }
        }

        ConnectionBanner(connectionState, onReconnect)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = alarmStateLabel(alarm?.state),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    color = if (alarm?.state == "triggered") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                alarm?.state?.let { rawState ->
                    Text(
                        text = rawState,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val showCode = alarm == null || alarm.codeFormat != null || alarm.codeArmRequired
        if (showCode) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pin)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (alarm?.codeFormat == "number") {
                        KeyboardType.NumberPassword
                    } else {
                        KeyboardType.Password
                    }
                )
            )
        }

        connectionState.actionErrorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val actionsEnabled = connectionState.status == ConnectionStatus.CONNECTED

        Button(
            onClick = {
                onAction(AlarmAction.DISARM, code.takeIf { it.isNotBlank() })
                code = ""
            },
            enabled = actionsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Text(stringResource(R.string.disarm))
        }

        AlarmActionRows(
            alarm = alarm,
            enabled = actionsEnabled,
            onAction = { action ->
                onAction(action, code.takeIf { it.isNotBlank() })
                code = ""
            }
        )

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ConnectionBanner(
    state: HomeAssistantConnectionState,
    onReconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connectionStatusLabel(state.status),
                    style = MaterialTheme.typography.titleMedium
                )
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (state.status == ConnectionStatus.ERROR ||
                state.status == ConnectionStatus.DISCONNECTED
            ) {
                OutlinedButton(onClick = onReconnect) {
                    Text(stringResource(R.string.reconnect))
                }
            }
        }
    }
}

@Composable
private fun AlarmActionRows(
    alarm: AlarmEntityState?,
    enabled: Boolean,
    onAction: (AlarmAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (alarm == null || alarm.supports(AlarmFeatures.ARM_HOME)) {
            ActionButton(
                label = stringResource(R.string.arm_home),
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onAction(AlarmAction.ARM_HOME) }
            )
        }
        if (alarm == null || alarm.supports(AlarmFeatures.ARM_AWAY)) {
            ActionButton(
                label = stringResource(R.string.arm_away),
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onAction(AlarmAction.ARM_AWAY) }
            )
        }
        if (alarm == null || alarm.supports(AlarmFeatures.ARM_NIGHT)) {
            ActionButton(
                label = stringResource(R.string.arm_night),
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onAction(AlarmAction.ARM_NIGHT) }
            )
        }
    }

    val showVacation = alarm?.supports(AlarmFeatures.ARM_VACATION) == true
    val showCustom = alarm?.supports(AlarmFeatures.ARM_CUSTOM_BYPASS) == true
    if (showVacation || showCustom) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showVacation) {
                ActionButton(
                    label = stringResource(R.string.arm_vacation),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(AlarmAction.ARM_VACATION) }
                )
            }
            if (showCustom) {
                ActionButton(
                    label = stringResource(R.string.arm_custom),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(AlarmAction.ARM_CUSTOM_BYPASS) }
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(58.dp)
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun connectionStatusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
    ConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
    ConnectionStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
    ConnectionStatus.ERROR -> stringResource(R.string.status_error)
}

@Composable
private fun alarmStateLabel(state: String?): String = when (state) {
    "disarmed" -> stringResource(R.string.state_disarmed)
    "armed_home" -> stringResource(R.string.state_armed_home)
    "armed_away" -> stringResource(R.string.state_armed_away)
    "armed_night" -> stringResource(R.string.state_armed_night)
    "armed_vacation" -> stringResource(R.string.state_armed_vacation)
    "armed_custom_bypass" -> stringResource(R.string.state_armed_custom)
    "pending" -> stringResource(R.string.state_pending)
    "arming" -> stringResource(R.string.state_arming)
    "disarming" -> stringResource(R.string.state_disarming)
    "triggered" -> stringResource(R.string.state_triggered)
    else -> stringResource(R.string.state_unknown)
}
