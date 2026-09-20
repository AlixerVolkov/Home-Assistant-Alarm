package dev.homepanel.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
    onSave: (String, String, String) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 28.dp),
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

        Spacer(Modifier.height(4.dp))

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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }

        discoveryState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (discoveryState.alarms.isNotEmpty()) {
            Text(
                text = stringResource(R.string.select_alarm),
                style = MaterialTheme.typography.titleMedium
            )
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

        if (discoveryState.hasRun &&
            !discoveryState.isLoading &&
            discoveryState.alarms.isEmpty() &&
            discoveryState.errorMessage == null
        ) {
            Text(
                text = stringResource(R.string.no_alarms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSave(baseUrl, token, entityId) },
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
    }
}
