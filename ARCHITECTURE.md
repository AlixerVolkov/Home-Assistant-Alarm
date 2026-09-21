# Architecture

## Goals

HomePanel should be a native Android alarm panel that treats Home Assistant as the source of truth.
MQTT is an optional device/integration transport, not a requirement for basic alarm control.

## v0.1 data flow

### Discovery

```text
SetupScreen
  -> MainViewModel
    -> HomeAssistantRestClient
      -> GET /api/states
        -> filter alarm_control_panel.*
```

### Runtime

```text
AlarmScreen
  <-> MainViewModel
       <-> HomeAssistantWebSocket
            -> /api/websocket
```

WebSocket sequence:

1. Connect.
2. Wait for `auth_required`.
3. Send `auth` with access token.
4. Wait for `auth_ok`.
5. Send `get_states`.
6. Subscribe to `state_changed`.
7. Update the selected alarm entity in real time.
8. Send `call_service` for alarm actions.

## Security

The access token is encrypted using AES/GCM. The AES key lives in Android Keystore.
The encrypted token and non-secret configuration are stored in Preferences DataStore.

The v0.1 manifest permits cleartext HTTP because many local Home Assistant installations still use it.
The UI warns the user whenever an `http://` URL is entered.
Production deployments should use TLS.

## Package structure

```text
dev.homepanel.app
|-- MainActivity.kt
|-- MainViewModel.kt
|-- data/
|   |-- PanelSettings.kt
|   `-- SettingsRepository.kt
|-- network/
|   |-- HomeAssistantModels.kt
|   |-- HomeAssistantRestClient.kt
|   |-- HomeAssistantUrl.kt
|   `-- HomeAssistantWebSocket.kt
|-- security/
|   `-- CryptoManager.kt
`-- ui/
    |-- HomePanelApp.kt
    |-- SetupScreen.kt
    |-- AlarmScreen.kt
    `-- theme/
        `-- Theme.kt
```

## Planned module split

When MQTT, CameraX and kiosk services are added, split into modules:

```text
:app
:core:model
:core:ha
:core:mqtt
:core:security
:feature:alarm
:feature:settings
:feature:kiosk
:feature:sensors
:feature:camera
```

## v0.6.8 maps and weather alerts

### People map

`person.*` and `zone.*` coordinates remain sourced exclusively from Home Assistant. The UI can render them on either:

- the existing offline coordinate canvas, or
- OpenStreetMap raster tiles from `https://tile.openstreetmap.org/{z}/{x}/{y}.png`.

The OpenStreetMap client requests only tiles visible in the current viewport, identifies HomePanel with a dedicated User-Agent, uses an OkHttp disk cache and keeps visible attribution on the map. No person identifiers are sent to the tile server; only normal tile requests for the viewed area are made.

### Contextual weather warnings

`HomeAssistantRestClient` discovers `binary_sensor.weather_warning*` / MeteoAlarm-like entities while building the house summary. `MainViewModel` compares the active warning set after each refresh. A newly active warning wakes the display and records a history event. `AlarmScreen` then opens a contextual warning dialog while keeping the normal warning banner visible after dismissal.
