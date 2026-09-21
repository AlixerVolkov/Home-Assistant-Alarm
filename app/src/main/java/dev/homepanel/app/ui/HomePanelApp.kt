package dev.homepanel.app.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.runtime.remember
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
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.homepanel.app.MainViewModel
import dev.homepanel.app.diagnostics.CrashLogStore
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
    val networkDiagnostics by viewModel.networkDiagnostics.collectAsStateWithLifecycle()
    val ambientLux by viewModel.ambientLux.collectAsStateWithLifecycle()
    val rtspCamera by viewModel.rtspCamera.collectAsStateWithLifecycle()
    val mqttDevice by viewModel.mqttDevice.collectAsStateWithLifecycle()
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    val wakePulse by viewModel.wakePulse.collectAsStateWithLifecycle()

    val safeMode = viewModel.isSafeMode()
    var crashReport by remember { mutableStateOf(CrashLogStore.pendingReport(context)) }
    var showSettingsPin by rememberSaveable { mutableStateOf(false) }
    var pendingInstallerUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Permission requests must be serialized. Android can drop/short-circuit one runtime
    // permission request when multiple launchers are fired during the same startup frame.
    // 0 = local network, 1 = camera (when RTSP is enabled), 2 = location, 3 = complete.
    var startupPermissionStage by rememberSaveable { mutableStateOf(0) }
    var showLocalNetworkIntro by rememberSaveable { mutableStateOf(false) }
    var showLocalNetworkDenied by rememberSaveable { mutableStateOf(false) }
    var showUnusedAppRestrictions by rememberSaveable { mutableStateOf(false) }
    var unusedAppStatusChecked by rememberSaveable { mutableStateOf(false) }
    var locationPermissionPrompted by rememberSaveable { mutableStateOf(false) }

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
        locationPermissionPrompted = true
        if (result.values.any { it }) viewModel.refreshDeviceLocation()
        startupPermissionStage = 3
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
        startupPermissionStage = 2
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onLocalNetworkPermissionResult(granted)
        showLocalNetworkDenied = !granted
        startupPermissionStage = 1
    }

    val appDetailsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= 37) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
            viewModel.onLocalNetworkPermissionResult(granted)
            showLocalNetworkDenied = !granted
            if (granted && startupPermissionStage == 0) startupPermissionStage = 1
        }
    }

    val unusedAppRestrictionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check next time the app is opened. The OS settings screen owns this toggle and
        // normal applications are intentionally not allowed to disable it silently.
        showUnusedAppRestrictions = false
    }

    LaunchedEffect(startupPermissionStage, settingsLoaded, settings?.rtspEnabled, showLocalNetworkDenied) {
        if (showLocalNetworkDenied) return@LaunchedEffect
        when (startupPermissionStage) {
            0 -> {
                if (Build.VERSION.SDK_INT >= 37 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_LOCAL_NETWORK
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    showLocalNetworkIntro = true
                } else {
                    // Also re-apply LAN services when the permission was already granted before
                    // this process started.
                    if (Build.VERSION.SDK_INT >= 37) viewModel.onLocalNetworkPermissionResult(true)
                    startupPermissionStage = 1
                }
            }
            1 -> {
                // Never stack CAMERA on top of the Android local-network permission prompt.
                val localNetworkGranted = Build.VERSION.SDK_INT < 37 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_LOCAL_NETWORK
                    ) == PackageManager.PERMISSION_GRANTED
                if (!safeMode && settingsLoaded && settings?.rtspEnabled == true && localNetworkGranted &&
                    !viewModel.hasCameraPermission()
                ) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else if (settingsLoaded) {
                    startupPermissionStage = 2
                }
            }
            2 -> {
                val needsLocation = settings?.weatherSource == MainViewModel.WEATHER_SOURCE_OPEN_METEO
                if (safeMode || !needsLocation) {
                    if (!safeMode && settings != null) viewModel.refreshWeather()
                    startupPermissionStage = 3
                } else if (viewModel.hasLocationPermission()) {
                    viewModel.refreshDeviceLocation()
                    startupPermissionStage = 3
                } else if (!locationPermissionPrompted) {
                    locationPermissionPrompted = true
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                } else {
                    startupPermissionStage = 3
                }
            }
        }
    }

    // If Open-Meteo is selected later from Settings, request location once. Home Assistant
    // weather does not need Android location permission at all.
    LaunchedEffect(settings?.weatherSource, editing, settingsLoaded) {
        if (settingsLoaded && !editing && !safeMode &&
            settings?.weatherSource == MainViewModel.WEATHER_SOURCE_OPEN_METEO &&
            !viewModel.hasLocationPermission() && !locationPermissionPrompted
        ) {
            locationPermissionPrompted = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    // If RTSP is enabled later from Settings, request CAMERA only after LAN permission is ready.
    LaunchedEffect(settings?.rtspEnabled, startupPermissionStage) {
        val localNetworkGranted = Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
        if (!safeMode && startupPermissionStage >= 3 && settings?.rtspEnabled == true &&
            localNetworkGranted && !viewModel.hasCameraPermission()
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Android can revoke runtime permissions and hibernate apps that remain unused. A wall-panel
    // app should normally be exempt, but only the user can change that system policy.
    LaunchedEffect(startupPermissionStage, unusedAppStatusChecked, showLocalNetworkDenied) {
        if (safeMode || startupPermissionStage < 3 || unusedAppStatusChecked || showLocalNetworkDenied) return@LaunchedEffect
        unusedAppStatusChecked = true
        val future = runCatching { PackageManagerCompat.getUnusedAppRestrictionsStatus(context) }
            .getOrNull() ?: return@LaunchedEffect
        future.addListener({
            val status = runCatching { future.get() }.getOrNull()
            showUnusedAppRestrictions = status == UnusedAppRestrictionsConstants.API_30 ||
                status == UnusedAppRestrictionsConstants.API_30_BACKPORT ||
                status == UnusedAppRestrictionsConstants.API_31
        }, ContextCompat.getMainExecutor(context))
    }

    // Proximity: publish state and wake on FAR -> NEAR while sleeping/saver.
    DisposableEffect(context, settings?.proximityWakeEnabled, displayMode) {
        val shouldListen = !safeMode && settings?.proximityWakeEnabled == true
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
        if (proximity != null) runCatching {
            sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { runCatching { sensorManager.unregisterListener(listener) } }
    }

    // Ambient-light sensor: drives auto-brightness and is published over MQTT Discovery.
    DisposableEffect(context, settings?.autoBrightnessEnabled, settings?.mqttDiscoveryEnabled) {
        val shouldListen = !safeMode && (settings?.autoBrightnessEnabled == true || settings?.mqttDiscoveryEnabled == true)
        if (!shouldListen) return@DisposableEffect onDispose { }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let(viewModel::onAmbientLightChanged)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (light != null) runCatching {
            sensorManager.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { runCatching { sensorManager.unregisterListener(listener) } }
    }

    LaunchedEffect(displayMode, ambientLux, settings?.autoBrightnessEnabled, activity) {
        activity?.let { current ->
            runCatching { current.setPanelBrightness(displayMode, !safeMode && settings?.autoBrightnessEnabled == true, ambientLux) }
        }
    }

    LaunchedEffect(settings?.kioskModeEnabled, editing, activity) {
        activity?.let { current ->
            runCatching { current.applyImmersiveKiosk(settings?.kioskModeEnabled == true && !editing) }
        }
    }

    LaunchedEffect(wakePulse, activity) {
        if (wakePulse > 0L) activity?.let { current -> runCatching { current.wakeHardwareScreen() } }
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
                networkDiagnosticsState = networkDiagnostics,
                canCancel = currentSettings != null,
                onDiscover = viewModel::discoverAlarms,
                onCheckUpdate = { viewModel.checkForUpdates() },
                onDownloadUpdate = viewModel::downloadUpdate,
                onRunNetworkDiagnostics = viewModel::runNetworkDiagnostics,
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

    if (showLocalNetworkIntro) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.local_network_permission_title)) },
            text = { Text(stringResource(R.string.local_network_permission_message)) },
            confirmButton = {
                Button(onClick = {
                    showLocalNetworkIntro = false
                    localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }) { Text(stringResource(R.string.allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocalNetworkIntro = false
                    showLocalNetworkDenied = true
                    startupPermissionStage = 1
                }) { Text(stringResource(R.string.not_now)) }
            }
        )
    }

    if (showLocalNetworkDenied) {
        AlertDialog(
            onDismissRequest = { showLocalNetworkDenied = false },
            title = { Text(stringResource(R.string.local_network_missing_title)) },
            text = { Text(stringResource(R.string.local_network_missing_message)) },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    appDetailsLauncher.launch(intent)
                }) { Text(stringResource(R.string.open_app_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showLocalNetworkDenied = false }) {
                    Text(stringResource(R.string.continue_limited))
                }
            }
        )
    }

    if (showUnusedAppRestrictions) {
        AlertDialog(
            onDismissRequest = { showUnusedAppRestrictions = false },
            title = { Text(stringResource(R.string.unused_app_title)) },
            text = { Text(stringResource(R.string.unused_app_message)) },
            confirmButton = {
                Button(onClick = {
                    runCatching {
                        IntentCompat.createManageUnusedAppRestrictionsIntent(
                            context,
                            context.packageName
                        )
                    }.onSuccess { intent -> unusedAppRestrictionsLauncher.launch(intent) }
                        .onFailure {
                            appDetailsLauncher.launch(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                }) { Text(stringResource(R.string.open_app_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showUnusedAppRestrictions = false }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }

    if (crashReport != null) {
        val report = crashReport.orEmpty()
        AlertDialog(
            onDismissRequest = {
                CrashLogStore.markReportShown(context)
                crashReport = null
            },
            title = { Text(stringResource(R.string.crash_detected_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (safeMode) R.string.crash_safe_mode_message else R.string.crash_detected_message))
                    Text(report.take(1600))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("HomePanel crash report", report))
                    CrashLogStore.markReportShown(context)
                    crashReport = null
                }) { Text(stringResource(R.string.copy_crash_report)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLogStore.markReportShown(context)
                    crashReport = null
                }) { Text(stringResource(R.string.close)) }
            }
        )
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
