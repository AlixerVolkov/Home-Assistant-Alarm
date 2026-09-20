package dev.homepanel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.homepanel.app.R
import dev.homepanel.app.WeatherUiState
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.network.AlarmAction
import dev.homepanel.app.network.AlarmEntityState
import dev.homepanel.app.network.AlarmFeatures
import dev.homepanel.app.network.ConnectionStatus
import dev.homepanel.app.network.DailyForecast
import dev.homepanel.app.network.HomeAssistantConnectionState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun AlarmScreen(
    settings: PanelSettings,
    connectionState: HomeAssistantConnectionState,
    weatherState: WeatherUiState,
    onAction: (AlarmAction, String?) -> Unit,
    onReconnect: () -> Unit,
    onRefreshWeather: () -> Unit,
    onSettings: () -> Unit
) {
    val alarm = connectionState.alarm
    var pinAction by remember { mutableStateOf<AlarmAction?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DashboardHeader(
            alarm = alarm,
            fallbackName = settings.alarmEntityId,
            connectionState = connectionState,
            weatherState = weatherState,
            onReconnect = onReconnect,
            onRefreshWeather = onRefreshWeather,
            onSettings = onSettings
        )

        val error = connectionState.actionErrorMessage ?: connectionState.errorMessage
        if (!error.isNullOrBlank()) {
            ErrorBanner(error)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AlarmStateCard(
                alarm = alarm,
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
            )

            AlarmActionsPanel(
                alarm = alarm,
                connectionState = connectionState,
                modifier = Modifier
                    .weight(1.6f)
                    .fillMaxHeight(),
                onActionRequested = { action ->
                    if (alarm?.requiresCode(action) == true) {
                        pinAction = action
                    } else {
                        onAction(action, null)
                    }
                }
            )
        }

        ForecastStrip(
            weatherState = weatherState,
            onRefresh = onRefreshWeather
        )
    }

    pinAction?.let { action ->
        PinDialog(
            action = action,
            numeric = alarm?.codeFormat == "number",
            onDismiss = { pinAction = null },
            onConfirm = { code ->
                pinAction = null
                onAction(action, code)
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    alarm: AlarmEntityState?,
    fallbackName: String,
    connectionState: HomeAssistantConnectionState,
    weatherState: WeatherUiState,
    onReconnect: () -> Unit,
    onRefreshWeather: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1.15f)) {
            Text(
                text = alarm?.friendlyName ?: fallbackName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            ConnectionChip(connectionState.status, onReconnect)
        }

        ClockBlock(modifier = Modifier.weight(0.85f))

        CurrentWeatherBlock(
            weatherState = weatherState,
            modifier = Modifier.weight(1.15f),
            onRefresh = onRefreshWeather
        )

        IconButton(onClick = onSettings) {
            Text(
                text = "⚙️",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
private fun ConnectionChip(
    status: ConnectionStatus,
    onReconnect: () -> Unit
) {
    val connected = status == ConnectionStatus.CONNECTED
    Surface(
        onClick = {
            if (!connected && status != ConnectionStatus.CONNECTING) onReconnect()
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (connected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = if (connected) "●" else "○",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = connectionStatusLabel(status),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ClockBlock(modifier: Modifier = Modifier) {
    var now by remember {
        mutableStateOf(LocalDateTime.now(ZoneId.of("Europe/Brussels")))
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now(ZoneId.of("Europe/Brussels"))
            delay(1_000L)
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = now.format(timeFormatter),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = now.format(dateFormatter).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CurrentWeatherBlock(
    weatherState: WeatherUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit
) {
    val current = weatherState.forecast?.current

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (current == null && weatherState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.weather_loading))
            } else if (current != null) {
                Text(
                    text = weatherEmoji(current.weatherCode),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.weather_ekeren),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${current.temperatureC.roundToInt()}°C · ${weatherDescription(current.weatherCode)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.weather_feels_wind,
                            current.apparentTemperatureC.roundToInt(),
                            current.windSpeedKmh.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.weather_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Text("↻", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.weather_ekeren), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = stringResource(R.string.weather_unavailable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = onRefresh) {
                    Text("↻", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun AlarmStateCard(
    alarm: AlarmEntityState?,
    modifier: Modifier = Modifier
) {
    val state = alarm?.state
    val containerColor = when (state) {
        "triggered" -> MaterialTheme.colorScheme.errorContainer
        "disarmed" -> MaterialTheme.colorScheme.secondaryContainer
        "arming", "disarming", "pending" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stateIcon(state),
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = alarmStateLabel(state),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            alarm?.changedBy?.let { changedBy ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.changed_by, changedBy),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AlarmActionsPanel(
    alarm: AlarmEntityState?,
    connectionState: HomeAssistantConnectionState,
    modifier: Modifier = Modifier,
    onActionRequested: (AlarmAction) -> Unit
) {
    val actions = buildList {
        add(AlarmAction.DISARM)
        if (alarm?.supports(AlarmFeatures.ARM_HOME) == true) add(AlarmAction.ARM_HOME)
        if (alarm?.supports(AlarmFeatures.ARM_AWAY) == true) add(AlarmAction.ARM_AWAY)
        if (alarm?.supports(AlarmFeatures.ARM_NIGHT) == true) add(AlarmAction.ARM_NIGHT)
        if (alarm?.supports(AlarmFeatures.ARM_VACATION) == true) add(AlarmAction.ARM_VACATION)
        if (alarm?.supports(AlarmFeatures.ARM_CUSTOM_BYPASS) == true) add(AlarmAction.ARM_CUSTOM_BYPASS)
    }

    val globallyEnabled = connectionState.status == ConnectionStatus.CONNECTED &&
        alarm != null && connectionState.pendingAction == null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        connectionState.pendingAction?.let { pending ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sending_action, alarmActionLabel(pending)),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        val rows = actions.chunked(3)
        rows.forEach { rowActions ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowActions.forEach { action ->
                    val active = alarm?.state == action.targetState
                    val enabled = globallyEnabled && alarm?.canPerform(action) == true
                    AlarmActionButton(
                        action = action,
                        active = active,
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onActionRequested(action) }
                    )
                }
                repeat(3 - rowActions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AlarmActionButton(
    action: AlarmAction,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = alarmActionIcon(action),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = alarmActionLabel(action),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall
            )
            if (active) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.current_mode),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ForecastStrip(
    weatherState: WeatherUiState,
    onRefresh: () -> Unit
) {
    val daily = weatherState.forecast?.daily.orEmpty()

    Card(modifier = Modifier.fillMaxWidth()) {
        if (daily.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = weatherState.errorMessage ?: stringResource(R.string.weather_loading),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                daily.forEach { day ->
                    ForecastDay(day = day, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ForecastDay(
    day: DailyForecast,
    modifier: Modifier = Modifier
) {
    val parsedDate = remember(day.date) { runCatching { LocalDate.parse(day.date) }.getOrNull() }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    val dayLabel = parsedDate?.format(formatter)?.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
    } ?: day.date

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(dayLabel, style = MaterialTheme.typography.labelLarge)
        Text(weatherEmoji(day.weatherCode), style = MaterialTheme.typography.titleLarge)
        Text(
            text = "${day.maximumC.roundToInt()}° / ${day.minimumC.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "💧 ${day.precipitationProbability}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("⚠️", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PinDialog(
    action: AlarmAction,
    numeric: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember(action) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.enter_pin_for, alarmActionLabel(action)))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (numeric) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (code.isEmpty()) "••••" else "•".repeat(code.length),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    NumericKeypad(
                        code = code,
                        onCodeChange = { code = it }
                    )
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.pin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun NumericKeypad(
    code: String,
    onCodeChange: (String) -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "C" -> onCodeChange("")
                                "⌫" -> onCodeChange(code.dropLast(1))
                                else -> if (code.length < 12) onCodeChange(code + key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

private fun stateIcon(state: String?): String = when (state) {
    "disarmed" -> "🔓"
    "triggered" -> "🚨"
    "arming", "disarming", "pending" -> "⏳"
    else -> "🔒"
}

private fun alarmActionIcon(action: AlarmAction): String = when (action) {
    AlarmAction.DISARM -> "🔓"
    AlarmAction.ARM_HOME -> "🏠"
    AlarmAction.ARM_AWAY -> "🚶"
    AlarmAction.ARM_NIGHT -> "🌙"
    AlarmAction.ARM_VACATION -> "✈️"
    AlarmAction.ARM_CUSTOM_BYPASS -> "🛠️"
}

@Composable
private fun alarmActionLabel(action: AlarmAction): String = when (action) {
    AlarmAction.DISARM -> stringResource(R.string.disarm)
    AlarmAction.ARM_HOME -> stringResource(R.string.arm_home)
    AlarmAction.ARM_AWAY -> stringResource(R.string.arm_away)
    AlarmAction.ARM_NIGHT -> stringResource(R.string.arm_night)
    AlarmAction.ARM_VACATION -> stringResource(R.string.arm_vacation)
    AlarmAction.ARM_CUSTOM_BYPASS -> stringResource(R.string.arm_custom)
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

@Composable
private fun weatherDescription(code: Int): String = when (code) {
    0 -> stringResource(R.string.weather_clear)
    1, 2 -> stringResource(R.string.weather_partly_cloudy)
    3, 45, 48 -> stringResource(R.string.weather_cloudy)
    in 51..67, in 80..82 -> stringResource(R.string.weather_rain)
    in 71..77, in 85..86 -> stringResource(R.string.weather_snow)
    in 95..99 -> stringResource(R.string.weather_storm)
    else -> stringResource(R.string.weather_cloudy)
}

private fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3, 45, 48 -> "☁️"
    in 51..67, in 80..82 -> "🌧️"
    in 71..77, in 85..86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "🌥️"
}
