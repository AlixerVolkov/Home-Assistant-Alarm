# HomePanel

A modern Android wall alarm panel for Home Assistant and Alarmo.

## v0.2.0

HomePanel connects directly to Home Assistant through `/api/websocket`, subscribes to the selected `alarm_control_panel` entity and controls it with Home Assistant's standard alarm actions.

### Dashboard

- Large alarm state display.
- Touch-friendly visual modes: Disarm, Home, Away, Night, Vacation and Custom bypass.
- PIN keypad shown only when needed.
- Prevents redundant state commands such as Disarm while already disarmed.
- Connection status and automatic reconnect.
- Current time/date for Europe/Brussels.
- Current Ekeren weather and 5-day forecast via Open-Meteo.
- Keeps the tablet screen awake while the panel is running.

### Setup

1. Install the APK.
2. Enter your Home Assistant base URL, for example `https://homeassistant.example.com`.
3. Enter a Home Assistant long-lived access token.
4. Discover and select your `alarm_control_panel` entity (Alarmo is supported).
5. Save and connect.

### Security

Use HTTPS/WSS whenever possible. The Home Assistant token is stored encrypted with Android Keystore/AES-GCM.

## Android

- minSdk 26 (Android 8.0+)
- targetSdk 37
- compileSdk 37.0
- Kotlin + Jetpack Compose
- JDK 17

## Build

GitHub Actions builds a debug APK automatically on pushes to `main`, or manually from **Actions → Build Android APK → Run workflow**.

The artifact is named:

`HomePanel-v0.2.0-debug-apk`

## Weather attribution

Weather forecast data: Open-Meteo.
