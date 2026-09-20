package dev.homepanel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.homepanel.app.R
import dev.homepanel.app.WeatherUiState
import dev.homepanel.app.network.AlarmEntityState
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ScreenSaverScreen(
    alarm: AlarmEntityState?,
    weatherState: WeatherUiState,
    deepSleep: Boolean
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(weatherState.forecast?.timezoneId) {
        while (true) {
            val zoneId = runCatching {
                ZoneId.of(weatherState.forecast?.timezoneId ?: ZoneId.systemDefault().id)
            }.getOrDefault(ZoneId.systemDefault())
            now = LocalDateTime.now(zoneId)
            delay(1_000L)
        }
    }

    val alignment = remember(now.minute) {
        BURN_IN_ALIGNMENTS[(now.minute / 3) % BURN_IN_ALIGNMENTS.size]
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(28.dp),
        contentAlignment = if (deepSleep) Alignment.Center else alignment
    ) {
        if (deepSleep) {
            Text(
                text = stringResource(R.string.tap_to_wake),
                color = Color(0xFF222222),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )

                val current = weatherState.forecast?.current
                if (current != null) {
                    Spacer(Modifier.size(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(weatherEmojiSaver(current.weatherCode), style = MaterialTheme.typography.displaySmall)
                        Text(
                            text = "${current.temperatureC.roundToInt()}°C",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Text(
                        text = weatherState.forecast?.locationLabel.orEmpty(),
                        color = Color(0xFF9E9E9E),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.size(4.dp))
                Text(
                    text = "${stateIconSaver(alarm?.state)}  ${alarmStateSaver(alarm?.state)}",
                    color = if (alarm?.state == "triggered") Color(0xFFFF6B6B) else Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun alarmStateSaver(state: String?): String = when (state) {
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

private fun stateIconSaver(state: String?): String = when (state) {
    "disarmed" -> "🔓"
    "triggered" -> "🚨"
    "arming", "disarming", "pending" -> "⏳"
    else -> "🔒"
}

private fun weatherEmojiSaver(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3, 45, 48 -> "☁️"
    in 51..67, in 80..82 -> "🌧️"
    in 71..77, in 85..86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "🌥️"
}

private val BURN_IN_ALIGNMENTS = listOf(
    Alignment.TopStart,
    Alignment.TopEnd,
    Alignment.Center,
    Alignment.BottomStart,
    Alignment.BottomEnd
)
