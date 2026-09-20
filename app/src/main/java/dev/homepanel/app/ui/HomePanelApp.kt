package dev.homepanel.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.homepanel.app.MainViewModel
import dev.homepanel.app.R
import dev.homepanel.app.PanelDisplayMode

@Composable
fun HomePanelApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val guestWifi by viewModel.guestWifi.collectAsStateWithLifecycle()
    val houseSummary by viewModel.houseSummary.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val ambientLux by viewModel.ambientLux.collectAsStateWithLifecycle()
    val rtspCamera by viewModel.rtspCamera.collectAsStateWithLifecycle()
    val mqttDevice by viewModel.mqttDevice.collectAsStateWithLifecycle()
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    val wakePulse by viewModel.wakePulse.collectAsStateWithLifecycle()

    var showSettingsPin by rememberSaveable { mutableStateOf(false) }
    var pendingInstallerUri by rememberSaveable { mutableStateOf<String?>(null) }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val raw = pendingInstallerUri
        if (raw != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls())
        ) {
            context.launchApkInstaller(Uri.parse(raw))
        }
        pendingInstallerUri = null
        viewModel.consumeInstallerUri()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.refreshDeviceLocation()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onCameraPermissionResult(granted) }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onLocalNetworkPermissionResult(granted) }

    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) {
            viewModel.refreshDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }

        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    LaunchedEffect(settings?.rtspEnabled) {
        if (settings?.rtspEnabled == true && !viewModel.hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Proximity: publish state and wake on FAR -> NEAR while sleeping/saver.
    DisposableEffect(context, settings?.proximityWakeEnabled, displayMode) {
        val shouldListen = settings?.proximityWakeEnabled == true
        if (!shouldListen) return@DisposableEffect onDispose { }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        var wasNear = false
        var lastWakeElapsed = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values.firstOrNull() ?: return
                val isNear = distance < event.sensor.maximumRange
                val now = SystemClock.elapsedRealtime()
                viewModel.onProximityChanged(isNear)
                if (displayMode != PanelDisplayMode.ACTIVE && isNear && !wasNear &&
                    now - lastWakeElapsed >= PROXIMITY_DEBOUNCE_MS
                ) {
                    lastWakeElapsed = now
                    viewModel.onLocalDetection()
                }
                wasNear = isNear
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (proximity != null) sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Ambient-light sensor: drives auto-brightness and is published over MQTT Discovery.
    DisposableEffect(context, settings?.autoBrightnessEnabled, settings?.mqttDiscoveryEnabled) {
        val shouldListen = settings?.autoBrightnessEnabled == true || settings?.mqttDiscoveryEnabled == true
        if (!shouldListen) return@DisposableEffect onDispose { }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let(viewModel::onAmbientLightChanged)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (light != null) sensorManager.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LaunchedEffect(displayMode, ambientLux, settings?.autoBrightnessEnabled, activity) {
        activity?.setPanelBrightness(displayMode, settings?.autoBrightnessEnabled == true, ambientLux)
    }

    LaunchedEffect(settings?.kioskModeEnabled, editing, activity) {
        activity?.applyImmersiveKiosk(settings?.kioskModeEnabled == true && !editing)
    }

    LaunchedEffect(wakePulse, activity) {
        if (wakePulse > 0L) activity?.wakeHardwareScreen()
    }

    LaunchedEffect(update.installerUri) {
        val raw = update.installerUri ?: return@LaunchedEffect
        val uri = Uri.parse(raw)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingInstallerUri = raw
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            unknownSourcesLauncher.launch(intent)
        } else {
            context.launchApkInstaller(uri)
            viewModel.consumeInstallerUri()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    viewModel.userActivity()
                }
            }
    ) {
        if (!settingsLoaded) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        val currentSettings = settings
        if (currentSettings == null || editing) {
            SetupScreen(
                initialSettings = currentSettings,
                discoveryState = discovery,
                rtspCameraState = rtspCamera,
                mqttDeviceState = mqttDevice,
                updateState = update,
                canCancel = currentSettings != null,
                onDiscover = viewModel::discoverAlarms,
                onCheckUpdate = { viewModel.checkForUpdates() },
                onDownloadUpdate = viewModel::downloadUpdate,
                onSave = viewModel::saveConfiguration,
                onCancel = viewModel::cancelEditing,
                onClear = viewModel::clearConfiguration
            )
            return@Box
        }

        when (displayMode) {
            PanelDisplayMode.ACTIVE -> AlarmScreen(
                settings = currentSettings,
                connectionState = connection,
                weatherState = weather,
                guestWifiState = guestWifi,
                houseSummaryState = houseSummary,
                history = history,
                onAction = viewModel::performAction,
                onCreateGuestVoucher = viewModel::createGuestVoucher,
                onDeleteGuestVoucher = viewModel::deleteGuestVoucher,
                onRefreshGuestQr = { viewModel.refreshGuestQr() },
                onReconnect = viewModel::reconnect,
                onRefreshWeather = viewModel::refreshWeather,
                onRefreshHouse = viewModel::refreshHouseSummary,
                onClearHistory = viewModel::clearHistory,
                onSettings = {
                    if (currentSettings.kioskModeEnabled && currentSettings.settingsPin.isNotBlank()) {
                        showSettingsPin = true
                    } else {
                        viewModel.editConfiguration()
                    }
                }
            )
            PanelDisplayMode.SCREENSAVER -> ScreenSaverScreen(
                alarm = connection.alarm,
                weatherState = weather,
                deepSleep = false
            )
            PanelDisplayMode.SLEEP -> ScreenSaverScreen(
                alarm = connection.alarm,
                weatherState = weather,
                deepSleep = true
            )
        }
    }

    if (showSettingsPin) {
        SettingsPinDialog(
            expectedPin = settings?.settingsPin.orEmpty(),
            onDismiss = { showSettingsPin = false },
            onSuccess = {
                showSettingsPin = false
                viewModel.editConfiguration()
            }
        )
    }
}

@Composable
private fun SettingsPinDialog(expectedPin: String, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_pin_prompt))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); invalid = false },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = invalid
                )
                if (invalid) Text(stringResource(R.string.settings_pin_incorrect))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pin == expectedPin) onSuccess() else invalid = true
            }, enabled = pin.isNotBlank()) { Text(stringResource(R.string.settings_unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}


private fun Context.launchApkInstaller(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Activity.applyImmersiveKiosk(enabled: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, !enabled)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    if (enabled) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Activity.setPanelBrightness(mode: PanelDisplayMode, autoBrightness: Boolean, ambientLux: Float?) {
    val params = window.attributes
    params.screenBrightness = when (mode) {
        PanelDisplayMode.SCREENSAVER -> 0.12f
        PanelDisplayMode.SLEEP -> 0.01f
        PanelDisplayMode.ACTIVE -> if (!autoBrightness || ambientLux == null) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            when {
                ambientLux < 3f -> 0.08f
                ambientLux < 15f -> 0.16f
                ambientLux < 60f -> 0.28f
                ambientLux < 250f -> 0.48f
                ambientLux < 1_000f -> 0.72f
                else -> 1.0f
            }
        }
    }
    window.attributes = params
}

@Suppress("DEPRECATION")
private fun Activity.wakeHardwareScreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setTurnScreenOn(true)
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    val wakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "HomePanel:wake"
    )
    wakeLock.acquire(3_000L)
}

private const val PROXIMITY_DEBOUNCE_MS = 900L
