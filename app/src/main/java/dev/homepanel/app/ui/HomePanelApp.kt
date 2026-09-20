package dev.homepanel.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.homepanel.app.MainViewModel

@Composable
fun HomePanelApp(viewModel: MainViewModel) {
    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()

    if (!settingsLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
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
    } else {
        AlarmScreen(
            settings = currentSettings,
            connectionState = connection,
            weatherState = weather,
            onAction = viewModel::performAction,
            onReconnect = viewModel::reconnect,
            onRefreshWeather = viewModel::refreshWeather,
            onSettings = viewModel::editConfiguration
        )
    }
}
