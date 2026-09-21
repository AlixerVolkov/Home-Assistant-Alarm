package dev.homepanel.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.homepanel.app.GuestWifiUiState
import dev.homepanel.app.HouseSummaryUiState
import dev.homepanel.app.R
import dev.homepanel.app.WeatherUiState
import dev.homepanel.app.data.PanelEvent
import dev.homepanel.app.data.PanelSettings
import dev.homepanel.app.network.AlarmAction
import dev.homepanel.app.network.AlarmEntityState
import dev.homepanel.app.network.AlarmFeatures
import dev.homepanel.app.network.ConnectionStatus
import dev.homepanel.app.network.DailyForecast
import dev.homepanel.app.network.HomeAssistantConnectionState
import dev.homepanel.app.network.GuestVoucherState
import java.time.Instant
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
    guestWifiState: GuestWifiUiState,
    houseSummaryState: HouseSummaryUiState,
    history: List<PanelEvent>,
    onAction: (AlarmAction, String?) -> Unit,
    onCreateGuestVoucher: () -> Unit,
    onDeleteGuestVoucher: () -> Unit,
    onRefreshGuestQr: () -> Unit,
    onReconnect: () -> Unit,
    onRefreshWeather: () -> Unit,
    onRefreshHouse: () -> Unit,
    onClearHistory: () -> Unit,
    onSettings: () -> Unit
) {
    val alarm = connectionState.alarm
    var pinAction by remember { mutableStateOf<AlarmAction?>(null) }
    var showGuestWifi by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(showGuestWifi) {
        if (showGuestWifi) onRefreshGuestQr()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxWidth < 720.dp || maxHeight < 520.dp
        val outerPadding = if (maxWidth < 480.dp) 10.dp else 16.dp

        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(outerPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardHeader(
                    alarm = alarm,
                    fallbackName = settings.alarmEntityId,
                    connectionState = connectionState,
                    weatherState = weatherState,
                    compact = true,
                    onReconnect = onReconnect,
                    onRefreshWeather = onRefreshWeather,
                    onHistory = { showHistory = true },
                    onSettings = onSettings
                )
                ErrorBlock(connectionState)
                ContextStatusBanner(alarm)
                if (!settings.guestVoucherSensorEntityId.isNullOrBlank()) {
                    GuestWifiEntryCard(
                        voucher = connectionState.guestVoucher,
                        pending = connectionState.pendingGuestVoucher,
                        deleting = connectionState.pendingGuestVoucherDelete,
                        onClick = { showGuestWifi = true }
                    )
                }
                HouseSummaryCard(houseSummaryState, onRefreshHouse)
                AlarmStateCard(alarm = alarm, modifier = Modifier.fillMaxWidth())
                AlarmActionsPanel(
                    alarm = alarm,
                    connectionState = connectionState,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                    onActionRequested = { action ->
                        if (alarm?.requiresCode(action) == true) pinAction = action else onAction(action, null)
                    }
                )
                ForecastStrip(weatherState, onRefreshWeather)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = outerPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardHeader(
                    alarm = alarm,
                    fallbackName = settings.alarmEntityId,
                    connectionState = connectionState,
                    weatherState = weatherState,
                    compact = false,
                    onReconnect = onReconnect,
                    onRefreshWeather = onRefreshWeather,
                    onHistory = { showHistory = true },
                    onSettings = onSettings
                )
                ErrorBlock(connectionState)
                ContextStatusBanner(alarm)
                if (!settings.guestVoucherSensorEntityId.isNullOrBlank()) {
                    GuestWifiEntryCard(
                        voucher = connectionState.guestVoucher,
                        pending = connectionState.pendingGuestVoucher,
                        deleting = connectionState.pendingGuestVoucherDelete,
                        onClick = { showGuestWifi = true }
                    )
                }
                HouseSummaryCard(houseSummaryState, onRefreshHouse)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AlarmStateCard(
                        alarm = alarm,
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                    )
                    AlarmActionsPanel(
                        alarm = alarm,
                        connectionState = connectionState,
                        compact = false,
                        modifier = Modifier
                            .weight(1.65f)
                            .fillMaxHeight(),
                        onActionRequested = { action ->
                            if (alarm?.requiresCode(action) == true) pinAction = action else onAction(action, null)
                        }
                    )
                }
                ForecastStrip(weatherState, onRefreshWeather)
            }
        }
    }

    if (showGuestWifi) {
        GuestWifiDialog(
            voucher = connectionState.guestVoucher,
            guestWifiState = guestWifiState,
            pending = connectionState.pendingGuestVoucher,
            deleting = connectionState.pendingGuestVoucherDelete,
            errorMessage = connectionState.guestErrorMessage,
            qrEntityId = settings.guestQrImageEntityId,
            canDelete = !settings.guestDeleteButtonEntityId.isNullOrBlank(),
            onCreate = onCreateGuestVoucher,
            onDelete = onDeleteGuestVoucher,
            onRefreshQr = onRefreshGuestQr,
            onDismiss = { showGuestWifi = false }
        )
    }

    if (showHistory) {
        HistoryDialog(
            events = history,
            onClear = onClearHistory,
            onDismiss = { showHistory = false }
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
private fun ErrorBlock(connectionState: HomeAssistantConnectionState) {
    val error = connectionState.actionErrorMessage ?: connectionState.errorMessage
    if (!error.isNullOrBlank()) ErrorBanner(error)
}

@Composable
private fun ContextStatusBanner(alarm: AlarmEntityState?) {
    val state = alarm?.state ?: return
    if (state !in setOf("triggered", "pending", "arming", "disarming")) return
    val critical = state == "triggered"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (critical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (critical) "🚨" else "⏳", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(alarmStateLabel(state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (critical) stringResource(R.string.context_alarm_triggered) else stringResource(R.string.context_alarm_transition),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun HouseSummaryCard(state: HouseSummaryUiState, onRefresh: () -> Unit) {
    val summary = state.summary
    Card(modifier = Modifier.fillMaxWidth(), onClick = onRefresh) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.house_status), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("↻", style = MaterialTheme.typography.titleMedium)
            }
            if (summary == null) {
                Text(state.errorMessage ?: stringResource(R.string.house_status_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("🚪 ${summary.openDoors}")
                    Text("🪟 ${summary.openWindows}")
                    Text("💡 ${summary.lightsOn}")
                    Text("👤 ${summary.personsHome}")
                    summary.temperatureC?.let { Text("🌡 ${String.format(Locale.getDefault(), "%.1f", it)}°C") }
                }
                if (summary.openEntityNames.isNotEmpty()) {
                    Text(
                        stringResource(R.string.house_open_entities, summary.openEntityNames.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDialog(events: List<PanelEvent>, onClear: () -> Unit, onDismiss: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🕘 ${stringResource(R.string.history_title)}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (events.isEmpty()) {
                    Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    events.forEach { event ->
                        val time = Instant.ofEpochMilli(event.timestampEpochMs)
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime()
                            .format(formatter)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Text(historyIcon(event.kind), style = MaterialTheme.typography.titleMedium)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.message, style = MaterialTheme.typography.bodyMedium)
                                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            if (events.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.history_clear)) }
        }
    )
}

private fun historyIcon(kind: String): String = when (kind) {
    "alarm" -> "🛡️"
    "guest" -> "📶"
    "connection" -> "🔗"
    "update" -> "⬆️"
    else -> "•"
}

@Composable
private fun DashboardHeader(
    alarm: AlarmEntityState?,
    fallbackName: String,
    connectionState: HomeAssistantConnectionState,
    weatherState: WeatherUiState,
    compact: Boolean,
    onReconnect: () -> Unit,
    onRefreshWeather: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alarm?.friendlyName ?: fallbackName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    ConnectionChip(connectionState.status, onReconnect)
                }
                ClockBlock(weatherState = weatherState)
                IconButton(onClick = onHistory) {
                    Text("🕘", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onSettings) {
                    Text("⚙️", style = MaterialTheme.typography.headlineSmall)
                }
            }
            CurrentWeatherBlock(
                weatherState = weatherState,
                modifier = Modifier.fillMaxWidth(),
                onRefresh = onRefreshWeather
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = alarm?.friendlyName ?: fallbackName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                ConnectionChip(connectionState.status, onReconnect)
            }
            ClockBlock(weatherState = weatherState, modifier = Modifier.weight(0.85f))
            CurrentWeatherBlock(
                weatherState = weatherState,
                modifier = Modifier.weight(1.25f),
                onRefresh = onRefreshWeather
            )
            IconButton(onClick = onHistory) {
                Text("🕘", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = onSettings) {
                Text("⚙️", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun ConnectionChip(status: ConnectionStatus, onReconnect: () -> Unit) {
    val connected = status == ConnectionStatus.CONNECTED
    Surface(
        onClick = { if (!connected && status != ConnectionStatus.CONNECTING) onReconnect() },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (connected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(if (connected) "●" else "○")
            Text(connectionStatusLabel(status), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GuestWifiEntryCard(
    voucher: GuestVoucherState?,
    pending: Boolean,
    deleting: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📶", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.guest_wifi_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val detail = when {
                    deleting -> stringResource(R.string.guest_wifi_deleting)
                    pending -> stringResource(R.string.guest_wifi_creating)
                    !voucher?.wlanName.isNullOrBlank() -> voucher?.wlanName.orEmpty()
                    !voucher?.code.isNullOrBlank() -> stringResource(R.string.guest_wifi_voucher_ready)
                    else -> stringResource(R.string.guest_wifi_tap)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun GuestWifiDialog(
    voucher: GuestVoucherState?,
    guestWifiState: GuestWifiUiState,
    pending: Boolean,
    deleting: Boolean,
    errorMessage: String?,
    qrEntityId: String?,
    canDelete: Boolean,
    onCreate: () -> Unit,
    onDelete: () -> Unit,
    onRefreshQr: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(guestWifiState.qrImageBytes) {
        guestWifiState.qrImageBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val busy = pending || deleting
    val voucherCode = voucher?.code.orEmpty()
    val shareText = buildString {
        append(voucher?.wlanName?.takeIf { it.isNotBlank() } ?: "Guest Wi-Fi")
        if (voucherCode.isNotBlank()) append("\nVoucher: $voucherCode")
        voucher?.duration?.takeIf { it.isNotBlank() }?.let { append("\nDuration: $it") }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📶", style = MaterialTheme.typography.headlineLarge)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.visitor_mode_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(voucher?.wlanName ?: stringResource(R.string.guest_wifi_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) }
                }

                if (voucherCode.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.guest_wifi_code), style = MaterialTheme.typography.titleMedium)
                            Text(voucherCode, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Guest Wi-Fi voucher", voucherCode))
                                }) { Text(stringResource(R.string.copy_code)) }
                                OutlinedButton(onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_voucher)))
                                }) { Text(stringResource(R.string.share_voucher)) }
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.guest_wifi_no_voucher), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }

                when {
                    guestWifiState.isLoadingQr -> CircularProgressIndicator(modifier = Modifier.size(52.dp))
                    qrBitmap != null -> Image(
                        bitmap = qrBitmap,
                        contentDescription = stringResource(R.string.guest_wifi_qr),
                        modifier = Modifier.sizeIn(minWidth = 220.dp, minHeight = 220.dp, maxWidth = 460.dp, maxHeight = 460.dp)
                    )
                    else -> {
                        Text(
                            guestWifiState.errorMessage ?: stringResource(R.string.guest_wifi_qr_unavailable),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!qrEntityId.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.guest_wifi_enable_qr_entity, qrEntityId),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onRefreshQr) { Text(stringResource(R.string.retry)) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    voucher?.duration?.let { Text(stringResource(R.string.guest_wifi_duration, it)) }
                    voucher?.status?.let { Text(stringResource(R.string.guest_wifi_status, it)) }
                }
                if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canDelete && voucherCode.isNotBlank()) {
                        OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                            Text(if (deleting) stringResource(R.string.guest_wifi_deleting) else stringResource(R.string.guest_wifi_delete))
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Button(onClick = onCreate, enabled = !busy) {
                        Text(if (pending) stringResource(R.string.guest_wifi_creating) else stringResource(R.string.guest_wifi_create))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.guest_wifi_delete_confirm_title)) },
            text = { Text(stringResource(R.string.guest_wifi_delete_confirm_message, voucherCode)) },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.guest_wifi_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ClockBlock(weatherState: WeatherUiState, modifier: Modifier = Modifier) {
    val zoneId = remember(weatherState.forecast?.timezoneId) {
        runCatching { ZoneId.of(weatherState.forecast?.timezoneId ?: ZoneId.systemDefault().id) }
            .getOrDefault(ZoneId.systemDefault())
    }
    var now by remember(zoneId) { mutableStateOf(LocalDateTime.now(zoneId)) }

    LaunchedEffect(zoneId) {
        while (true) {
            now = LocalDateTime.now(zoneId)
            delay(1_000L)
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember(Locale.getDefault()) {
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = now.format(timeFormatter),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = now.format(dateFormatter),
            style = MaterialTheme.typography.labelSmall,
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
    val forecast = weatherState.forecast
    val current = forecast?.current

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                current == null && weatherState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.weather_loading))
                }
                current != null -> {
                    Text(weatherEmoji(current.weatherCode), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = forecast?.locationLabel.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                        Text(
                            text = "${current.temperatureC.roundToInt()}°C · ${weatherDescription(current.weatherCode)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(
                                R.string.weather_feels_wind,
                                current.apparentTemperatureC.roundToInt(),
                                current.windSpeedKmh.roundToInt()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = forecast?.sourceLabel.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onRefresh) { Text("↻") }
                }
                else -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.weather_unavailable), style = MaterialTheme.typography.labelLarge)
                        Text(
                            weatherState.errorMessage ?: stringResource(R.string.weather_unavailable),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = onRefresh) { Text("↻") }
                }
            }
        }
    }
}

@Composable
private fun AlarmStateCard(alarm: AlarmEntityState?, modifier: Modifier = Modifier) {
    val triggered = alarm?.state == "triggered"
    Card(
        modifier = modifier.heightIn(min = 150.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (triggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stateIcon(alarm?.state), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = alarmStateLabel(alarm?.state),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            alarm?.changedBy?.let { changedBy ->
                Spacer(Modifier.height(6.dp))
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
    compact: Boolean,
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

    BoxWithConstraints(modifier = modifier) {
        val columns = when {
            maxWidth < 360.dp -> 2
            maxWidth < 620.dp -> 3
            else -> 3
        }
        val rows = actions.chunked(columns)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            connectionState.pendingAction?.let { pending ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sending_action, alarmActionLabel(pending)))
                }
            }

            rows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                .height(if (compact) 88.dp else 110.dp),
                            onClick = { onActionRequested(action) }
                        )
                    }
                    repeat(columns - rowActions.size) { Spacer(Modifier.weight(1f)) }
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
    val colors = if (active) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = modifier,
        colors = colors,
        enabled = enabled || active,
        onClick = { if (enabled) onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(alarmActionIcon(action), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(5.dp))
            Text(
                text = alarmActionLabel(action),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (active) {
                Text(stringResource(R.string.current_mode), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ForecastStrip(weatherState: WeatherUiState, onRefresh: () -> Unit) {
    val daily = weatherState.forecast?.daily.orEmpty()
    Card(modifier = Modifier.fillMaxWidth()) {
        if (daily.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = weatherState.errorMessage ?: stringResource(R.string.weather_loading),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                daily.forEach { day ->
                    ForecastDay(day = day, modifier = Modifier.widthIn(min = 100.dp, max = 130.dp))
                }
            }
        }
    }
}

@Composable
private fun ForecastDay(day: DailyForecast, modifier: Modifier = Modifier) {
    val parsedDate = remember(day.date) { runCatching { LocalDate.parse(day.date) }.getOrNull() }
    val formatter = remember(Locale.getDefault()) { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    val dayLabel = parsedDate?.format(formatter)?.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
    } ?: day.date

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        title = { Text(stringResource(R.string.enter_pin_for, alarmActionLabel(action))) },
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
                    NumericKeypad(code = code, onCodeChange = { code = it })
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.pin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(code) }, enabled = code.isNotBlank()) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun NumericKeypad(code: String, onCodeChange: (String) -> Unit) {
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
