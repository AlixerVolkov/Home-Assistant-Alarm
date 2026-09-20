package dev.homepanel.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.homepanel.app.MainViewModel
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
    val rtspCamera by viewModel.rtspCamera.collectAsStateWithLifecycle()
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    val wakePulse by viewModel.wakePulse.collectAsStateWithLifecycle()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            viewModel.refreshDeviceLocation()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onLocalNetworkPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) {
            viewModel.refreshDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
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

    // The tablet's own proximity sensor is used only while the saver/sleep screen is active.
    // Waking on a FAR -> NEAR edge avoids continuous wake events from noisy proximity sensors.
    DisposableEffect(context, settings?.proximityWakeEnabled, displayMode) {
        val shouldListen = settings?.proximityWakeEnabled == true && displayMode != PanelDisplayMode.ACTIVE
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
                if (isNear && !wasNear && now - lastWakeElapsed >= PROXIMITY_DEBOUNCE_MS) {
                    lastWakeElapsed = now
                    viewModel.onLocalDetection()
                }
                wasNear = isNear
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (proximity != null) {
            sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(displayMode, activity) {
        activity?.setPanelBrightness(displayMode)
    }

    LaunchedEffect(wakePulse, activity) {
        if (wakePulse > 0L) {
            activity?.wakeHardwareScreen()
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
                canCancel = currentSettings != null,
                onDiscover = viewModel::discoverAlarms,
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
                rtspCameraState = rtspCamera,
                onAction = viewModel::performAction,
                onCreateGuestVoucher = viewModel::createGuestVoucher,
                onRefreshGuestQr = { viewModel.refreshGuestQr() },
                onReconnect = viewModel::reconnect,
                onRefreshWeather = viewModel::refreshWeather,
                onSettings = viewModel::editConfiguration
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
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Activity.setPanelBrightness(mode: PanelDisplayMode) {
    val params = window.attributes
    params.screenBrightness = when (mode) {
        PanelDisplayMode.ACTIVE -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        PanelDisplayMode.SCREENSAVER -> 0.16f
        PanelDisplayMode.SLEEP -> 0.01f
    }
    window.attributes = params
}

@Suppress("DEPRECATION")
private fun Activity.wakeHardwareScreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setTurnScreenOn(true)
    }

    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    val wakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "HomePanel:wake"
    )
    wakeLock.acquire(3_000L)
}

private const val PROXIMITY_DEBOUNCE_MS = 900L
